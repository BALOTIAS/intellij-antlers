package com.github.balotias.intellijantlers.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Colors the `{` and `}` delimiters of Antlers interpolation inside a string literal. The interpolation
 * *contents* are now a real injected Antlers fragment (see AntlersStringInterpolationInjector) and are
 * highlighted natively by the injected language, so this only paints the delimiters — with an explicit
 * foreground (HighlighterColors.TEXT) so they don't bleed the green T_STRING base in schemes where the
 * brace key inherits its foreground (observed in PhpStorm). The surrounding string text keeps STRING.
 */
class AntlersStringInterpolationAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != AntlersTypes.T_STRING) return
        val text = element.text
        val base = element.textRange.startOffset
        for (span in AntlersInterpolationScanner.scan(text)) {
            paint(holder, base + span.openBrace, base + span.openBrace + 1)
            paint(holder, base + span.closeBrace, base + span.closeBrace + 1)
        }
    }

    private fun paint(holder: AnnotationHolder, start: Int, end: Int) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(start, end))
            .textAttributes(HighlighterColors.TEXT)
            .create()
    }
}
