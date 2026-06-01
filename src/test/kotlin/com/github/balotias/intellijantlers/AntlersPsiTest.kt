package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
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

    fun testNamePathAccessors() {
        val file = myFixture.configureByText("t.antlers.html", "{{ collection:blog }}")
        val path = PsiTreeUtil.findChildOfType(file, AntlersNamePathMixin::class.java)!!
        assertEquals("collection", path.head)
        assertEquals("blog", path.method)
        assertEquals("collection:blog", path.pathText.trim())
    }

    fun testConditionKeyword() {
        val file = myFixture.configureByText("t.antlers.html", "{{ if x == 1 }}yes{{ /if }}")
        val cond = PsiTreeUtil.findChildOfType(file, AntlersConditionMixin::class.java)!!
        assertEquals("if", cond.keyword)
    }

    fun testClosingTagName() {
        val file = myFixture.configureByText("t.antlers.html", "{{ collection:blog }}{{ /collection:blog }}")
        val closing = PsiTreeUtil.findChildrenOfType(file, AntlersClosingTagMixin::class.java).toList()
        assertEquals(1, closing.size)
        assertEquals("collection:blog", closing[0].closedName?.trim())
    }

    fun testDollarBoundParameter() {
        val file = myFixture.configureByText("t.antlers.html", "{{ tag \$foo }}")
        val params = PsiTreeUtil.findChildrenOfType(file, AntlersParameterMixin::class.java).toList()
        assertEquals(1, params.size)
        assertEquals("foo", params[0].parameterName)
        assertTrue(params[0].isBound)
    }
}
