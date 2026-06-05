package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lexer.FlexAdapter
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

/**
 * Colors Antlers `{ … }` interpolation inside a string literal as a real Antlers expression. The lexer
 * emits the whole literal as one flat T_STRING, so this overlays per-token colors on the interpolation
 * sub-spans (running after lexer highlighting, like [AntlersSemanticHighlightAnnotator]). The surrounding
 * string text keeps STRING. Highlighting only — no PSI/references inside the interpolation.
 */
class AntlersStringInterpolationAnnotator : Annotator {

    companion object {
        /**
         * Braces, parens and operators inside the interpolation. We paint these as an annotation *overlay*
         * on top of the green T_STRING base, so the key MUST carry an explicit foreground: an inherited-
         * foreground key (BRACES / OPERATION_SIGN) is a no-op as an overlay in color schemes that don't set
         * those foregrounds, leaving the string-green to bleed through (observed in PhpStorm; runIde's
         * default scheme hides it). HighlighterColors.TEXT carries the scheme's default foreground — the
         * same reason the modifier pipe uses it.
         */
        private val STRUCTURAL: TextAttributesKey = HighlighterColors.TEXT
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != AntlersTypes.T_STRING) return
        val text = element.text
        val base = element.textRange.startOffset
        for (span in AntlersInterpolationScanner.scan(text)) {
            paint(holder, base + span.openBrace, base + span.openBrace + 1, STRUCTURAL)
            paint(holder, base + span.closeBrace, base + span.closeBrace + 1, STRUCTURAL)
            if (span.contentStart < span.contentEnd) lexInner(text, span, base, holder)
        }
    }

    private fun lexInner(text: String, span: AntlersInterpolationScanner.Span, base: Int, holder: AnnotationHolder) {
        val lexer = FlexAdapter(_AntlersLexer(null))
        lexer.start(text, span.contentStart, span.contentEnd, _AntlersLexer.EXPR)
        var prev: IElementType? = null
        while (true) {
            val type = lexer.tokenType ?: break
            colorFor(type, prev)?.let { paint(holder, base + lexer.tokenStart, base + lexer.tokenEnd, it) }
            if (type != AntlersTypes.T_WS) prev = type
            lexer.advance()
        }
    }

    private fun colorFor(type: IElementType, prev: IElementType?): TextAttributesKey? = when (type) {
        // Pipe-adjacency heuristic: only the name right after `|` is the modifier. Without PSI inside the
        // interpolation, modifier *arguments* (`{x | foo:arg}`) stay IDENTIFIER — a deliberate approximation.
        AntlersTypes.T_IDENT ->
            if (prev == AntlersTypes.T_PIPE) AntlersSyntaxHighlighter.MODIFIER else AntlersSyntaxHighlighter.IDENTIFIER
        AntlersTypes.T_DOLLAR -> AntlersSyntaxHighlighter.IDENTIFIER
        AntlersTypes.T_STRING -> AntlersSyntaxHighlighter.STRING
        AntlersTypes.T_NUMBER -> AntlersSyntaxHighlighter.NUMBER
        AntlersTypes.T_PIPE -> AntlersSyntaxHighlighter.PIPE
        AntlersTypes.T_COLON, AntlersTypes.T_DOT, AntlersTypes.T_OP,
        AntlersTypes.T_EQUALS, AntlersTypes.T_ARROW, AntlersTypes.T_SLASH,
        AntlersTypes.T_COMMA, AntlersTypes.T_SEMICOLON,
        AntlersTypes.T_LBRACE, AntlersTypes.T_RBRACE, AntlersTypes.T_LBRACKET, AntlersTypes.T_RBRACKET,
        AntlersTypes.T_LPAREN, AntlersTypes.T_RPAREN, AntlersTypes.T_AT -> STRUCTURAL
        else -> null   // T_WS, BAD_CHARACTER, etc. → leave the STRING base color
    }

    private fun paint(holder: AnnotationHolder, start: Int, end: Int, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(start, end))
            .textAttributes(key)
            .create()
    }
}
