package com.github.balotias.intellijantlers.settings

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider

/**
 * Adds Settings → Editor → Code Style → Antlers with the standard Indent panel. The formatter already
 * reads `CodeStyle.getIndentOptions(file)`, so the configured indent size / tab settings drive it.
 * Indent settings only — the formatter consumes no spacing/wrapping/blank-line options.
 */
class AntlersLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {

    override fun getLanguage(): Language = AntlersLanguage.INSTANCE

    override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        if (settingsType == SettingsType.INDENT_SETTINGS) {
            consumer.showStandardOptions("INDENT_SIZE", "TAB_SIZE", "USE_TAB_CHARACTER")
        }
    }

    override fun getCodeSample(settingsType: SettingsType): String =
        "{{ collection:blog }}\n" +
        "    {{ if featured }}\n" +
        "        {{ title }}\n" +
        "    {{ /if }}\n" +
        "{{ /collection }}\n"
}
