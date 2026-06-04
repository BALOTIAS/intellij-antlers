package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.settings.AntlersFormatterSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFormatterOptOutTest : BasePlatformTestCase() {
    override fun tearDown() {
        try { AntlersFormatterSettings.getInstance(project).reformatEnabled = true } finally { super.tearDown() }
    }

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    fun testDisabledLeavesDelimiterSpacingUntouched() {
        AntlersFormatterSettings.getInstance(project).reformatEnabled = false
        assertEquals("{{x}}", reformat("{{x}}"))   // would become "{{ x }}" when enabled
    }

    fun testDisabledLeavesMultilineTagUntouched() {
        AntlersFormatterSettings.getInstance(project).reformatEnabled = false
        val src = "{{ collection:blog\nlimit=\"3\"\n}}"   // would be indented + spacing-normalized when enabled
        assertEquals(src, reformat(src))
    }

    fun testEnabledStillFormats() {
        // default (enabled) — proves the gate doesn't break normal behavior
        assertEquals("{{ x }}", reformat("{{x}}"))
    }
}
