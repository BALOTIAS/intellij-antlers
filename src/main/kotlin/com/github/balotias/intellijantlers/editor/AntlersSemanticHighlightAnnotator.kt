package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.completion.AntlersConditionOperators
import com.github.balotias.intellijantlers.completion.QUERY_OPERATORS
import com.github.balotias.intellijantlers.psi.AntlersInlineTags
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
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

            is AntlersParameterMixin -> {
                firstIdent(element)?.let { ident ->
                    val key = when {
                        // The `void` placeholder (`{{ x ? 'a' : void }}`) parses as a bound param `:void`.
                        ident.text in valueKeywords -> AntlersSyntaxHighlighter.KEYWORD
                        // A `field:operator="value"` query condition parses as a bound parameter named
                        // after the operator — paint it as an operator, not a parameter name.
                        AntlersConditionOperators.isConditionOperatorIdent(ident) -> AntlersSyntaxHighlighter.OPERATOR
                        else -> AntlersSyntaxHighlighter.PARAMETER
                    }
                    paint(holder, ident, key)
                }
            }

            is AntlersNamePathMixin -> {
                // Also paints the keyword/tag in a closer's name-path (`{{ /if }}`, `{{ /collection }}`)
                // so a closer matches its opener. A bare `{{ if }}` is a condition, handled above; the
                // only name-path whose head is a condition keyword is a slash-closer.
                val key = when {
                    element.head in AntlersParserUtil.CONDITION_KEYWORDS -> AntlersSyntaxHighlighter.KEYWORD
                    // `switch(…)` is the inline match-like *operator* (a language keyword), not the
                    // `{{ switch between=… }}` cycling tag — tell them apart by the trailing `(`.
                    element.head == "switch" && isFollowedByLParen(element) -> AntlersSyntaxHighlighter.KEYWORD
                    AntlersCatalogService.getInstance(element.project).isTag(element.head) -> AntlersSyntaxHighlighter.TAG
                    else -> null
                }
                key?.let { k -> firstIdent(element)?.let { paint(holder, it, k) } }
            }

            else -> when {
                // The `:` of a ternary/elvis operator (`a ? b : c`, `x ?: y`) is a loose T_COLON in the
                // expression — paint it like the `?` (operator), not as subtle path punctuation.
                isTernaryColon(element) -> paint(holder, element, AntlersSyntaxHighlighter.OPERATOR)

                // Logical word operators (`and`/`or`/`xor`/`not`) are Statamic LanguageKeywords lexed as
                // plain T_IDENT; paint them as keywords (like `if`/`else`) so the word stands out rather
                // than blending into default-foreground text. Only when standing as their own token in an
                // expression or condition body — not as a `.`/`:` path segment or a modifier argument.
                isWordOperator(element) -> paint(holder, element, AntlersSyntaxHighlighter.KEYWORD)

                // The `void` value placeholder (`{{ x ? 'a' : void }}`) — a language keyword, not a variable.
                isValueKeyword(element) -> paint(holder, element, AntlersSyntaxHighlighter.KEYWORD)

                // The inline switch *operator* `{ switch(…) }` — e.g. the single-brace form Antlers string
                // interpolation rewrites `{{ switch(…) }}` into. Here `switch` is a bare T_IDENT (the leading
                // `{` blocks a name-path), so it never hits the NAME_PATH branch above. Catch it before the
                // inline-tag-head rule below, which would otherwise mistake it for a `{tag …}` call.
                isSwitchOperatorHead(element) -> paint(holder, element, AntlersSyntaxHighlighter.KEYWORD)

                // An inline tag call `{tag param=…}` (e.g. `href = {obfuscate_link …}`) leaves the tag name
                // as a bare T_IDENT directly under the statement, not wrapped in a NAME_PATH. Color it as a tag.
                AntlersInlineTags.isInlineTagHead(element) -> paint(holder, element, AntlersSyntaxHighlighter.TAG)
            }
        }
    }

    /**
     * Infix word operators lexed as plain T_IDENT: the four logical operators (Statamic
     * `LanguageKeywords`) plus the query/builder operators (`where`/`take`/…). All reserved in operator
     * position, so paint them as keywords.
     */
    private val wordOperators = setOf("and", "or", "xor", "not") + QUERY_OPERATORS

    private fun isWordOperator(element: PsiElement): Boolean {
        if (element.node?.elementType != AntlersTypes.T_IDENT) return false
        if (element.text !in wordOperators) return false
        // Paint only when the operator stands directly in an expression or condition body — a path
        // segment (`foo.or`), parameter, or modifier argument lands under a different parent.
        val parent = element.parent
        return parent is AntlersStatement || parent is AntlersConditionMixin
    }

    /** Bare-ident value keywords (not operators) — currently the `void` placeholder. */
    private val valueKeywords = setOf("void")

    private fun isValueKeyword(element: PsiElement): Boolean {
        if (element.node?.elementType != AntlersTypes.T_IDENT) return false
        if (element.text !in valueKeywords) return false
        val parent = element.parent
        return parent is AntlersStatement || parent is AntlersConditionMixin
    }

    /**
     * The `:` of a ternary/elvis operator — a loose `T_COLON` standing in an expression / condition body
     * (so NOT a path `collection:blog`, modifier `upper:2`, or bound-parameter `:src` colon, which sit
     * under their own node), with a `?` operator earlier in the same expression. (A ternary whose value
     * after `:` is a bare identifier — `a ? b : c` or `… : void` — parses that colon into a parameter
     * node and is left subtle; the common string/expression form is covered.)
     */
    private fun isTernaryColon(element: PsiElement): Boolean {
        if (element.node?.elementType != AntlersTypes.T_COLON) return false
        val parent = element.parent
        if (parent !is AntlersStatement && parent !is AntlersConditionMixin) return false
        var sib = element.prevSibling
        while (sib != null) {
            if (sib.node?.elementType == AntlersTypes.T_OP && sib.text.contains('?')) return true
            sib = sib.prevSibling
        }
        return false
    }

    private fun isSwitchOperatorHead(element: PsiElement): Boolean =
        element.node?.elementType == AntlersTypes.T_IDENT &&
            element.text == "switch" &&
            isFollowedByLParen(element)

    private fun isFollowedByLParen(element: PsiElement): Boolean {
        var sib = element.nextSibling
        while (sib != null && sib.text.isBlank()) sib = sib.nextSibling
        return sib?.node?.elementType == AntlersTypes.T_LPAREN
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
