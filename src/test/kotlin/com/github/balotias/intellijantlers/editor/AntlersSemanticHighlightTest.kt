package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersSemanticHighlightTest : BasePlatformTestCase() {

    private fun keyOver(text: String, token: String): TextAttributesKey? {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .firstOrNull { it.text == token && it.forcedTextAttributesKey != null }
            ?.forcedTextAttributesKey
    }

    fun testTagHeadColored() =
        assertEquals(AntlersSyntaxHighlighter.TAG, keyOver("{{ collection:blog }}", "collection"))

    fun testConditionKeywordColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ if count > 0 }}{{ /if }}", "if"))

    fun testModifierColored() =
        assertEquals(AntlersSyntaxHighlighter.MODIFIER, keyOver("{{ title | upper }}", "upper"))

    fun testPlainVariableNotColored() =
        assertNull(keyOver("{{ title }}", "title"))
}
