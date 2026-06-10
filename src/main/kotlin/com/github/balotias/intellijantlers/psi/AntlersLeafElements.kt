package com.github.balotias.intellijantlers.psi

import com.github.balotias.intellijantlers.references.AntlersDefinitionReferenceHelper
import com.github.balotias.intellijantlers.references.AntlersPartialReferenceHelper
import com.github.balotias.intellijantlers.references.AntlersStaticFileReferenceHelper
import com.github.balotias.intellijantlers.references.AntlersSvgReferenceHelper
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/**
 * Custom leaf PSI element for T_STRING tokens.
 * Overrides getReferences() to support partial path resolution.
 * Implements PsiLanguageInjectionHost to allow language injection into string interpolation spans.
 */
class AntlersStringLeaf(type: IElementType, text: CharSequence) :
    LeafPsiElement(type, text), PsiLanguageInjectionHost {

    override fun getReferences(): Array<PsiReference> {
        return AntlersSvgReferenceHelper.refsForString(this) +
            AntlersPartialReferenceHelper.refsForString(this) +
            AntlersStaticFileReferenceHelper.refsForString(this)
    }

    override fun getReference(): PsiReference? = references.firstOrNull()

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost =
        ElementManipulators.handleContentChange(this, text) as PsiLanguageInjectionHost

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}

/**
 * Custom leaf PSI element for T_IDENT tokens.
 * Overrides getReferences() to support partial path resolution and go-to-definition.
 */
class AntlersIdentLeaf(type: IElementType, text: CharSequence) : LeafPsiElement(type, text) {
    override fun getReferences(): Array<PsiReference> =
        AntlersSvgReferenceHelper.refsForIdent(this) +
            AntlersPartialReferenceHelper.refsForIdent(this) +
            AntlersDefinitionReferenceHelper.refsForIdent(this)

    override fun getReference(): PsiReference? = references.firstOrNull()
}
