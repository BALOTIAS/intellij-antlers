package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMultilineFormatTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()  // sync PSI with the doc edits
        }
        return file.text
    }

    /** The active indent unit — call AFTER reformat() has configured the fixture (NPE otherwise). */
    private fun unit(): String {
        val opts = CodeStyle.getIndentOptions(myFixture.file)
        return if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
    }

    fun testMultilineCloserNotPulledUp() {
        val out = reformat("{{ collection:blog\nlimit=\"3\"\n}}")
        assertTrue("closer stays on its own line, got:\n$out", out.trimEnd().endsWith("\n}}"))
        assertFalse("closer not joined to the last param, got:\n$out", out.contains("\"3\" }}"))
    }
}
