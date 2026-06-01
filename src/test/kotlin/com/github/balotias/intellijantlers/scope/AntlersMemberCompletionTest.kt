package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMemberCompletionTest : BasePlatformTestCase() {

    private val blueprint = """
        tabs:
          main:
            sections:
              - fields:
                  - handle: hero
                    field:
                      type: group
                      fields:
                        - handle: headline
                          field:
                            type: text
                            display: Headline
                        - handle: subhead
                          field:
                            type: text
                  - handle: hero_image
                    field:
                      type: assets
    """.trimIndent()

    private fun setup() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
    }

    private fun lookupsAt(path: String, textWithCaret: String): List<String> {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject(path, textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testGroupDotOffersSubFields() {
        setup()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ hero.<caret> }}")
        assertTrue("sub-field headline: $l", l.contains("headline"))
        assertTrue("sub-field subhead: $l", l.contains("subhead"))
        assertFalse("page sibling hero_image not offered: $l", l.contains("hero_image"))
    }

    fun testAssetsDotOffersProperties() {
        setup()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ hero_image.<caret> }}")
        assertTrue("asset url: $l", l.contains("url"))
        assertTrue("asset alt: $l", l.contains("alt"))
    }

    fun testColonOffersSubFields() {
        setup()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ hero:<caret> }}")
        assertTrue("colon group sub-field: $l", l.contains("headline"))
    }

    fun testDotInsideCollectionLoop() {
        setup()
        val l = lookupsAt("page.antlers.html", "{{ collection:blog }}{{ hero.<caret> }}{{ /collection }}")
        assertTrue("scope-aware seg 0: $l", l.contains("headline"))
    }
}
