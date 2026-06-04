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
}
