package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPsiTest : BasePlatformTestCase() {

    fun testParameterAccessors() {
        val file = myFixture.configureByText("t.antlers.html", "{{ collection:blog limit=\"5\" :sort=\"order\" }}")
        val params = PsiTreeUtil.findChildrenOfType(file, AntlersParameterMixin::class.java).toList()
        assertEquals(2, params.size)
        assertEquals("limit", params[0].parameterName)
        assertFalse(params[0].isBound)
        assertEquals("sort", params[1].parameterName)
        assertTrue(params[1].isBound)
    }

    fun testModifierAccessor() {
        val file = myFixture.configureByText("t.antlers.html", "{{ title | upper | truncate(20) }}")
        val mods = PsiTreeUtil.findChildrenOfType(file, AntlersModifierMixin::class.java).toList()
        assertEquals(listOf("upper", "truncate"), mods.map { it.modifierName })
    }
}
