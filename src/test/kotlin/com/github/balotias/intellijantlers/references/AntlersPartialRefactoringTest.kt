package com.github.balotias.intellijantlers.references

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialRefactoringTest : BasePlatformTestCase() {

    private fun addPartial(path: String) = myFixture.addFileToProject(path, "<div>card</div>")
    private fun addTemplate(text: String): PsiFile =
        myFixture.addFileToProject("resources/views/page.antlers.html", text)

    fun testRenameStringForm() {
        val partial = addPartial("resources/views/blog/card.antlers.html")
        val tmpl = addTemplate("{{ partial:src=\"blog/card\" }}")
        myFixture.renameElement(partial, "tile.antlers.html")
        assertEquals("{{ partial:src=\"blog/tile\" }}", tmpl.text)
    }

    fun testRenameColonForm() {
        val partial = addPartial("resources/views/blog/card.antlers.html")
        val tmpl = addTemplate("{{ partial:blog/card }}")
        myFixture.renameElement(partial, "tile.antlers.html")
        assertEquals("{{ partial:blog/tile }}", tmpl.text)
    }

    fun testBindToElementMove() {
        addPartial("resources/views/blog/card.antlers.html")
        val text = "{{ partial:src=\"blog/card\" }}"
        val caret = text.indexOf("card")
        val tmpl = addTemplate(text)
        val moved = addPartial("resources/views/shared/card.antlers.html")
        val ref = tmpl.findReferenceAt(caret) as? AntlersPartialReference
            ?: error("no partial reference at caret")
        WriteCommandAction.runWriteCommandAction(project) { ref.bindToElement(moved) }
        assertTrue("path rewritten to shared/card: ${tmpl.text}", tmpl.text.contains("shared/card"))
    }

    fun testFindUsages() {
        val partial = addPartial("resources/views/blog/card.antlers.html")
        addTemplate("{{ partial:src=\"blog/card\" }}")
        val usages = myFixture.findUsages(partial)
        assertTrue("expected an include usage: ${usages.map { it.element?.text }}", usages.isNotEmpty())
    }

    fun testUnrelatedRenameLeavesTemplateAlone() {
        addPartial("resources/views/blog/card.antlers.html")
        val tmpl = addTemplate("{{ partial:src=\"blog/card\" }}")
        val other = myFixture.addFileToProject("resources/views/other.antlers.html", "x")
        myFixture.renameElement(other, "renamed.antlers.html")
        assertEquals("{{ partial:src=\"blog/card\" }}", tmpl.text)
    }
}
