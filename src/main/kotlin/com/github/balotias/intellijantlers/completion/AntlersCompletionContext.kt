package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.psi.AntlersClosingTag
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

enum class AntlersCompletionKind { TAG_NAME, TAG_METHOD, TAG_SHORTHAND, PARAMETER, PARAMETER_VALUE, MODIFIER, FIELD_PATH, CONDITION_OPERATOR, NONE }

data class AntlersCompletionInfo(
    val kind: AntlersCompletionKind,
    val tagHead: String? = null,
    val pathPrefix: List<String> = emptyList(),
    val paramName: String? = null,
    val isClosing: Boolean = false
)

object AntlersCompletionContext {

    fun classify(position: PsiElement): AntlersCompletionInfo {
        val statement = PsiTreeUtil.getParentOfType(position, AntlersStatement::class.java)
            ?: return AntlersCompletionInfo(AntlersCompletionKind.NONE)

        val prev = prevSignificantLeaf(position, statement)
            ?: return AntlersCompletionInfo(AntlersCompletionKind.NONE)

        return when (prev.node.elementType) {
            AntlersTypes.T_PIPE -> AntlersCompletionInfo(AntlersCompletionKind.MODIFIER)

            AntlersTypes.T_LDOUBLE -> AntlersCompletionInfo(AntlersCompletionKind.TAG_NAME)

            AntlersTypes.T_SLASH ->
                if (prevSignificantLeaf(prev, statement)?.node?.elementType == AntlersTypes.T_LDOUBLE)
                    AntlersCompletionInfo(AntlersCompletionKind.TAG_NAME, isClosing = true)
                else AntlersCompletionInfo(AntlersCompletionKind.NONE)

            AntlersTypes.T_DOT ->
                AntlersCompletionInfo(AntlersCompletionKind.FIELD_PATH, pathPrefix = segmentsBeforeCaret(statement, position))

            AntlersTypes.T_COLON -> {
                val beforeColon = prevSignificantLeaf(prev, statement)
                when {
                    beforeColon?.node?.elementType == AntlersTypes.T_LDOUBLE ->
                        AntlersCompletionInfo(AntlersCompletionKind.TAG_SHORTHAND)
                    // `field:operator=` query condition: the colon's left side is a floating field ident
                    // (a direct statement child, not part of the head namePath) on a condition-capable tag.
                    beforeColon?.node?.elementType == AntlersTypes.T_IDENT &&
                        beforeColon.parent is AntlersStatement &&
                        headOf(statement) in AntlersConditionOperators.CONDITION_TAGS ->
                        AntlersCompletionInfo(AntlersCompletionKind.CONDITION_OPERATOR, headOf(statement))
                    else -> headOf(statement)?.let {
                        AntlersCompletionInfo(AntlersCompletionKind.TAG_METHOD, it, segmentsBeforeCaret(statement, position))
                    } ?: AntlersCompletionInfo(AntlersCompletionKind.NONE)
                }
            }

            AntlersTypes.T_EQUALS -> {
                val nameLeaf = prevSignificantLeaf(prev, statement)
                if (nameLeaf?.node?.elementType != AntlersTypes.T_IDENT) {
                    AntlersCompletionInfo(AntlersCompletionKind.NONE)
                } else if (isBoundParamName(nameLeaf, statement)) {
                    // Bound param `:name=` / `:$name=` takes a variable expression, not a literal.
                    AntlersCompletionInfo(AntlersCompletionKind.NONE)
                } else {
                    headOf(statement)?.let {
                        AntlersCompletionInfo(AntlersCompletionKind.PARAMETER_VALUE, it, paramName = nameLeaf.text)
                    } ?: AntlersCompletionInfo(AntlersCompletionKind.NONE)
                }
            }

            AntlersTypes.T_IDENT, AntlersTypes.T_STRING, AntlersTypes.T_NUMBER,
            AntlersTypes.T_RBRACE, AntlersTypes.T_RBRACKET, AntlersTypes.T_RPAREN ->
                if (PsiTreeUtil.findChildOfType(statement, AntlersClosingTag::class.java) != null)
                    AntlersCompletionInfo(AntlersCompletionKind.NONE)
                else headOf(statement)?.let { AntlersCompletionInfo(AntlersCompletionKind.PARAMETER, it) }
                    ?: AntlersCompletionInfo(AntlersCompletionKind.NONE)

            else -> AntlersCompletionInfo(AntlersCompletionKind.NONE)
        }
    }

    /** True when [nameLeaf] is a BOUND parameter name: `:name` or `:$name` (grammar `T_COLON T_DOLLAR? T_IDENT`). */
    private fun isBoundParamName(nameLeaf: PsiElement, statement: AntlersStatement): Boolean {
        val before = prevSignificantLeaf(nameLeaf, statement) ?: return false
        return when (before.node.elementType) {
            AntlersTypes.T_COLON -> true
            AntlersTypes.T_DOLLAR -> prevSignificantLeaf(before, statement)?.node?.elementType == AntlersTypes.T_COLON
            else -> false
        }
    }

    private fun headOf(statement: AntlersStatement): String? {
        val namePath = PsiTreeUtil.findChildOfType(statement, AntlersNamePathMixin::class.java) ?: return null
        return namePath.head.ifBlank { null }
    }

    private fun segmentsBeforeCaret(statement: AntlersStatement, position: PsiElement): List<String> {
        val namePath = PsiTreeUtil.findChildOfType(statement, AntlersNamePathMixin::class.java) ?: return emptyList()
        return namePath.segmentsBefore(position.textRange.startOffset)
    }

    /** Nearest preceding leaf within [statement] that is not whitespace (and not in another statement). */
    private fun prevSignificantLeaf(from: PsiElement, statement: AntlersStatement): PsiElement? {
        var e: PsiElement? = PsiTreeUtil.prevLeaf(from)
        while (e != null) {
            if (!PsiTreeUtil.isAncestor(statement, e, false)) return null
            val isWhitespace = e is com.intellij.psi.PsiWhiteSpace || e.node.elementType == AntlersTypes.T_WS
            if (!isWhitespace) return e
            e = PsiTreeUtil.prevLeaf(e)
        }
        return null
    }
}
