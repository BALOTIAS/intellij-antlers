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

    // An inline tag call `{tag param=…}` (e.g. `href = {obfuscate_link …}`) leaves the tag name as a bare
    // T_IDENT, not wrapped in a NAME_PATH. Color it like any other tag head.
    fun testInlineTagHeadColored() =
        assertEquals(AntlersSyntaxHighlighter.TAG, keyOver("{{ a = {collection from=\"blog\"} }}", "collection"))

    // …but an array key that happens to share a tag's name (`{collection: 'y'}`) must NOT be tag-colored.
    fun testInlineArrayKeyNotColoredAsTag() =
        assertNull(keyOver("{{ a = {collection: 'y'} }}", "collection"))

    fun testConditionKeywordColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ if count > 0 }}{{ /if }}", "if"))

    fun testModifierColored() =
        assertEquals(AntlersSyntaxHighlighter.MODIFIER, keyOver("{{ title | upper }}", "upper"))

    fun testPlainVariableNotColored() =
        assertNull(keyOver("{{ title }}", "title"))

    fun testParameterNameColored() =
        assertEquals(AntlersSyntaxHighlighter.PARAMETER, keyOver("{{ collection from=\"x\" }}", "from"))

    fun testBoundParameterNameColored() =
        assertEquals(AntlersSyntaxHighlighter.PARAMETER, keyOver("{{ partial :src=\"x\" }}", "src"))

    /** Count keyword/tag-colored highlights over [token] — used to assert the closer is painted too. */
    private fun coloredCount(text: String, token: String, key: TextAttributesKey): Int {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting().count { it.text == token && it.forcedTextAttributesKey == key }
    }

    fun testClosingConditionKeywordColored() =
        assertEquals("both the opening and closing 'if' are keyword-colored",
            2, coloredCount("{{ if x }}{{ /if }}", "if", AntlersSyntaxHighlighter.KEYWORD))

    fun testClosingUnlessKeywordColored() =
        assertEquals("both the opening and closing 'unless' are keyword-colored",
            2, coloredCount("{{ unless x }}{{ /unless }}", "unless", AntlersSyntaxHighlighter.KEYWORD))

    fun testClosingTagColored() =
        assertEquals("both the opening and closing 'collection' are tag-colored",
            2, coloredCount("{{ collection }}{{ /collection }}", "collection", AntlersSyntaxHighlighter.TAG))
}
