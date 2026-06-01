package com.github.balotias.intellijantlers.scope

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersRelationTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            """
            tabs:
              main:
                sections:
                  - fields:
                      - handle: author
                        field:
                          type: entries
                          collections:
                            - team
                      - handle: topics
                        field:
                          type: terms
                          taxonomy: tags
                      - handle: hero
                        field:
                          type: assets
                          container: images
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/team/team.yaml",
            """
            tabs:
              main:
                sections:
                  - fields:
                      - handle: full_name
                        field:
                          type: text
                          display: Full Name
                      - handle: avatar
                        field:
                          type: assets
                          container: images
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "resources/blueprints/taxonomies/tags/tags.yaml",
            "fields:\n  - handle: tag_color\n    field:\n      type: text\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/assets/images.yaml",
            "fields:\n  - handle: caption\n    field:\n      type: text\n"
        )
    }

    private fun lookupsAt(text: String): List<String> {
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testEntriesRelationOffersLinkedFieldsAndProps() {
        setup()
        val l = lookupsAt("{{ author.<caret> }}")
        assertTrue("linked field full_name: $l", l.contains("full_name"))
        assertTrue("entry prop url: $l", l.contains("url"))
        assertFalse("not a blog sibling: $l", l.contains("topics"))
    }

    fun testTermsRelation() {
        setup()
        assertTrue(lookupsAt("{{ topics.<caret> }}").contains("tag_color"))
    }

    fun testAssetsRelationOffersContainerFieldsAndProps() {
        setup()
        val l = lookupsAt("{{ hero.<caret> }}")
        assertTrue("container field caption: $l", l.contains("caption"))
        assertTrue("asset prop alt: $l", l.contains("alt"))
    }

    fun testMultiHop() {
        setup()
        assertTrue("asset prop via author->team->avatar", lookupsAt("{{ author.avatar.<caret> }}").contains("url"))
    }

    fun testBracketAccess() {
        setup()
        assertTrue("bracket access offers linked fields", lookupsAt("{{ author[0].<caret> }}").contains("full_name"))
    }

    fun testNavOnLinkedField() {
        setup()
        val text = "{{ author.full<caret>_name }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertEquals("team.yaml", target!!.name)
    }
}
