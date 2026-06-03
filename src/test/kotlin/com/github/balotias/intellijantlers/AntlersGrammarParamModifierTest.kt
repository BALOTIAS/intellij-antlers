package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.psi.AntlersModifier
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameter
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersGrammarParamModifierTest : BasePlatformTestCase() {

    private fun configure(text: String) = myFixture.configureByText("p.antlers.html", text)

    private fun errors() =
        PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java)

    fun testBoundParamParsesAsParameter() {
        configure("{{ partial :src=\"hero\" }}")
        val param = PsiTreeUtil.findChildOfType(myFixture.file, AntlersParameter::class.java)
        assertNotNull("`:src=\"hero\"` should parse as a parameter, not be swallowed into the name-path", param)
        assertTrue("the parameter covers the bound name", param!!.text.contains(":src"))
        assertTrue("the parameter covers the value", param.text.contains("\"hero\""))
        assertTrue("no parse errors: ${errors().map { it.errorDescription }}", errors().isEmpty())
    }

    fun testColonArgModifierKeepsArgInsideModifier() {
        configure("{{ title | truncate:10 }}")
        val mod = PsiTreeUtil.findChildOfType(myFixture.file, AntlersModifier::class.java)
        assertNotNull(mod)
        assertEquals("truncate", (mod as AntlersModifierMixin).modifierName)
        assertTrue("the modifier node spans the `:10` colon-arg", mod.text.replace(" ", "").contains("truncate:10"))
        assertTrue("no parse errors: ${errors().map { it.errorDescription }}", errors().isEmpty())
    }

    fun testShorthandHandleStillOneNamePath() {
        // Regression: `collection:blog` (no `=`) is still a single name-path, not a param.
        configure("{{ collection:blog }}{{ /collection:blog }}")
        val np = PsiTreeUtil.findChildOfType(myFixture.file, AntlersNamePathMixin::class.java)
        assertNotNull(np)
        assertTrue("collection:blog stays one name-path", np!!.text.contains("collection:blog"))
        assertTrue("no parse errors: ${errors().map { it.errorDescription }}", errors().isEmpty())
    }
}
