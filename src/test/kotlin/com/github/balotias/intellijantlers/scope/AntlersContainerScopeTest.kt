package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersContainerScopeTest : BasePlatformTestCase() {

    private val gridBlueprint = """
        tabs:
          main:
            sections:
              - fields:
                  - handle: rows
                    field:
                      type: grid
                      fields:
                        - handle: caption
                          field:
                            type: text
                            display: Caption
                        - handle: gallery
                          field:
                            type: grid
                            fields:
                              - handle: photo_alt
                                field:
                                  type: text
                  - handle: title
                    field: text
    """.trimIndent()

    private fun addBlueprint() =
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", gridBlueprint)

    private fun addPageConfig() =
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")

    /** Completion lookups at the caret in a file added at [path]. */
    private fun lookupsAt(path: String, textWithCaret: String): List<String> {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject(path, textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testGridScopeInsideCollectionLoop() {
        addBlueprint()
        // collection:blog gives the namespace; rows (grid) opens its sub-field scope.
        val l = lookupsAt(
            "page.antlers.html",
            "{{ collection:blog }}{{ rows }}{{ <caret> }}{{ /rows }}{{ /collection }}"
        )
        assertTrue("grid sub-field caption: $l", l.contains("caption"))
        assertTrue("loop var inside a grid: $l", l.contains("index"))
    }

    fun testTopLevelGridViaPageMappingExcludesSiblings() {
        addBlueprint()
        addPageConfig()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ rows }}{{ <caret> }}{{ /rows }}")
        assertTrue("grid sub-field caption: $l", l.contains("caption"))
        // 'rows' is a blueprint field on the page-level scope, not a sub-field of itself;
        // we verify the container (rows) is not re-offered inside its own sub-scope.
        assertFalse("grid scope excludes the container itself (rows): $l", l.contains("rows"))
    }

    fun testNestedGridScope() {
        addBlueprint()
        addPageConfig()
        val l = lookupsAt(
            "resources/views/blog/show.antlers.html",
            "{{ rows }}{{ gallery }}{{ <caret> }}{{ /gallery }}{{ /rows }}"
        )
        assertTrue("deeper sub-field photo_alt: $l", l.contains("photo_alt"))
    }

    fun testNavOnGridSubField() {
        addBlueprint()
        addPageConfig()
        val text = "{{ rows }}{{ cap<caret>tion }}{{ /rows }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertNotNull("caption resolves", target)
        assertEquals("blog.yaml", target!!.name)
    }

    fun testDocLabelShowsContainerPath() {
        addBlueprint()
        addPageConfig()
        val text = "{{ rows }}{{ cap<caret>tion }}{{ /rows }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("doc names the rows container: $doc", doc!!.contains("rows"))
        assertTrue("doc shows the display: $doc", doc.contains("Caption"))
    }
}
