package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class LexerTest {

    private fun lex(input: String): List<Pair<IElementType, String>> {
        val lexer = _AntlersLexer(StringReader(input))
        val out = mutableListOf<Pair<IElementType, String>>()
        var t = lexer.advance()
        while (t != null) {
            out.add(t to lexer.yytext().toString())
            t = lexer.advance()
        }
        return out
    }

    private fun types(input: String) = lex(input).map { it.first }

    @Test
    fun outerHtmlAndTag() {
        assertEquals(
            listOf(
                AntlersTypes.T_OUTER_HTML,   // "Hi "
                AntlersTypes.T_LDOUBLE,      // "{{"
                AntlersTypes.T_WS,
                AntlersTypes.T_IDENT,        // "title"
                AntlersTypes.T_WS,
                AntlersTypes.T_RDOUBLE       // "}}"
            ),
            types("Hi {{ title }}")
        )
    }

    @Test
    fun escapedDelimiterIsOuterHtml() {
        val ts = types("@{{ title }}")
        assert(ts.none { it == AntlersTypes.T_LDOUBLE }) { "escaped @{{ opened an expression: $ts" }
        assertEquals(AntlersTypes.T_OUTER_HTML, ts.first())
    }

    @Test
    fun comment() {
        assertEquals(
            listOf(AntlersTypes.T_COMMENT_OPEN, AntlersTypes.T_COMMENT_TEXT, AntlersTypes.T_COMMENT_CLOSE),
            types("{{# hidden #}}")
        )
    }

    @Test
    fun phpRawAndEcho() {
        assertEquals(
            listOf(AntlersTypes.T_PHP_RAW_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_RAW_CLOSE),
            types("{{? \$x = 1; ?}}")
        )
        assertEquals(
            listOf(AntlersTypes.T_PHP_ECHO_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_ECHO_CLOSE),
            types("{{\$ \$x \$}}")
        )
    }

    @Test
    fun noparseIsRaw() {
        val ts = types("{{ noparse }}{{ title }}{{ /noparse }}")
        assertEquals(AntlersTypes.T_NOPARSE_OPEN, ts.first())
        assertEquals(AntlersTypes.T_NOPARSE_CLOSE, ts.last())
        assert(ts.none { it == AntlersTypes.T_IDENT }) { "noparse body was tokenised: $ts" }
    }

    @Test
    fun modifierPipeAndOperators() {
        val ts = types("{{ a | upper }}{{ if x == 1 }}")
        assert(ts.contains(AntlersTypes.T_PIPE))
        assert(ts.contains(AntlersTypes.T_OP))
    }

    @Test
    fun accessAndParams() {
        val ts = types("{{ collection:blog limit=\"5\" :sort=\"order\" }}")
        assert(ts.contains(AntlersTypes.T_COLON))
        assert(ts.contains(AntlersTypes.T_EQUALS))
        assert(ts.contains(AntlersTypes.T_STRING))
    }
}
