package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersStringLeafHostTest : BasePlatformTestCase() {

    private fun stringLeaf(text: String): AntlersStringLeaf {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.collectElements(antlers) { it is AntlersStringLeaf }.first() as AntlersStringLeaf
    }

    fun testStringLeafIsValidHost() {
        assertTrue((stringLeaf("{{ x = \"title\" }}") as PsiLanguageInjectionHost).isValidHost)
    }

    fun testUpdateTextReplacesWholeLiteral() {
        val leaf = stringLeaf("{{ x = \"title\" }}")
        val updated = WriteCommandAction.runWriteCommandAction<PsiLanguageInjectionHost>(project) {
            leaf.updateText("\"name\"")
        }
        assertEquals("\"name\"", updated.text)
    }

    fun testUpdateTextBailsWhenEditBreaksTheString() {
        val leaf = stringLeaf("{{ x = \"title\" }}")
        // An unescaped inner quote would end the string early → not a single T_STRING → bail, text intact.
        val updated = WriteCommandAction.runWriteCommandAction<PsiLanguageInjectionHost>(project) {
            leaf.updateText("\"na\"me\"")
        }
        assertEquals("\"title\"", updated.text)
    }
}
