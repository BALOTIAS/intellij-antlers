package com.github.balotias.intellijantlers.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersColorSettingsPageTest : BasePlatformTestCase() {

    fun testDescriptorsCoverExactlyTheSixKeys() {
        val page = AntlersColorSettingsPage()
        val keys = page.attributeDescriptors.map { it.key }.toSet()
        val expected = setOf(
            AntlersSyntaxHighlighter.BRACES,
            AntlersSyntaxHighlighter.IDENTIFIER,
            AntlersSyntaxHighlighter.STRING,
            AntlersSyntaxHighlighter.NUMBER,
            AntlersSyntaxHighlighter.COMMENT,
            AntlersSyntaxHighlighter.OPERATOR,
        )
        assertEquals(expected, keys)
    }

    fun testBasics() {
        val page = AntlersColorSettingsPage()
        assertEquals("Antlers", page.displayName)
        assertTrue("demo text present", page.demoText.isNotBlank())
        assertTrue("reuses the Antlers highlighter", page.highlighter is AntlersSyntaxHighlighter)
        assertNull("no additional highlighting tags", page.additionalHighlightingTagToDescriptorMap)
    }
}
