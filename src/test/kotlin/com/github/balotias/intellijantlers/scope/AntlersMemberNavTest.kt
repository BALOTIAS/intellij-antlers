package com.github.balotias.intellijantlers.scope

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMemberNavTest : BasePlatformTestCase() {

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

    private fun resolveAt(textWithCaret: String): PsiFile? {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
    }

    fun testNavOnSubField() {
        setup()
        assertEquals("blog.yaml", resolveAt("{{ hero.sub<caret>head }}")?.name)
    }

    fun testNavOnAugmentationPropertyIsNull() {
        setup()
        assertNull(resolveAt("{{ hero_image.u<caret>rl }}"))
    }
}
