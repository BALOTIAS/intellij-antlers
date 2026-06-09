package com.github.balotias.intellijantlers.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersColorSettingsPageTest : BasePlatformTestCase() {

    fun testDescriptorsCoverAllKeys() {
        val keys = AntlersColorSettingsPage().attributeDescriptors.map { it.key }.toSet()
        val expected = setOf(
            AntlersSyntaxHighlighter.BRACES, AntlersSyntaxHighlighter.TAG, AntlersSyntaxHighlighter.KEYWORD,
            AntlersSyntaxHighlighter.MODIFIER, AntlersSyntaxHighlighter.PARAMETER, AntlersSyntaxHighlighter.IDENTIFIER,
            AntlersSyntaxHighlighter.STRING, AntlersSyntaxHighlighter.NUMBER, AntlersSyntaxHighlighter.COMMENT,
            AntlersSyntaxHighlighter.OPERATOR, AntlersSyntaxHighlighter.PUNCTUATION,
            AntlersSyntaxHighlighter.PIPE, AntlersSyntaxHighlighter.HINT,
            AntlersSyntaxHighlighter.FRONTMATTER_FENCE,
        )
        assertEquals(expected, keys)
    }

    fun testBasics() {
        val page = AntlersColorSettingsPage()
        assertEquals("Antlers", page.displayName)
        assertTrue("demo text present", page.demoText.isNotBlank())
        assertTrue("reuses the Antlers highlighter", page.highlighter is AntlersSyntaxHighlighter)
        assertEquals("preview maps the semantic tags", setOf("tag", "kw", "mod", "param", "fmfence", "pipe", "hint"),
            page.additionalHighlightingTagToDescriptorMap!!.keys)
    }
}
