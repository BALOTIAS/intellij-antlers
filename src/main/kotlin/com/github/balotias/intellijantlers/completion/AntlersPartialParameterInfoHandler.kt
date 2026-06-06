package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.references.AntlersPartialParams
import com.github.balotias.intellijantlers.references.AntlersPartialParams.PartialParam
import com.github.balotias.intellijantlers.references.AntlersPartialReferenceHelper
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * Ctrl/⌘P signature popup for a `{{ partial:… }}` include: lists the included partial's declared
 * `@param`s (`label*, as, button_type, faux`) with the one at the caret in bold. Required params are
 * marked with a trailing `*`. Sources the params from the same resolver/parser used by completion and
 * docs, so the three surfaces always agree. No-ops for non-partial tags and unresolved partials.
 */
class AntlersPartialParameterInfoHandler : ParameterInfoHandler<AntlersStatement, List<PartialParam>> {

    /** The enclosing `{{ partial:… }}` statement at [offset], or null if the caret isn't in one. */
    internal fun partialStmtAt(file: PsiFile, offset: Int): AntlersStatement? {
        val el = file.findElementAt(offset) ?: return null
        val stmt = PsiTreeUtil.getParentOfType(el, AntlersStatement::class.java, false) ?: return null
        val head = PsiTreeUtil.getChildOfType(stmt, AntlersNamePathMixin::class.java)?.head
        return if (head == "partial") stmt else null
    }

    /** The included partial's `@param`s, or null when it doesn't resolve or declares none. */
    internal fun paramsFor(stmt: AntlersStatement): List<PartialParam>? {
        val partial = AntlersPartialReferenceHelper.includedPartialFile(stmt) ?: return null
        return AntlersPartialParams.of(partial).ifEmpty { null }
    }

    /**
     * Index of the param whose name the caret is inside, or -1. Partial params are named (any order), so
     * we bold the param being edited rather than a positional slot (unlike the modifier handler).
     */
    internal fun currentIndex(stmt: AntlersStatement, offset: Int): Int {
        val params = paramsFor(stmt) ?: return -1
        val el = stmt.containingFile.findElementAt(offset)
        val name = PsiTreeUtil.getParentOfType(el, AntlersParameterMixin::class.java, false)?.parameterName
            ?: return -1
        return params.indexOfFirst { it.name == name }
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): AntlersStatement? {
        val stmt = partialStmtAt(context.file, context.offset) ?: return null
        val params = paramsFor(stmt) ?: return null
        context.itemsToShow = arrayOf(params)
        return stmt
    }

    override fun showParameterInfo(element: AntlersStatement, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): AntlersStatement? =
        partialStmtAt(context.file, context.offset)

    override fun updateParameterInfo(element: AntlersStatement, context: UpdateParameterInfoContext) {
        context.setCurrentParameter(currentIndex(element, context.offset))
    }

    override fun updateUI(p: List<PartialParam>, context: ParameterInfoUIContext) {
        val (text, highlightStart, highlightEnd) = renderSignature(p, context.currentParameterIndex)
        context.setupUIComponentPresentation(
            text, highlightStart, highlightEnd, false, false, false, context.defaultParameterColor
        )
    }

    companion object {
        /**
         * Pure signature rendering: `"label*, as, button_type, faux"` plus the half-open char range of the
         * param at [currentIndex] to bold (`-1, -1` when none / out of range). Required params get a `*`.
         */
        fun renderSignature(params: List<PartialParam>, currentIndex: Int): Triple<String, Int, Int> {
            if (params.isEmpty()) return Triple("", -1, -1)
            val sb = StringBuilder()
            val ranges = ArrayList<IntRange>()
            params.forEachIndexed { i, param ->
                if (i > 0) sb.append(", ")
                val start = sb.length
                sb.append(param.name)
                if (param.required) sb.append("*")
                ranges.add(start until sb.length)
            }
            val clamped = if (currentIndex < 0) -1 else currentIndex.coerceAtMost(params.size - 1)
            return if (clamped >= 0) Triple(sb.toString(), ranges[clamped].first, ranges[clamped].last + 1)
            else Triple(sb.toString(), -1, -1)
        }
    }
}
