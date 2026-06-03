package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
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

    fun testInterpolationBraceColored() =
        assertEquals(AntlersSyntaxHighlighter.BRACES,
            keyOver("{{ \"object-position: {logo:focus_css}\" }}", "{"))

    fun testInterpolatedModifierColored() =
        assertEquals(AntlersSyntaxHighlighter.MODIFIER,
            keyOver("{{ \"{title | upper}\" }}", "upper"))

    fun testInterpolatedPipeIsOperator() =
        assertEquals(AntlersSyntaxHighlighter.OPERATOR,
            keyOver("{{ \"{title | upper}\" }}", "|"))

    fun testArrayBracketColored() =
        assertEquals(AntlersSyntaxHighlighter.BRACES,
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
