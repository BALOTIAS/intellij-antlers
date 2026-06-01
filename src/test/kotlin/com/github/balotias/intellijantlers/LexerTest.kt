package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.lexer._AntlersLexer
import org.junit.Test
import java.io.StringReader

class LexerTest {
    @Test
    fun testLexer() {
        val lexer = _AntlersLexer(StringReader("Hello {{ test }} World"))
        var token = lexer.advance()
        while (token != null) {
            println(token)
            token = lexer.advance()
        }
    }
}
