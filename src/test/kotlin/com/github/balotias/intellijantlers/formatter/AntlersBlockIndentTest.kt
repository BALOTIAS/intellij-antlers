package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersBlockIndentTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    private fun unit(): String {
        val opts = CodeStyle.getIndentOptions(myFixture.file)
        return if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
    }

    fun testIfBodyIndented() {
        val out = reformat("{{ if x }}\n{{ title }}\n{{ /if }}")
        val u = unit()
        assertEquals("{{ if x }}\n${u}{{ title }}\n{{ /if }}", out)
    }

    fun testNestedBlocksCompound() {
        val out = reformat("{{ if a }}\n{{ if b }}\n{{ title }}\n{{ /if }}\n{{ /if }}")
        val u = unit()
        assertEquals(
            "{{ if a }}\n${u}{{ if b }}\n${u}${u}{{ title }}\n${u}{{ /if }}\n{{ /if }}",
            out
        )
    }
}
