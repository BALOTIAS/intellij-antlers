package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.template.AntlersFileViewProvider
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * Suppresses false-positive *structural* HTML errors that arise when an Antlers interpolation acts as
 * (or sits inside) an HTML tag name — e.g. `<{{ as or 'h2' }}> … </{{ as or 'h2' }}>`.
 *
 * The template-data tree only sees opaque placeholders where the `{{ }}` are, so the HTML parser can't
 * pair `<placeholder>`/`</placeholder>` and reports "Closing tag matches nothing" (and the matching
 * "element is not closed"). These are spurious for Antlers templates.
 *
 * The suppression is narrow: it only drops the specific structural messages, only in Antlers view
 * providers, and only when the flagged range actually overlaps an Antlers `{{ }}` statement. A genuine
 * mismatch with no interpolation in range (`<div>…</span>`) is left untouched.
 */
class AntlersHtmlErrorFilter : HighlightInfoFilter {

    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        file ?: return true
        val viewProvider = file.viewProvider
        if (viewProvider !is AntlersFileViewProvider) return true

        val description = highlightInfo.description ?: return true
        if (SUPPRESSED_FRAGMENTS.none { description.contains(it, ignoreCase = true) }) return true

        val antlers = viewProvider.getPsi(AntlersLanguage.INSTANCE) ?: return true
        val range = TextRange(highlightInfo.startOffset, highlightInfo.endOffset)
        return !overlapsInterpolation(antlers, range)
    }

    private fun overlapsInterpolation(antlers: PsiFile, range: TextRange): Boolean =
        PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)
            .any { it.textRange.intersects(range) }

    companion object {
        /** Substrings of the HTML structural errors produced by an interpolated tag name. */
        private val SUPPRESSED_FRAGMENTS = listOf(
            "Closing tag matches nothing",
            "Closing tag name is missing",   // multi-line `</{{ … }}>` — HTML sees `</` then `{{`
            "is not closed",
        )
    }
}
