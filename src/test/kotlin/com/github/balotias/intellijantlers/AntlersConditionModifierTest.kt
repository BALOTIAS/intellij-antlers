package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.psi.AntlersModifier
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersConditionModifierTest : BasePlatformTestCase() {

    private fun errors(text: String): List<String> {
        val file = myFixture.configureByText("p.antlers.html", text)
        return PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).map { it.errorDescription }
    }

    fun testModifierInIfParsesCleanly() {
        assertEquals("no errors: ${errors("{{ if title | upper }}{{ /if }}")}", emptyList<String>(),
            errors("{{ if title | upper }}{{ /if }}"))
    }

    fun testContainsWithAngleBracketStringParses() {
        // The reported case — the `<style` string is fine; the `|` was the problem.
        assertEquals(emptyList<String>(), errors("{{ if code | contains(\"<style\") }}{{ /if }}"))
    }

    fun testUnlessAndElseifAndChainedModifiers() {
        assertEquals(emptyList<String>(), errors("{{ unless items | count }}{{ /unless }}"))
        assertEquals(emptyList<String>(), errors("{{ if a | upper | lower }}{{ /if }}"))
        assertEquals(emptyList<String>(), errors("{{ if title | lower == \"x\" }}{{ /if }}"))
    }

    fun testModifierNodeInsideCondition() {
        myFixture.configureByText("p.antlers.html", "{{ if code | contains(\"<style\") }}{{ /if }}")
        val mod = PsiTreeUtil.findChildOfType(myFixture.file, AntlersModifier::class.java)
        assertNotNull("a modifier node exists inside the condition", mod)
        assertEquals("contains", (mod as AntlersModifierMixin).modifierName)
    }
}
