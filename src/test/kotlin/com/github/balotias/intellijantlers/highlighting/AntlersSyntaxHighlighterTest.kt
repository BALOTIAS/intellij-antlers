package com.github.balotias.intellijantlers.highlighting

import com.github.balotias.intellijantlers.psi.AntlersTypes
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

    @Test fun otherOperatorsStayOperator() {
        assertTrue(hl.getTokenHighlights(AntlersTypes.T_COLON).toList().contains(AntlersSyntaxHighlighter.OPERATOR))
        assertTrue(hl.getTokenHighlights(AntlersTypes.T_DOT).toList().contains(AntlersSyntaxHighlighter.OPERATOR))
    }

    // `%` (modulo and the tag-disambiguation prefix) keeps the operator color it had as part of T_OP.
    @Test fun percentIsOperatorColored() {
        assertTrue(hl.getTokenHighlights(AntlersTypes.T_PERCENT).toList().contains(AntlersSyntaxHighlighter.OPERATOR))
    }

    @Test fun phpTagDelimitersAreBraces() {
        for (t in listOf(AntlersTypes.T_PHP_TAG_OPEN, AntlersTypes.T_PHP_ECHO_TAG_OPEN, AntlersTypes.T_PHP_TAG_CLOSE)) {
            assertTrue("$t should be brace-colored",
                hl.getTokenHighlights(t).toList().contains(AntlersSyntaxHighlighter.BRACES))
        }
    }
}
