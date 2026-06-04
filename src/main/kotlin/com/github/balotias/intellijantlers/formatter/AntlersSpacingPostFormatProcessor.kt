package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.settings.AntlersFormatterSettings
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiTreeUtil

/**
 * Normalizes spacing inside Antlers {{ }} regions after Reformat Code: exactly one space against the
 * delimiters and around each modifier pipe. Token-based (a `|` inside a string is T_STRING, never
 * T_PIPE, so strings are safe). Runs after the HTML formatter and edits only inside {{ }} spans.
 * Never throws.
 */
class AntlersSpacingPostFormatProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (!AntlersFormatterSettings.getInstance(source.project).reformatEnabled) return rangeToReformat
        val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat
        val document = source.viewProvider.document ?: return rangeToReformat

        // All leaves of the Antlers tree, in document order.
        val leaves = mutableListOf<PsiElement>()
        var leaf: PsiElement? = PsiTreeUtil.getDeepestFirst(antlers)
        while (leaf != null) {
            leaves.add(leaf)
            leaf = PsiTreeUtil.nextLeaf(leaf)
        }

        fun isWs(e: PsiElement?): Boolean =
            e != null && (e is PsiWhiteSpace || e.node.elementType == AntlersTypes.T_WS)

        fun nextSignificant(i: Int): PsiElement? {
            var j = i + 1
            while (j < leaves.size && isWs(leaves[j])) j++
            return leaves.getOrNull(j)
        }
        fun prevSignificant(i: Int): PsiElement? {
            var j = i - 1
            while (j >= 0 && isWs(leaves[j])) j--
            return leaves.getOrNull(j)
        }

        // (range to replace, replacement) — an insertion is a zero-length range.
        val edits = mutableListOf<Pair<TextRange, String>>()

        fun normalizeGap(aEnd: Int, bStart: Int) {
            if (bStart < aEnd) return
            val gap = TextRange(aEnd, bStart)
            if (gap.startOffset < rangeToReformat.startOffset || gap.endOffset > rangeToReformat.endOffset) return
            val current = document.getText(gap)
            if (current.contains('\n')) return          // preserve intentional line breaks (multi-line tags)
            if (current == " ") return                 // already correct (idempotent)
            if (current.isNotEmpty() && current.isNotBlank()) return  // non-whitespace in gap → skip (defensive)
            edits.add(gap to " ")
        }

        for ((i, e) in leaves.withIndex()) {
            when (e.node.elementType) {
                AntlersTypes.T_LDOUBLE -> {
                    val nxt = nextSignificant(i) ?: continue
                    if (nxt.node.elementType == AntlersTypes.T_RDOUBLE) continue   // empty {{ }}
                    normalizeGap(e.textRange.endOffset, nxt.textRange.startOffset)
                }
                AntlersTypes.T_RDOUBLE -> {
                    val prv = prevSignificant(i) ?: continue
                    if (prv.node.elementType == AntlersTypes.T_LDOUBLE) continue   // empty {{ }}
                    normalizeGap(prv.textRange.endOffset, e.textRange.startOffset)
                }
                AntlersTypes.T_PIPE -> {
                    prevSignificant(i)?.let { normalizeGap(it.textRange.endOffset, e.textRange.startOffset) }
                    nextSignificant(i)?.let { normalizeGap(e.textRange.endOffset, it.textRange.startOffset) }
                }
            }
        }

        var delta = 0
        // distinct(): a `| }}` boundary makes the pipe's after-gap and the }}'s before-gap reference the
        // same offset pair — dedupe so they aren't double-applied (which would double-insert a space).
        for ((range, replacement) in edits.distinct().sortedByDescending { it.first.startOffset }) {
            document.replaceString(range.startOffset, range.endOffset, replacement)
            delta += replacement.length - range.length
        }
        return TextRange(rangeToReformat.startOffset, rangeToReformat.endOffset + delta)
    }
}
