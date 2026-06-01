package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil

/** Soft go-to-def references from a custom tag-head / modifier-name identifier to its PHP class. */
object AntlersDefinitionReferenceHelper {
    fun refsForIdent(element: PsiElement): Array<PsiReference> {
        val name = element.text

        // Modifier name: element must be the first T_IDENT child of an AntlersModifierMixin
        PsiTreeUtil.getParentOfType(element, AntlersModifierMixin::class.java)?.let { mod ->
            if (mod.modifierName == name &&
                mod.node.findChildByType(AntlersTypes.T_IDENT)?.psi == element) {
                return arrayOf(AntlersPhpClassReference(element, name, isModifier = true))
            }
        }

        // Tag head: element must be the first T_IDENT child of an AntlersNamePathMixin
        val path = PsiTreeUtil.getParentOfType(element, AntlersNamePathMixin::class.java)
            ?: return emptyArray()
        if (path.head == name &&
            path.node.findChildByType(AntlersTypes.T_IDENT)?.psi == element) {
            return arrayOf(AntlersPhpClassReference(element, name, isModifier = false))
        }

        return emptyArray()
    }
}
