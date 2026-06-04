package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersHintHighlightTest : BasePlatformTestCase() {
    private fun keyOver(text: String, token: String): TextAttributesKey? {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .firstOrNull { it.text == token && it.forcedTextAttributesKey != null }
            ?.forcedTextAttributesKey
    }

    fun testDirectiveTagIsHintColored() =
        assertEquals(AntlersSyntaxHighlighter.HINT, keyOver("{{#\n@collection blog\n#}}", "@collection"))

    fun testPlainCommentProseNotColored() =
        assertNull(keyOver("{{# just a note #}}", "just"))
}
