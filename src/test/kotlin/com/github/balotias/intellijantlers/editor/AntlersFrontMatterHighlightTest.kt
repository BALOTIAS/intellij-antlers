package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFrontMatterHighlightTest : BasePlatformTestCase() {

    private fun keyOver(text: String, token: String): TextAttributesKey? {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .firstOrNull { it.text == token && it.forcedTextAttributesKey != null }
            ?.forcedTextAttributesKey
    }

    fun testFenceColored() =
        assertEquals(AntlersSyntaxHighlighter.FRONTMATTER_FENCE, keyOver("---\nfoo: bar\n---\n{{ title }}", "---"))

    fun testKeyColored() =
        assertEquals(AntlersSyntaxHighlighter.FRONTMATTER_KEY, keyOver("---\nfoo: bar\n---\n{{ title }}", "foo"))

    fun testValueColored() =
        assertEquals(AntlersSyntaxHighlighter.FRONTMATTER_VALUE, keyOver("---\nfoo: bar\n---\n{{ title }}", "bar"))

    fun testNoFrontMatterNotColored() =
        assertNull(keyOver("{{ title }}\nfoo: bar", "foo"))

    fun testOnlyFrontMatterKeyIsKeyColored() {
        // The `foo` in the body `{{ view:foo }}` must NOT get the front-matter key color.
        myFixture.configureByText("p.antlers.html", "---\nfoo: bar\n---\n{{ view:foo }}")
        val keyColored = myFixture.doHighlighting()
            .filter { it.text == "foo" && it.forcedTextAttributesKey == AntlersSyntaxHighlighter.FRONTMATTER_KEY }
        assertEquals("only the front-matter key is FM-key-colored", 1, keyColored.size)
    }
}
