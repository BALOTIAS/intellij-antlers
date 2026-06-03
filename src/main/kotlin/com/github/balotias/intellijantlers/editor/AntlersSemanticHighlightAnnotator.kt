package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.parser.AntlersParserUtil
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

            is AntlersParameterMixin ->
                firstIdent(element)?.let { paint(holder, it, AntlersSyntaxHighlighter.PARAMETER) }

            is AntlersNamePathMixin -> {
                // Also paints the keyword/tag in a closer's name-path (`{{ /if }}`, `{{ /collection }}`)
                // so a closer matches its opener. A bare `{{ if }}` is a condition, handled above; the
                // only name-path whose head is a condition keyword is a slash-closer.
                val key = when {
                    element.head in AntlersParserUtil.CONDITION_KEYWORDS -> AntlersSyntaxHighlighter.KEYWORD
                    AntlersCatalogService.getInstance(element.project).isTag(element.head) -> AntlersSyntaxHighlighter.TAG
                    else -> null
                }
                key?.let { k -> firstIdent(element)?.let { paint(holder, it, k) } }
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
