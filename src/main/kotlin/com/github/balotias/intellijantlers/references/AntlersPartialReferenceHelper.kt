package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
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
 * Grammar reality for `{{ partial:src="blog/card" }}`:
 *   - Parsed as namePath(head=partial, method=src) followed by T_EQUALS and T_STRING.
 *   - The T_STRING "blog/card" is a direct child of the statement.
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
        // partial:src="..." → head=partial, method=src
        if (namePath.head != "partial" || namePath.method != "src") return emptyArray()
        // Must be preceded by T_EQUALS
        if (!isPrecededByEquals(element)) return emptyArray()

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
            AntlersPartialReference(element, TextRange(0, element.textLength), fullPath)
        )
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

    private fun isPrecededByEquals(element: PsiElement): Boolean {
        var prev: PsiElement? = element.prevSibling
        while (prev != null) {
            val t = prev.node?.elementType
            if (t == AntlersTypes.T_WS || t == com.intellij.psi.TokenType.WHITE_SPACE) {
                prev = prev.prevSibling; continue
            }
            return t == AntlersTypes.T_EQUALS
        }
        return false
    }

    private fun extractPartialPath(statement: AntlersStatement): String? {
        val namePath = PsiTreeUtil.getChildOfType(statement, AntlersNamePathMixin::class.java)
            ?: return null
        val method = namePath.method ?: return null

        if (method == "src") {
            // src="..." form: find T_STRING after T_EQUALS
            var sibling: PsiElement? = namePath.nextSibling
            var seenEquals = false
            while (sibling != null) {
                val t = sibling.node?.elementType
                when {
                    t == AntlersTypes.T_WS || t == com.intellij.psi.TokenType.WHITE_SPACE -> { /* skip */ }
                    t == AntlersTypes.T_EQUALS -> seenEquals = true
                    t == AntlersTypes.T_STRING && seenEquals -> {
                        val raw = sibling.text
                        return raw.removeSurrounding("\"").removeSurrounding("'")
                    }
                    else -> break
                }
                sibling = sibling.nextSibling
            }
            return null
        }

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
