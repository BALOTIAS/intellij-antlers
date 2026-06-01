package com.github.balotias.intellijantlers.psi

import com.github.balotias.intellijantlers.references.AntlersPartialReferenceHelper
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/**
 * Custom leaf PSI element for T_STRING tokens.
 * Overrides getReferences() to support partial path resolution.
 */
class AntlersStringLeaf(type: IElementType, text: CharSequence) : LeafPsiElement(type, text) {
    override fun getReferences(): Array<PsiReference> {
        return AntlersPartialReferenceHelper.refsForString(this)
    }

    override fun getReference(): PsiReference? = references.firstOrNull()
}

/**
 * Custom leaf PSI element for T_IDENT tokens.
 * Overrides getReferences() to support partial path resolution.
 */
class AntlersIdentLeaf(type: IElementType, text: CharSequence) : LeafPsiElement(type, text) {
    override fun getReferences(): Array<PsiReference> {
        return AntlersPartialReferenceHelper.refsForIdent(this)
    }

    override fun getReference(): PsiReference? = references.firstOrNull()
}
