package com.github.balotias.intellijantlers.formatter

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersSpacingFormatterTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            // The post-format processor edits the document directly; commit so the PSI (file.text) reflects it.
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    fun testEdgeSpacing() {
        assertEquals("{{ x }}", reformat("{{x}}"))
        assertEquals("{{ x }}", reformat("{{  x  }}"))
        assertEquals("{{ x }}", reformat("{{ x }}"))   // idempotent
    }

    fun testSeparatorsUntouched() {
        assertEquals("{{ collection:blog }}", reformat("{{collection:blog}}"))
        assertEquals("{{ author.name }}", reformat("{{ author.name }}"))
    }

    fun testPipeSpacing() {
        assertEquals("{{ x | upper }}", reformat("{{ x|upper }}"))
        assertEquals("{{ x | a | b }}", reformat("{{ x|a|b }}"))
        assertEquals("{{ x | upper }}", reformat("{{ x | upper }}"))  // idempotent
    }

    fun testStringSafety() {
        assertEquals("{{ x | replace('a|b', 'c') }}", reformat("{{ x | replace('a|b', 'c') }}"))
    }

    fun testParamsUntouched() {
        assertEquals("{{ partial:src=\"blog/card\" }}", reformat("{{ partial:src=\"blog/card\" }}"))
    }

    fun testMultipleRegions() {
        assertEquals("{{ x }} {{ y | z }}", reformat("{{x}} {{ y|z }}"))
    }

    fun testNonExprUntouched() {
        assertEquals("{{# comment #}}", reformat("{{# comment #}}"))
    }
}
