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
import com.intellij.psi.util.PsiTreeUtil

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

            // An inline tag call `{tag param=…}` (e.g. `href = {obfuscate_link …}`) leaves the tag name as
            // a bare T_IDENT directly under the statement, not wrapped in a NAME_PATH. Color it as a tag.
            else -> if (isInlineTagHead(element)) paint(holder, element, AntlersSyntaxHighlighter.TAG)
        }
    }

    /**
     * True when [element] is a bare `T_IDENT` opening an inline tag call: preceded by `{` and not followed
     * by `:` (which would make it an array key like `{collection: 'x'}`), whose text is a known tag.
     */
    private fun isInlineTagHead(element: PsiElement): Boolean {
        if (element.node.elementType != AntlersTypes.T_IDENT) return false
        if (PsiTreeUtil.skipWhitespacesBackward(element)?.node?.elementType != AntlersTypes.T_LBRACE) return false
        if (PsiTreeUtil.skipWhitespacesForward(element)?.node?.elementType == AntlersTypes.T_COLON) return false
        return AntlersCatalogService.getInstance(element.project).isTag(element.text)
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
