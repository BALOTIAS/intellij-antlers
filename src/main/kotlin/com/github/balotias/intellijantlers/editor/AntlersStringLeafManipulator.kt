package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lexer.FlexAdapter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator

/**
 * Write-back for edits made inside an injected interpolation fragment. Splices the new content into the
 * string leaf and rebuilds it, bailing (round-trip guard) if the result no longer lexes to a single
 * T_STRING covering the whole text — e.g. an unescaped quote that ends the string early — so a bad edit
 * leaves the source intact instead of corrupting it.
 */
class AntlersStringLeafManipulator : AbstractElementManipulator<AntlersStringLeaf>() {
    override fun handleContentChange(
        element: AntlersStringLeaf,
        range: TextRange,
        newContent: String
    ): AntlersStringLeaf {
        val old = element.text
        val newText = old.substring(0, range.startOffset) + newContent + old.substring(range.endOffset)
        if (!isSingleString(newText)) return element
        return element.replaceWithText(newText).psi as AntlersStringLeaf
    }

    private fun isSingleString(text: String): Boolean {
        val lexer = FlexAdapter(_AntlersLexer(null))
        lexer.start(text, 0, text.length, _AntlersLexer.EXPR)
        return lexer.tokenType == AntlersTypes.T_STRING && lexer.tokenEnd == text.length
    }
}
