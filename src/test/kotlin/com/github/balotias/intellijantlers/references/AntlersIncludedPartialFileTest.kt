package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersIncludedPartialFileTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject("resources/views/components/_button.antlers.html", "{{# @param* label x #}}")
    }

    private fun statementOf(text: String): AntlersStatement {
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text)
        return PsiTreeUtil.findChildOfType(file, AntlersStatement::class.java)!!
    }

    fun testResolvesColonForm() {
        setup()
        val stmt = statementOf("{{ partial:components/button }}")
        assertEquals("_button.antlers.html", AntlersPartialReferenceHelper.includedPartialFile(stmt)?.name)
        assertTrue(AntlersPartialReferenceHelper.hasColonPath(stmt))
    }

    fun testResolvesSrcForm() {
        setup()
        val stmt = statementOf("{{ partial src=\"components/button\" }}")
        assertEquals("_button.antlers.html", AntlersPartialReferenceHelper.includedPartialFile(stmt)?.name)
        assertFalse("src form has no colon path", AntlersPartialReferenceHelper.hasColonPath(stmt))
    }

    fun testUnresolvedReturnsNull() {
        setup()
        val stmt = statementOf("{{ partial:does/not/exist }}")
        assertNull(AntlersPartialReferenceHelper.includedPartialFile(stmt))
    }
}
