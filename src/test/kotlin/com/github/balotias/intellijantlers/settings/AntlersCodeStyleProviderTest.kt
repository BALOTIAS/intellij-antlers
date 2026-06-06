package com.github.balotias.intellijantlers.settings

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCodeStyleProviderTest : BasePlatformTestCase() {

    fun testProviderLanguageAndSample() {
        val p = AntlersLanguageCodeStyleSettingsProvider()
        assertEquals(AntlersLanguage.INSTANCE, p.language)
        val sample = p.getCodeSample(LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS)
        assertTrue("code sample is a non-blank Antlers snippet, got: $sample",
            sample.isNotBlank() && sample.contains("{{"))
    }

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    fun testConfiguredIndentSizeDrivesTheFormatter() {
        val temp = CodeStyle.getSettings(project).clone()
        temp.getCommonSettings(AntlersLanguage.INSTANCE).indentOptions!!.INDENT_SIZE = 2
        CodeStyle.doWithTemporarySettings(project, temp, Runnable {
            // {{ if }} body indents by the configured 2 spaces, not the default 4.
            assertEquals("{{ if x }}\n  {{ a }}\n{{ /if }}", reformat("{{ if x }}\n{{ a }}\n{{ /if }}"))
        })
    }

    fun testConfiguredTabsDriveTheFormatter() {
        val temp = CodeStyle.getSettings(project).clone()
        temp.getCommonSettings(AntlersLanguage.INSTANCE).indentOptions!!.let {
            it.USE_TAB_CHARACTER = true; it.TAB_SIZE = 4
        }
        CodeStyle.doWithTemporarySettings(project, temp, Runnable {
            assertEquals("{{ if x }}\n\t{{ a }}\n{{ /if }}", reformat("{{ if x }}\n{{ a }}\n{{ /if }}"))
        })
    }
}
