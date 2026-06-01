package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

enum class AntlersCompletionKind { TAG_NAME, TAG_METHOD, PARAMETER, MODIFIER, NONE }

data class AntlersCompletionInfo(val kind: AntlersCompletionKind, val tagHead: String? = null)

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
                    AntlersCompletionInfo(AntlersCompletionKind.TAG_NAME)
                else AntlersCompletionInfo(AntlersCompletionKind.NONE)

            AntlersTypes.T_COLON ->
                headOf(statement)?.let { AntlersCompletionInfo(AntlersCompletionKind.TAG_METHOD, it) }
                    ?: AntlersCompletionInfo(AntlersCompletionKind.NONE)

            AntlersTypes.T_EQUALS -> AntlersCompletionInfo(AntlersCompletionKind.NONE)

            AntlersTypes.T_IDENT, AntlersTypes.T_STRING, AntlersTypes.T_NUMBER,
            AntlersTypes.T_RBRACE, AntlersTypes.T_RBRACKET, AntlersTypes.T_RPAREN ->
                headOf(statement)?.let { AntlersCompletionInfo(AntlersCompletionKind.PARAMETER, it) }
                    ?: AntlersCompletionInfo(AntlersCompletionKind.NONE)

            else -> AntlersCompletionInfo(AntlersCompletionKind.NONE)
        }
    }

    private fun headOf(statement: AntlersStatement): String? {
        val namePath = PsiTreeUtil.findChildOfType(statement, AntlersNamePathMixin::class.java) ?: return null
        return namePath.head.ifBlank { null }
    }

    /** Nearest preceding leaf within [statement] that is not whitespace (and not in another statement). */
    private fun prevSignificantLeaf(from: PsiElement, statement: AntlersStatement): PsiElement? {
        var e: PsiElement? = PsiTreeUtil.prevLeaf(from)
        while (e != null) {
            if (!PsiTreeUtil.isAncestor(statement, e, false)) return null  // left the statement
            if (e is PsiWhiteSpace) { e = PsiTreeUtil.prevLeaf(e); continue }
            if (e.node.elementType != AntlersTypes.T_WS && e.textLength > 0) return e
            e = PsiTreeUtil.prevLeaf(e)
        }
        return null
    }
}
