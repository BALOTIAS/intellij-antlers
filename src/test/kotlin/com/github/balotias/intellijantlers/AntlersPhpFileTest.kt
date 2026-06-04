package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.references.StatamicProject
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPhpFileTest : BasePlatformTestCase() {

    fun testAntlersPhpFileIsAntlers() {
        val file = myFixture.configureByText("p.antlers.php", "{{ title }}")
        assertEquals(AntlersLanguage.INSTANCE, file.viewProvider.baseLanguage)
    }

    fun testPartialResolvesToAntlersPhp() {
        myFixture.addFileToProject("resources/views/partials/card.antlers.php", "<div>card</div>")
        val text = "{{ partial:card }}"
        val caretOffset = text.indexOf("card")
        val pageFile = myFixture.addFileToProject("resources/views/page.antlers.html", text)
        myFixture.configureFromExistingVirtualFile(pageFile.virtualFile)
        val target = pageFile.findReferenceAt(caretOffset)?.resolve()
        assertNotNull("partial:card resolves", target)
        assertTrue(
            "resolves to the .antlers.php partial, got ${(target as? com.intellij.psi.PsiFile)?.name}",
            (target as com.intellij.psi.PsiFile).name == "card.antlers.php"
        )
    }

    fun testListPartialsIncludesAntlersPhp() {
        myFixture.addFileToProject("resources/views/partials/widget.antlers.php", "x")
        val pageFile = myFixture.addFileToProject("resources/views/page.antlers.html", "{{ title }}")
        myFixture.configureFromExistingVirtualFile(pageFile.virtualFile)
        val partials = StatamicProject.listPartials(pageFile)
        assertTrue("listPartials includes the .antlers.php partial, got $partials", partials.contains("widget"))
    }
}
