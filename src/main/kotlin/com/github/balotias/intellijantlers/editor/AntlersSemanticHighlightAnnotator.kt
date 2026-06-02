package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement

/**
 * Semantic highlighting the lexer can't do: T_IDENT is every identifier, so this paints the tag head,
 * condition keyword, and modifier name distinctly. Variables/fields/params keep IDENTIFIER.
 */
class AntlersSemanticHighlightAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is AntlersConditionMixin ->
                firstIdent(element)?.let { paint(holder, it, AntlersSyntaxHighlighter.KEYWORD) }

            is AntlersModifierMixin ->
                firstIdent(element)?.let { paint(holder, it, AntlersSyntaxHighlighter.MODIFIER) }

            is AntlersNamePathMixin -> {
                if (AntlersCatalogService.getInstance(element.project).isTag(element.head)) {
                    firstIdent(element)?.let { paint(holder, it, AntlersSyntaxHighlighter.TAG) }
                }
            }
        }
    }

    private fun firstIdent(element: PsiElement): PsiElement? =
        element.node.findChildByType(AntlersTypes.T_IDENT)?.psi

    private fun paint(holder: AnnotationHolder, leaf: PsiElement, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(leaf)
            .textAttributes(key)
            .create()
    }
}
