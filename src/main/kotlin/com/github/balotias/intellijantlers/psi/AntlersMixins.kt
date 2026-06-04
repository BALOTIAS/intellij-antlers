package com.github.balotias.intellijantlers.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil

open class AntlersClosingTagMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** The closed tag's name-path text, e.g. "collection:blog" (null if absent). */
    val closedName: String?
        get() = PsiTreeUtil.findChildOfType(this, AntlersNamePathMixin::class.java)?.pathText
}

open class AntlersConditionMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** if | elseif | else | unless | endif | endunless */
    val keyword: String
        get() = node.findChildByType(AntlersTypes.T_IDENT)?.text ?: ""
}

open class AntlersNamePathMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** First identifier segment, e.g. "collection" in "collection:blog". */
    val head: String
        get() = node.findChildByType(AntlersTypes.T_IDENT)?.text ?: ""

    /** Second path segment (after the first ':' or '.'), e.g. "blog" in "collection:blog" or "name" in "user.name"; null if none. */
    val method: String?
        get() {
            val idents = node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }
            return if (idents.size >= 2) idents[1].text else null
        }

    /** Whole path text, e.g. "collection:blog". */
    val pathText: String get() = text

    /** All identifier segments in order, e.g. ["a","b","c"] for `a.b.c`. */
    val segments: List<String>
        get() = node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }.map { it.text }

    /** Identifier segment texts whose token starts before [offset] (the segments preceding the caret). */
    fun segmentsBefore(offset: Int): List<String> =
        node.getChildren(null)
            .filter { it.elementType == AntlersTypes.T_IDENT && it.startOffset < offset }
            .map { it.text }
}

open class AntlersParameterMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** Parameter name without the leading ':' or '$'. */
    val parameterName: String
        get() = node.findChildByType(AntlersTypes.T_IDENT)?.text ?: ""

    /** True for bound parameters written as ':name', ':$name', or '$name'. */
    val isBound: Boolean
        get() = node.findChildByType(AntlersTypes.T_COLON) != null
            || node.firstChildNode?.elementType == AntlersTypes.T_DOLLAR

    /** The value PSI (string/number/namePath/braced expr), or null for a bare flag. */
    val valueElement: PsiElement?
        get() {
            val eq = node.findChildByType(AntlersTypes.T_EQUALS)?.psi ?: return null
            var e: PsiElement? = eq.nextSibling
            while (e != null && e.node.elementType == AntlersTypes.T_WS) e = e.nextSibling
            return e
        }
}

open class AntlersModifierMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** Modifier name after the pipe, e.g. "upper". */
    val modifierName: String
        get() = node.findChildByType(AntlersTypes.T_IDENT)?.text ?: ""
}

/**
 * Front-matter body (`T_FRONTMATTER_TEXT+`) as a YAML injection host. The body text is the verbatim YAML
 * between the `---` fences, so the escaper is trivial and the injected range is the whole element.
 */
open class AntlersFrontMatterBodyMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {
    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost =
        ElementManipulators.handleContentChange(this, text) as PsiLanguageInjectionHost

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}
