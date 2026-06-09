package com.github.balotias.intellijantlers.highlighting

import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntlersSyntaxHighlighterTest {
    private val hl = AntlersSyntaxHighlighter()

    @Test fun pipeUsesItsOwnPipeColorNotOperator() {
        val keys = hl.getTokenHighlights(AntlersTypes.T_PIPE).toList()
        assertTrue("pipe is PIPE-colored", keys.contains(AntlersSyntaxHighlighter.PIPE))
        assertFalse("pipe is no longer Operator", keys.contains(AntlersSyntaxHighlighter.OPERATOR))
    }

    // The genuine comparison/logical/arithmetic operators (T_OP) and the arrow get the Operator color.
    @Test fun symbolicOperatorsAreOperatorColored() {
        for (t in listOf(AntlersTypes.T_OP, AntlersTypes.T_ARROW)) {
            assertTrue("$t should be operator-colored",
                hl.getTokenHighlights(t).toList().contains(AntlersSyntaxHighlighter.OPERATOR))
        }
    }

    // OPERATION_SIGN renders as default foreground in most themes, so `==`/`<` looked uncolored.
    // The Operator color falls back to KEYWORD (themed) so symbolic operators actually stand out.
    @Test fun operatorColorIsVisibleNotDefaultForeground() {
        assertEquals(DefaultLanguageHighlighterColors.KEYWORD,
            AntlersSyntaxHighlighter.OPERATOR.fallbackAttributeKey)
    }

    // Path/structural punctuation (`:` `.` `/` `=` `%`) is split off onto a separate, subtle key so
    // that making operators visible does not also paint every colon and dot in a path.
    @Test fun pathPunctuationIsSeparateAndNotOperatorColored() {
        for (t in listOf(AntlersTypes.T_COLON, AntlersTypes.T_DOT, AntlersTypes.T_SLASH,
                AntlersTypes.T_EQUALS, AntlersTypes.T_PERCENT)) {
            val keys = hl.getTokenHighlights(t).toList()
            assertTrue("$t should be punctuation-colored", keys.contains(AntlersSyntaxHighlighter.PUNCTUATION))
            assertFalse("$t must not use the visible operator color", keys.contains(AntlersSyntaxHighlighter.OPERATOR))
        }
    }

    @Test fun phpTagDelimitersAreBraces() {
        for (t in listOf(AntlersTypes.T_PHP_TAG_OPEN, AntlersTypes.T_PHP_ECHO_TAG_OPEN, AntlersTypes.T_PHP_TAG_CLOSE)) {
            assertTrue("$t should be brace-colored",
                hl.getTokenHighlights(t).toList().contains(AntlersSyntaxHighlighter.BRACES))
        }
    }
}
