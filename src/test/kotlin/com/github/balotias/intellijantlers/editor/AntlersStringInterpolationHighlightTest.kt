package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
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

    fun testInterpolatedVariableColored() =
        assertEquals(AntlersSyntaxHighlighter.IDENTIFIER,
            keyOver("{{ \"object-position: {logo:focus_css}\" }}", "logo"))

    fun testInterpolatedFieldColored() =
        assertEquals(AntlersSyntaxHighlighter.IDENTIFIER,
            keyOver("{{ \"object-position: {logo:focus_css}\" }}", "focus_css"))

    // Structural tokens (braces, parens, operators) inside interpolation must use an explicit-foreground
    // key. As an annotation *overlay* on the green T_STRING base, an inherited-foreground key (BRACES /
    // OPERATION_SIGN) is a no-op in schemes that don't set those foregrounds, so the string-green bleeds
    // through (reproduced in PhpStorm). HighlighterColors.TEXT carries the scheme's default foreground and
    // overrides the green — same fix as the modifier pipe.
    fun testInterpolationBraceUsesPlainText() =
        assertEquals(HighlighterColors.TEXT,
            keyOver("{{ \"object-position: {logo:focus_css}\" }}", "{"))

    fun testInterpolationColonUsesPlainText() =
        assertEquals(HighlighterColors.TEXT,
            keyOver("{{ \"{view:href | replace('mailto:', '')}\" }}", ":"))

    fun testInterpolationParenUsesPlainText() =
        assertEquals(HighlighterColors.TEXT,
            keyOver("{{ \"{view:href | replace('mailto:', '')}\" }}", "("))

    fun testInterpolatedModifierColored() =
        assertEquals(AntlersSyntaxHighlighter.MODIFIER,
            keyOver("{{ \"{title | upper}\" }}", "upper"))

    fun testInterpolatedPipeUsesPipeColor() =
        assertEquals(AntlersSyntaxHighlighter.PIPE,
            keyOver("{{ \"{title | upper}\" }}", "|"))

    fun testArrayBracketUsesPlainText() =
        assertEquals(HighlighterColors.TEXT,
            keyOver("{{ \"{['a', view:class] | classes}\" }}", "["))

    fun testArrayModifierColored() =
        assertEquals(AntlersSyntaxHighlighter.MODIFIER,
            keyOver("{{ \"{['a', view:class] | classes}\" }}", "classes"))

    fun testSurroundingStringTextNotColored() =
        assertNull(keyOver("{{ \"object-position: {logo:focus_css}\" }}", "object"))

    fun testPlainStringNotColored() =
        assertNull(keyOver("{{ \"hello world\" }}", "hello"))

    fun testEscapedBraceNotColored() =
        assertNull(keyOver("{{ \"a \\{not} b\" }}", "not"))

    fun testTwoInterpolationsColored() {
        myFixture.configureByText("p.antlers.html", "{{ \"a {one} b {two} c\" }}")
        val ids = myFixture.doHighlighting()
            .filter { it.forcedTextAttributesKey == AntlersSyntaxHighlighter.IDENTIFIER }
            .map { it.text }.toSet()
        assertTrue("both 'one' and 'two' colored, got $ids", ids.containsAll(listOf("one", "two")))
    }
}
