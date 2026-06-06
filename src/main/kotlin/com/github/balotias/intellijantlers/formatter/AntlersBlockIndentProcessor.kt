package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.github.balotias.intellijantlers.scope.NestingNode
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
 * Indents the body of paired Antlers tags/conditions one level per enclosing pair, the level the inline
 * model in [AntlersHtmlFormattingModelBuilder] omits. Antlers `{{ }}` and text lines are set absolutely
 * (`base + depth*unit`) from the stable outermost-opener anchor; HTML element lines are shifted additively
 * (`currentIndent + depth*unit`) on top of the HTML formatter's own re-normalized indent — so the pass is
 * idempotent. Multi-line tag param lines are left to [AntlersMultilineTagIndentProcessor] (runs after).
 * Text-based; never throws. In-`{{ }}` spacing is owned by [AntlersSpacingPostFormatProcessor].
 */
class AntlersBlockIndentProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (!AntlersFormatterSettings.getInstance(source.project).reformatEnabled) return rangeToReformat
        val document = source.viewProvider.document ?: return rangeToReformat
        // Re-sync PSI after the spacing pass edited the document without committing (same guard the
        // multiline processor uses) so statement/string ranges match the current text.
        PsiDocumentManager.getInstance(source.project).commitDocument(document)
        val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat

        val tree = AntlersNestingTreeBuilder.build(antlers, source.project)
        if (tree.roots.isEmpty()) return rangeToReformat               // no paired blocks → nothing to do

        val opts = CodeStyle.getIndentOptions(source)
        val unit = if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
        val lineCount = document.lineCount

        val depth = IntArray(lineCount)
        val touched = BooleanArray(lineCount)                          // a line some node's walk assigned
        val baseOf = arrayOfNulls<String>(lineCount)
        val continuation = BooleanArray(lineCount)                     // line strictly inside a multi-line tag

        for (stmt in PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)) {
            val sLine = document.getLineNumber(stmt.textRange.startOffset)
            val eLine = document.getLineNumber(stmt.textRange.endOffset - 1)
            for (l in (sLine + 1)..eLine) if (l in 0 until lineCount) continuation[l] = true
        }

        fun openerLine(n: NestingNode) = document.getLineNumber(n.opener.textRange.startOffset)

        // Lines whose statement is an `else`/`elseif` branch marker — they render at their {{ if }}'s
        // depth (one less than the body), so each branch's content stays indented +1 under them.
        val elseLines: Set<Int> = PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)
            .filter {
                val kw = (it.condition as? AntlersConditionMixin)?.keyword
                kw == "else" || kw == "elseif"
            }
            .map { document.getLineNumber(it.textRange.startOffset) }
            .toSet()

        fun walk(node: NestingNode, d: Int, base: String) {
            val oLine = openerLine(node)
            // Skip unclosed multiline-opener statements (e.g. {{ collection:blog\nlimit="3"\n}} with no
            // {{ /collection }}): they are self-contained tags, not block openers, so their "body" lines
            // (the continuation params and whatever follows) must not be indented as a block body.
            val openerELine = document.getLineNumber(node.opener.textRange.endOffset - 1)
            if (node.closer == null && openerELine > oLine) return
            val cLine = node.closer?.let { document.getLineNumber(it.textRange.startOffset) }
            val bodyEnd = if (cLine != null) cLine - 1 else lineCount - 1
            for (l in (oLine + 1)..bodyEnd) if (l in 0 until lineCount) {
                depth[l] = if (l in elseLines) d else d + 1; touched[l] = true; baseOf[l] = base
            }
            if (cLine != null && cLine in 0 until lineCount) { depth[cLine] = d; touched[cLine] = true; baseOf[cLine] = base }
            if (oLine in 0 until lineCount) { depth[oLine] = d; touched[oLine] = true; baseOf[oLine] = base }
            for (child in node.children) walk(child, d + 1, base)
        }
        for (root in tree.roots) walk(root, 0, leadingWhitespace(document, openerLine(root)))

        val stringSpans = PsiTreeUtil.collectElements(antlers) {
            it.node?.elementType == AntlersTypes.T_STRING
        }.map { it.textRange }

        val edits = mutableListOf<Pair<TextRange, String>>()
        for (line in 0 until lineCount) {
            val lineStart = document.getLineStartOffset(line)
            if (lineStart < rangeToReformat.startOffset || lineStart > rangeToReformat.endOffset) continue
            if (continuation[line] || !touched[line]) continue         // multiline-owned, or top-level (leave it)
            if (stringSpans.any { it.startOffset < lineStart && lineStart < it.endOffset }) continue

            val lineEnd = document.getLineEndOffset(line)
            val lineText = document.getText(TextRange(lineStart, lineEnd))
            val firstNonWs = lineText.indexOfFirst { it != ' ' && it != '\t' }
            if (firstNonWs < 0) {                                      // blank line → strip
                if (lineStart != lineEnd) edits.add(TextRange(lineStart, lineEnd) to "")
                continue
            }
            val contentOffset = lineStart + firstNonWs
            val currentIndent = lineText.substring(0, firstNonWs)
            val base = baseOf[line] ?: ""
            val d = depth[line]
            val next = lineText.getOrNull(firstNonWs + 1)
            val isHtmlTag = lineText[firstNonWs] == '<' && (next != null && (next.isLetter() || next == '/'))
            val target = if (isHtmlTag) currentIndent + unit.repeat(d) else base + unit.repeat(d)

            val indentRange = TextRange(lineStart, contentOffset)
            if (document.getText(indentRange) != target) edits.add(indentRange to target)
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
