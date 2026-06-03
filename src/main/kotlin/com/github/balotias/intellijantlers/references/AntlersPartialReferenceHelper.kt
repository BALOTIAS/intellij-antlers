package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil

/**
 * Shared helper for computing partial references from leaf PSI elements.
 *
 * Called from custom leaf classes (AntlersStringLeaf, AntlersIdentLeaf) that override
 * getReferences() on the leaf PSI element. This avoids the PsiReferenceContributor
 * mechanism which is unreliable for non-Java-derived languages in BasePlatformTestCase.
 *
 * Grammar reality for `{{ partial:src="blog/card" }}` (and the static `{{ partial src="..." }}`):
 *   - `src="blog/card"` parses as an AntlersParameter (`:src=` bound, `src=` static); the T_STRING
 *     "blog/card" is the parameter's value element.
 *
 * Grammar reality for `{{ partial:blog/card }}`:
 *   - Parsed as namePath(head=partial, method=blog) followed by T_SLASH, T_IDENT(card).
 *   - `card` is a direct child of the statement.
 */
object AntlersPartialReferenceHelper {

    /** Returns references for a T_STRING leaf that might be a partial path. */
    fun refsForString(element: PsiElement): Array<PsiReference> {
        val statement = PsiTreeUtil.getParentOfType(element, AntlersStatement::class.java)
            ?: return emptyArray()
        val namePath = PsiTreeUtil.getChildOfType(statement, AntlersNamePathMixin::class.java)
            ?: return emptyArray()
        if (namePath.head != "partial") return emptyArray()
        // The string must be the value of a `src` parameter (`:src="..."` or `src="..."`).
        val param = PsiTreeUtil.getParentOfType(element, AntlersParameterMixin::class.java)
            ?: return emptyArray()
        if (param.parameterName != "src" || param.valueElement != element) return emptyArray()

        val raw = element.text
        val inner = raw.removeSurrounding("\"").removeSurrounding("'")
        val start = if (raw.length >= 2) 1 else 0
        return arrayOf(
            AntlersPartialReference(element, TextRange(start, start + inner.length), inner)
        )
    }

    /** Returns references for a T_IDENT leaf that might be part of a partial path. */
    fun refsForIdent(element: PsiElement): Array<PsiReference> {
        val statement = PsiTreeUtil.getParentOfType(element, AntlersStatement::class.java)
            ?: return emptyArray()
        val namePath = PsiTreeUtil.getChildOfType(statement, AntlersNamePathMixin::class.java)
            ?: return emptyArray()
        if (namePath.head != "partial") return emptyArray()

        // Skip the "partial" head ident itself
        if (isHeadIdent(element, namePath)) return emptyArray()
        // Skip the "src" method ident (handled by refsForString)
        if (element.text == "src" && isMethodIdent(element, namePath)) return emptyArray()

        // The element is either:
        //   (a) the method ident inside namePath (e.g. `blog` in partial:blog)
        //   (b) a loose T_IDENT after T_SLASH (e.g. `card` in partial:blog/card)
        val inNamePath = PsiTreeUtil.isAncestor(namePath, element, false)
        val isPathMethodIdent = inNamePath && isMethodIdent(element, namePath)
        val isLoosePathIdent = !inNamePath && isPrecededBySlash(element)

        if (!isPathMethodIdent && !isLoosePathIdent) return emptyArray()

        val fullPath = extractPartialPath(statement) ?: return emptyArray()
        return arrayOf(
            AntlersPartialReference(element, TextRange(0, element.textLength), fullPath, isTailPathIdent(element, namePath))
        )
    }

    /** True when [element] is the LAST identifier of a colon-form partial path (the renamable segment). */
    private fun isTailPathIdent(element: PsiElement, namePath: AntlersNamePathMixin): Boolean {
        // The path's last ident is the last loose T_IDENT after the namePath, or (if none) the method ident.
        var last: PsiElement? = namePath.node.getChildren(null)
            .filter { it.elementType == AntlersTypes.T_IDENT }.getOrNull(1)?.psi
        var sib: PsiElement? = namePath.nextSibling
        while (sib != null) {
            val t = sib.node?.elementType
            when {
                t == AntlersTypes.T_WS || t == com.intellij.psi.TokenType.WHITE_SPACE -> {}
                t == AntlersTypes.T_SLASH -> {}
                t == AntlersTypes.T_IDENT -> last = sib
                else -> return element == last
            }
            sib = sib.nextSibling
        }
        return element == last
    }

    private fun isHeadIdent(element: PsiElement, namePath: AntlersNamePathMixin): Boolean {
        val firstIdent = namePath.node.findChildByType(AntlersTypes.T_IDENT)?.psi ?: return false
        return firstIdent == element
    }

    private fun isMethodIdent(element: PsiElement, namePath: AntlersNamePathMixin): Boolean {
        val idents = namePath.node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }
        return idents.size >= 2 && idents[1].psi == element
    }

    private fun isPrecededBySlash(element: PsiElement): Boolean {
        var prev: PsiElement? = element.prevSibling
        while (prev != null) {
            val t = prev.node?.elementType
            if (t == AntlersTypes.T_WS || t == com.intellij.psi.TokenType.WHITE_SPACE) {
                prev = prev.prevSibling; continue
            }
            return t == AntlersTypes.T_SLASH
        }
        return false
    }

    private fun extractPartialPath(statement: AntlersStatement): String? {
        val namePath = PsiTreeUtil.getChildOfType(statement, AntlersNamePathMixin::class.java)
            ?: return null
        // Only the `:path` form reaches here (the `src="..."` form is handled by refsForString,
        // where the string lives inside the `src` parameter).
        val method = namePath.method ?: return null

        // :path form: method is first segment, followed by /seg pairs
        val sb = StringBuilder(method)
        var sibling: PsiElement? = namePath.nextSibling
        while (sibling != null) {
            val t = sibling.node?.elementType
            when {
                t == AntlersTypes.T_WS || t == com.intellij.psi.TokenType.WHITE_SPACE -> { /* skip */ }
                t == AntlersTypes.T_SLASH -> sb.append('/')
                t == AntlersTypes.T_IDENT -> sb.append(sibling.text)
                else -> break
            }
            sibling = sibling.nextSibling
        }

        return sb.toString()
    }
}
