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

    // Logical word operators (`and`/`or`/`xor`/`not`) are Statamic LanguageKeywords lexed as plain
    // T_IDENT; the annotator paints them as keywords (like `if`/`else`) so a word operator stands out
    // instead of blending into identifier/default-foreground text.
    fun testWordOperatorOrColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ as or 'a' }}", "or"))

    fun testWordOperatorAndColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ a and b }}", "and"))

    fun testWordOperatorXorColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ a xor b }}", "xor"))

    fun testWordOperatorNotColoredInCondition() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ if not foo }}{{ /if }}", "not"))

    // The real-world trigger: `or` inside an interpolated HTML tag name `<{{ as or 'a' }}>`.
    fun testWordOperatorInInterpolatedHtmlTag() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("<{{ as or 'a' }}>x</{{ as or 'a' }}>", "or"))

    // A path segment that merely shares an operator's spelling (`foo.or`) is a field access, not an
    // operator — it must stay an identifier.
    fun testOperatorWordAsPathSegmentNotColored() =
        assertNull(keyOver("{{ foo.or }}", "or"))

    // `switch between=…` is the cycling tag; `switch(…)` is the inline match-like operator. Same head,
    // different role — disambiguated by the trailing `(`.
    fun testSwitchTagColoredAsTag() =
        assertEquals(AntlersSyntaxHighlighter.TAG, keyOver("{{ switch between='a|b' }}", "switch"))

    fun testSwitchOperatorColoredAsKeyword() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD,
            keyOver("{{ switch((size == 'sm') => '12px', () => '16px') }}", "switch"))

    // The switch operator nested in a tag-parameter string is injected as Antlers and rewritten to the
    // single-brace `{ switch(…) }` inline form — `switch` is then a bare ident, not a name-path. It must
    // still read as the operator (keyword), not be mistaken for an inline tag call.
    fun testSwitchOperatorInInterpolatedStringColoredAsKeyword() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD,
            keyOver("{{ picture sizes=\"{{ switch((s == 'md') => 'a', () => 'b') }}\" }}", "switch"))

    fun testBoundParameterNameColored() =
        assertEquals(AntlersSyntaxHighlighter.PARAMETER, keyOver("{{ partial :src=\"x\" }}", "src"))

    // Antlers template/layout structural constructs (stacks/once/slot) are catalog tags now, so their
    // heads color like any other tag.
    fun testOnceTagColored() =
        assertEquals(AntlersSyntaxHighlighter.TAG, keyOver("{{ once }}{{ /once }}", "once"))

    fun testPushTagColored() =
        assertEquals(AntlersSyntaxHighlighter.TAG, keyOver("{{ push:scripts }}{{ /push:scripts }}", "push"))

    fun testStackTagColored() =
        assertEquals(AntlersSyntaxHighlighter.TAG, keyOver("{{ stack:scripts }}", "stack"))

    // Query/builder operators (`where`/`merge`/`orderby`/`groupby`/`take`/`skip`/`pluck`) are Statamic
    // language operators lexed as plain T_IDENT — keyword-colored like the logical word operators when
    // they stand as an infix operator in an expression body.
    fun testQueryOperatorWhereColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ players where ('x') }}", "where"))

    fun testQueryOperatorTakeColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ players take (2) }}", "take"))

    fun testQueryOperatorMergeColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ a merge b }}", "merge"))

    // A field access (`foo.take`) or a standalone variable (`{{ take }}`) sharing an operator's spelling
    // must stay an identifier, not be mistaken for the operator.
    fun testQueryOperatorAsPathSegmentNotColored() =
        assertNull(keyOver("{{ foo.take }}", "take"))

    fun testQueryOperatorAsLoneVariableNotColored() =
        assertNull(keyOver("{{ take }}", "take"))

    // The `void` placeholder (nullifies a value, e.g. `{{ x ? 'a' : void }}`) is a language keyword.
    fun testVoidKeywordColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ x ? 'a' : void }}", "void"))

    // …but a field access sharing the spelling (`foo.void`) stays an identifier.
    fun testVoidAsPathSegmentNotColored() =
        assertNull(keyOver("{{ foo.void }}", "void"))

    // A tag query condition `field:operator="value"` — the operator reads as an operator, not a param.
    fun testConditionOperatorColored() =
        assertEquals(AntlersSyntaxHighlighter.OPERATOR,
            keyOver("{{ collection:blog title:contains=\"tao\" }}", "contains"))

    // A genuine bound parameter (`:src=`) on a non-condition tag stays a parameter name.
    fun testBoundParamNotMiscoloredAsConditionOperator() =
        assertEquals(AntlersSyntaxHighlighter.PARAMETER, keyOver("{{ partial :src=\"x\" }}", "src"))

    // The `:` of a ternary/elvis operator is a loose colon in the expression — painted as an operator to
    // match the `?`, not left as subtle path punctuation.
    fun testElvisColonColoredAsOperator() =
        assertEquals(AntlersSyntaxHighlighter.OPERATOR, keyOver("{{ srcset_from ?: 'default' }}", ":"))

    fun testTernaryColonColoredAsOperator() =
        assertEquals(AntlersSyntaxHighlighter.OPERATOR, keyOver("{{ test ? 'something' : 'other' }}", ":"))

    // The user-reported shape: an elvis inside a single-brace string interpolation.
    fun testElvisColonInInterpolationColoredAsOperator() =
        assertEquals(AntlersSyntaxHighlighter.OPERATOR,
            keyOver("{{ partial src=\"{ srcset_from ?: 'snippets/srcset_default' }\" }}", ":"))

    // Structural colons (path / modifier arg / bound parameter) stay subtle — the annotator must not
    // repaint them as operators.
    fun testPathColonNotOperatorColored() = assertNull(keyOver("{{ collection:blog }}", ":"))
    fun testModifierColonNotOperatorColored() = assertNull(keyOver("{{ title | upper:2 }}", ":"))
    fun testBoundParamColonNotOperatorColored() = assertNull(keyOver("{{ partial :src=\"x\" }}", ":"))

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
