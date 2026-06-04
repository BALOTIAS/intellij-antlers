package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.settings.AntlersFormatterSettings
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiTreeUtil

/**
 * Indents the parameter lines of a MULTI-LINE Antlers `{{ … }}` region one level under the `{{` line and
 * leaves `}}` on its own line at the opener indent. Text-based: it sets each interior line's indentation
 * absolutely, so it is independent of the HTML formatter's opaque-block layout, and idempotent. Single-
 * line tags, comments, PHP, noparse and HTML are untouched. In-`{{ }}` spacing is owned by
 * [AntlersSpacingPostFormatProcessor]. Never throws.
 */
class AntlersMultilineTagIndentProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (!AntlersFormatterSettings.getInstance(source.project).reformatEnabled) return rangeToReformat
        val document = source.viewProvider.document ?: return rangeToReformat
        // A prior PostFormatProcessor (the spacing pass) can edit the document WITHOUT committing PSI;
        // re-sync so AntlersStatement / T_STRING text ranges match the current document text. Without
        // this, stale offsets make the range guard below skip a statement whose opener-line spacing was
        // just collapsed — leaving its params un-indented until a second reformat.
        PsiDocumentManager.getInstance(source.project).commitDocument(document)
        val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat

        val opts = CodeStyle.getIndentOptions(source)
        val unit = if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)

        // (indent range to replace, replacement); applied end-to-start so offsets stay valid.
        val edits = mutableListOf<Pair<TextRange, String>>()

        for (stmt in PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)) {
            val range = stmt.textRange
            if (range.startOffset < rangeToReformat.startOffset || range.endOffset > rangeToReformat.endOffset) continue

            val openerLine = document.getLineNumber(range.startOffset)
            val closerLine = document.getLineNumber(range.endOffset - 1)
            if (closerLine == openerLine) continue                       // single-line tag → skip

            val openerIndent = leadingWhitespace(document, openerLine)
            val contIndent = openerIndent + unit
            // `}}` token start, or -1 when the tag is unclosed (the pin=1 grammar allows a missing closer).
            val lastLeaf = PsiTreeUtil.getDeepestLast(stmt)
            val rdoubleStart =
                if (lastLeaf.node.elementType == AntlersTypes.T_RDOUBLE) lastLeaf.textRange.startOffset else -1

            // Antlers strings may contain newlines; never reindent a line that begins inside one.
            val stringSpans = PsiTreeUtil.collectElements(stmt) {
                it.node?.elementType == AntlersTypes.T_STRING
            }.map { it.textRange }

            for (line in (openerLine + 1)..closerLine) {
                val lineStart = document.getLineStartOffset(line)
                val lineEnd = document.getLineEndOffset(line)
                val lineText = document.getText(TextRange(lineStart, lineEnd))
                val firstNonWs = lineText.indexOfFirst { it != ' ' && it != '\t' }
                val contentOffset = if (firstNonWs < 0) lineEnd else lineStart + firstNonWs

                if (stringSpans.any { it.startOffset < lineStart && lineStart < it.endOffset }) continue

                val target = when {
                    firstNonWs < 0 -> ""                                  // blank line → strip
                    line == closerLine && contentOffset == rdoubleStart -> openerIndent  // `}}` on its own line
                    else -> contIndent                                    // param / content line
                }
                val indentRange = TextRange(lineStart, contentOffset)
                if (document.getText(indentRange) != target) edits.add(indentRange to target)
            }
        }

        var delta = 0
        for ((indentRange, replacement) in edits.distinct().sortedByDescending { it.first.startOffset }) {
            document.replaceString(indentRange.startOffset, indentRange.endOffset, replacement)
            delta += replacement.length - indentRange.length
        }
        return TextRange(rangeToReformat.startOffset, rangeToReformat.endOffset + delta)
    }

    private fun leadingWhitespace(document: Document, line: Int): String {
        val start = document.getLineStartOffset(line)
        val text = document.getText(TextRange(start, document.getLineEndOffset(line)))
        val n = text.indexOfFirst { it != ' ' && it != '\t' }
        return if (n < 0) text else text.substring(0, n)
    }
}
