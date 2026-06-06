package com.github.balotias.intellijantlers.editor

import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersStringInterpolationHighlightTest : BasePlatformTestCase() {

    /** The forced color over the first highlight whose covered text is exactly [token], or null. */
    private fun keyOver(text: String, token: String): TextAttributesKey? {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .firstOrNull { it.text == token && it.forcedTextAttributesKey != null }
            ?.forcedTextAttributesKey
    }

    fun testInterpolationBraceUsesPlainText() =
        assertEquals(HighlighterColors.TEXT,
            keyOver("{{ \"object-position: {logo:focus_css}\" }}", "{"))

    fun testSurroundingStringTextNotColored() =
        assertNull(keyOver("{{ \"object-position: {logo:focus_css}\" }}", "object"))

    fun testPlainStringNotColored() =
        assertNull(keyOver("{{ \"hello world\" }}", "hello"))

    fun testEscapedBraceNotColored() =
        assertNull(keyOver("{{ \"a \\{not} b\" }}", "not"))
}
