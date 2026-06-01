package com.github.balotias.intellijantlers.highlighting

import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class AntlersSyntaxHighlighter : SyntaxHighlighterBase() {
    companion object {
        val BRACES = TextAttributesKey.createTextAttributesKey("ANTLERS_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val IDENTIFIER = TextAttributesKey.createTextAttributesKey("ANTLERS_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val STRING = TextAttributesKey.createTextAttributesKey("ANTLERS_STRING", DefaultLanguageHighlighterColors.STRING)
        val NUMBER = TextAttributesKey.createTextAttributesKey("ANTLERS_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val COMMENT = TextAttributesKey.createTextAttributesKey("ANTLERS_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)

        private val BRACES_KEYS = arrayOf(BRACES)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val STRING_KEYS = arrayOf(STRING)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val EMPTY_KEYS = arrayOf<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = FlexAdapter(_AntlersLexer(null))

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            AntlersTypes.T_OPEN_BRACE, AntlersTypes.T_CLOSE_BRACE -> BRACES_KEYS
            AntlersTypes.T_IDENTIFIER -> IDENTIFIER_KEYS
            AntlersTypes.T_STRING -> STRING_KEYS
            AntlersTypes.T_NUMBER -> NUMBER_KEYS
            AntlersTypes.T_COMMENT_START, AntlersTypes.T_COMMENT_END, AntlersTypes.T_COMMENT_TEXT -> COMMENT_KEYS
            AntlersTypes.T_OPERATOR, AntlersTypes.T_EQUALS, AntlersTypes.T_MODIFIER_PIPE, AntlersTypes.T_SLASH, AntlersTypes.T_AT, AntlersTypes.T_COLON -> IDENTIFIER_KEYS
            else -> EMPTY_KEYS
        }
    }
}
