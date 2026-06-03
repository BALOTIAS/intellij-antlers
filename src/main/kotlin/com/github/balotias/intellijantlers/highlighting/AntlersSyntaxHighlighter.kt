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
        val OPERATOR = TextAttributesKey.createTextAttributesKey("ANTLERS_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)

        val TAG = TextAttributesKey.createTextAttributesKey("ANTLERS_TAG", DefaultLanguageHighlighterColors.METADATA)
        val KEYWORD = TextAttributesKey.createTextAttributesKey("ANTLERS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val MODIFIER = TextAttributesKey.createTextAttributesKey("ANTLERS_MODIFIER", DefaultLanguageHighlighterColors.INSTANCE_METHOD)
        val PARAMETER = TextAttributesKey.createTextAttributesKey("ANTLERS_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER)

        val FRONTMATTER_FENCE = TextAttributesKey.createTextAttributesKey("ANTLERS_FRONTMATTER_FENCE", DefaultLanguageHighlighterColors.METADATA)
        val FRONTMATTER_KEY = TextAttributesKey.createTextAttributesKey("ANTLERS_FRONTMATTER_KEY", DefaultLanguageHighlighterColors.KEYWORD)
        val FRONTMATTER_VALUE = TextAttributesKey.createTextAttributesKey("ANTLERS_FRONTMATTER_VALUE", DefaultLanguageHighlighterColors.STRING)

        private val BRACES_KEYS = arrayOf(BRACES)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val STRING_KEYS = arrayOf(STRING)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val OPERATOR_KEYS = arrayOf(OPERATOR)
        private val EMPTY_KEYS = arrayOf<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = FlexAdapter(_AntlersLexer(null))

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            AntlersTypes.T_LDOUBLE, AntlersTypes.T_RDOUBLE,
            AntlersTypes.T_PHP_RAW_OPEN, AntlersTypes.T_PHP_RAW_CLOSE,
            AntlersTypes.T_PHP_ECHO_OPEN, AntlersTypes.T_PHP_ECHO_CLOSE,
            AntlersTypes.T_NOPARSE_OPEN, AntlersTypes.T_NOPARSE_CLOSE -> BRACES_KEYS

            AntlersTypes.T_IDENT, AntlersTypes.T_DOLLAR -> IDENTIFIER_KEYS
            AntlersTypes.T_STRING -> STRING_KEYS
            AntlersTypes.T_NUMBER -> NUMBER_KEYS
            AntlersTypes.T_COMMENT_OPEN, AntlersTypes.T_COMMENT_CLOSE, AntlersTypes.T_COMMENT_TEXT -> COMMENT_KEYS

            AntlersTypes.T_OP, AntlersTypes.T_PIPE, AntlersTypes.T_EQUALS, AntlersTypes.T_ARROW,
            AntlersTypes.T_COLON, AntlersTypes.T_SLASH, AntlersTypes.T_DOT -> OPERATOR_KEYS

            else -> EMPTY_KEYS
        }
    }
}
