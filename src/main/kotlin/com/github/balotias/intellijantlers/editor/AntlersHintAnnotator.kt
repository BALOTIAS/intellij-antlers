package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersHintParser
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Colors Antlers Toolbox "Template IDE Hint" directives (`@name`, `@collection`, …) inside `{{# … #}}`
 * comments, like a Javadoc/KDoc tag. Overlay on the comment-text token (same mechanism as 4b).
 */
class AntlersHintAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != AntlersTypes.T_COMMENT_TEXT) return
        val base = element.textRange.startOffset
        for (d in AntlersHintParser.parse(element.text)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(base + d.nameStart, base + d.nameEnd))
                .textAttributes(AntlersSyntaxHighlighter.HINT)
                .create()
        }
    }
}
