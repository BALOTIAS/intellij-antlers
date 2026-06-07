package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.parser.AntlersFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Overlays HTML attribute colors onto the attribute names/values of template-named `<{{ … }}>` tags, which
 * the layered lexer leaves as plain text (the chunk after `}}` is re-lexed without its in-tag context).
 * Runs once on the file root; tolerant. The `{{ }}` interpolations are left to the base highlighter.
 */
class AntlersTemplateTagAttributeAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is AntlersFile) return            // run once, on the Antlers file root
        if (element.project.isDefault) return
        for (part in AntlersTemplateTagAttributes.parts(element.text)) {
            val key = when (part.kind) {
                AntlersTemplateTagAttributes.Kind.NAME -> XmlHighlighterColors.HTML_ATTRIBUTE_NAME
                AntlersTemplateTagAttributes.Kind.VALUE -> XmlHighlighterColors.HTML_ATTRIBUTE_VALUE
            }
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(part.start, part.end))
                .textAttributes(key)
                .create()
        }
    }
}
