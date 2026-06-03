package com.github.balotias.intellijantlers

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersVersionCompatTest : BasePlatformTestCase() {

    private fun parseErrors(text: String): List<String> {
        val file = myFixture.configureByText("p.antlers.html", text)
        return PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).map { it.errorDescription }
    }

    fun testStatamicFiveIdiomsParseCleanly() {
        val v5 = """
            {{ relate:items }}{{ title }}{{ /relate }}
            {{ session:cart_count }}
            {{ if status == 'published' }}{{ title | upper }}{{ /if }}
        """.trimIndent()
        assertEquals("v5 template should have no parse errors: ${parseErrors(v5)}", emptyList<String>(), parseErrors(v5))
    }

    fun testStatamicSixIdiomsParseCleanly() {
        val v6 = """
            {{ session :handle="cart_count" }}
            {{ value | urlencode_except_slashes }}
            {{ related_posts }}{{ title }}{{ /related_posts }}
            {{ unless hidden }}{{ content }}{{ /unless }}
        """.trimIndent()
        assertEquals("v6 template should have no parse errors: ${parseErrors(v6)}", emptyList<String>(), parseErrors(v6))
    }
}
