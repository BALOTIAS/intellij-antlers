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
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil

/**
 * Single absolute-indent pass. For each physical line it sums four region contributors into one depth and
 * rewrites the leading whitespace to `unit * depth` — so indentation reflects the true combined nesting of
 * HTML elements, Antlers pairs/conditions, template-named `<{{ }}>` tags, and multi-line `{{ }}` params.
 * Whitespace-significant / opaque regions (`<pre>`/`<textarea>`, multi-line strings, comment/noparse/PHP
 * interiors) are preserved. Indentation-only, idempotent, tolerant; never throws. In-`{{ }}` spacing is
 * owned by [AntlersSpacingPostFormatProcessor].
 */
class AntlersIndentProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (!AntlersFormatterSettings.getInstance(source.project).reformatEnabled) return rangeToReformat
        val document = source.viewProvider.document ?: return rangeToReformat
        // The spacing pass may have edited the document without committing PSI; re-sync so PSI offsets match.
        PsiDocumentManager.getInstance(source.project).commitDocument(document)
        val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat
        val html = source.viewProvider.getPsi(HTMLLanguage.INSTANCE)

        val lineCount = document.lineCount
        if (lineCount == 0) return rangeToReformat
        val opts = CodeStyle.getIndentOptions(source)
        val unit = if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)

        val depth = IntArray(lineCount)
        val preserve = BooleanArray(lineCount)

        // The combined nesting depth is the sum of four independent contributors.
        val htmlNesting = AntlersHtmlNesting.compute(document, html)
        for (l in 0 until lineCount) depth[l] += htmlNesting.depth.getOrElse(l) { 0 }
        for (l in htmlNesting.preserve) if (l in 0 until lineCount) preserve[l] = true
        addAntlersPairDepth(antlers, document, source.project, depth)
        addTemplateTagDepth(document.text, depth)
        addMultilineParamDepth(antlers, document, depth)

        // Interiors of multi-line strings and comment/noparse/PHP blocks are opaque (never reindented).
        val opaqueRanges = PsiTreeUtil.collectElements(antlers) { it.node?.elementType in OPAQUE_TYPES }
            .map { it.textRange }

        val delta = applyIndents(document, rangeToReformat, depth, preserve, opaqueRanges, unit)
        return TextRange(rangeToReformat.startOffset, rangeToReformat.endOffset + delta)
    }

    /**
     * (Contributor 2) Antlers pairs & conditions. A node's body — `opener-END-line+1 .. closer-START-line-1`
     * — gets `+1`; using the opener's END line leaves a multi-line opener's own param lines to contributor
     * 4 (no double counting). `else`/`elseif` branch markers render at the `{{ if }}` level (no `+1`).
     */
    private fun addAntlersPairDepth(antlers: AntlersFile, document: Document, project: Project, depth: IntArray) {
        val lineCount = depth.size
        val elseLines = PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)
            .filter {
                val kw = (it.condition as? AntlersConditionMixin)?.keyword
                kw == "else" || kw == "elseif"
            }
            .map { document.getLineNumber(it.textRange.startOffset) }
            .toSet()
        fun walk(node: NestingNode) {
            val oStart = document.getLineNumber(node.opener.textRange.startOffset)
            val oEnd = document.getLineNumber(node.opener.textRange.endOffset - 1)
            // Unclosed multi-line opener: a self-contained tag, not a block — don't indent a "body".
            if (node.closer == null && oEnd > oStart) { node.children.forEach(::walk); return }
            val cStart = node.closer?.let { document.getLineNumber(it.textRange.startOffset) }
            val bodyEnd = if (cStart != null) cStart - 1 else lineCount - 1
            for (l in (oEnd + 1)..bodyEnd) if (l in 0 until lineCount && l !in elseLines) depth[l] += 1
            node.children.forEach(::walk)
        }
        AntlersNestingTreeBuilder.build(antlers, project).roots.forEach(::walk)
    }

    /** (Contributor 3) Template-named tags `<{{ … }}> … </{{ … }}>`: interior `+1`, excluding the `>` line. */
    private fun addTemplateTagDepth(text: String, depth: IntArray) {
        val lineCount = depth.size
        for (e in AntlersTemplateTags.elements(text)) {
            val end = e.closeLine ?: lineCount
            for (l in (e.openStartLine + 1) until end) {
                if (l != e.openEndLine && l in 0 until lineCount) depth[l] += 1
            }
        }
    }

    /** (Contributor 4) Multi-line `{{ }}` params: each interior line `+1` + bracket nesting; `}}` line `+0`. */
    private fun addMultilineParamDepth(antlers: AntlersFile, document: Document, depth: IntArray) {
        val lineCount = depth.size
        for (stmt in PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)) {
            val sLine = document.getLineNumber(stmt.textRange.startOffset)
            val eLine = document.getLineNumber(stmt.textRange.endOffset - 1)
            if (eLine == sLine) continue
            val lastLeaf = PsiTreeUtil.getDeepestLast(stmt)
            val rdoubleStart =
                if (lastLeaf.node.elementType == AntlersTypes.T_RDOUBLE) lastLeaf.textRange.startOffset else -1
            val brackets = PsiTreeUtil.collectElements(stmt) { BRACKET_DELTAS.containsKey(it.node?.elementType) }
                .map { it.textRange.startOffset to BRACKET_DELTAS.getValue(it.node!!.elementType) }
            for (line in (sLine + 1)..eLine) {
                if (line !in 0 until lineCount) continue
                val contentOffset = firstNonWsOffset(document, line)
                if (contentOffset < 0) continue
                if (line == eLine && contentOffset == rdoubleStart) continue   // `}}` on its own line → +0
                val openBefore = brackets.filter { it.first < contentOffset }.sumOf { it.second }
                val closesFirst = if (document.charsSequence[contentOffset] in "])}") 1 else 0
                depth[line] += maxOf(1, 1 + openBefore - closesFirst)
            }
        }
    }

    /** Rewrites each in-range, non-preserved, non-blank line's leading whitespace to `unit * depth`. */
    private fun applyIndents(
        document: Document,
        range: TextRange,
        depth: IntArray,
        preserve: BooleanArray,
        opaqueRanges: List<TextRange>,
        unit: String,
    ): Int {
        val edits = mutableListOf<Pair<TextRange, String>>()
        for (line in 0 until depth.size) {
            val lineStart = document.getLineStartOffset(line)
            if (lineStart < range.startOffset || lineStart > range.endOffset) continue
            if (preserve[line]) continue
            if (opaqueRanges.any { it.startOffset < lineStart && lineStart < it.endOffset }) continue
            val contentOffset = firstNonWsOffset(document, line)
            if (contentOffset < 0) {                                   // blank line → strip
                val lineEnd = document.getLineEndOffset(line)
                if (lineStart != lineEnd) edits.add(TextRange(lineStart, lineEnd) to "")
                continue
            }
            val target = unit.repeat(depth[line].coerceAtLeast(0))
            val indentRange = TextRange(lineStart, contentOffset)
            if (document.getText(indentRange) != target) edits.add(indentRange to target)
        }

        var delta = 0
        for ((indentRange, replacement) in edits.distinct().sortedByDescending { it.first.startOffset }) {
            document.replaceString(indentRange.startOffset, indentRange.endOffset, replacement)
            delta += replacement.length - indentRange.length
        }
        return delta
    }

    /** Offset of the line's first non-whitespace char, or -1 when the line is blank. */
    private fun firstNonWsOffset(document: Document, line: Int): Int {
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        val cs = document.charsSequence
        var i = start
        while (i < end && (cs[i] == ' ' || cs[i] == '\t')) i++
        return if (i >= end) -1 else i
    }

    companion object {
        private val BRACKET_DELTAS: Map<IElementType, Int> = mapOf(
            AntlersTypes.T_LBRACE to 1, AntlersTypes.T_LBRACKET to 1, AntlersTypes.T_LPAREN to 1,
            AntlersTypes.T_RBRACE to -1, AntlersTypes.T_RBRACKET to -1, AntlersTypes.T_RPAREN to -1,
        )
        private val OPAQUE_TYPES = setOf(
            AntlersTypes.T_STRING, AntlersTypes.T_COMMENT_TEXT,
            AntlersTypes.T_NOPARSE_TEXT, AntlersTypes.T_PHP_TEXT,
        )
    }
}
