package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMemberDocTest : BasePlatformTestCase() {

    private val blueprint = """
        tabs:
          main:
            sections:
              - fields:
                  - handle: hero
                    field:
                      type: group
                      fields:
                        - handle: subhead
                          field:
                            type: text
                            display: Subhead
                  - handle: hero_image
                    field:
                      type: assets
    """.trimIndent()

    private fun setup() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
    }

    private fun docAt(textWithCaret: String): String? {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(caret)!!
        return AntlersDocumentationProvider().generateDoc(el, el)
    }

    fun testDocOnSubField() {
        setup()
        val doc = docAt("{{ hero.sub<caret>head }}")
        assertNotNull(doc)
        assertTrue("names the hero container: $doc", doc!!.contains("hero"))
        assertTrue("shows the display: $doc", doc.contains("Subhead"))
    }

    fun testDocOnAugmentationProperty() {
        setup()
        val doc = docAt("{{ hero_image.u<caret>rl }}")
        assertNotNull(doc)
        assertTrue("names the assets type: $doc", doc!!.contains("assets"))
        assertTrue("shows the property description: $doc", doc.contains("URL"))
    }
}
