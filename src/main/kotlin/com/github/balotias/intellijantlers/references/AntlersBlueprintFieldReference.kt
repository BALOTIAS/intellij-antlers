package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafPsiElement

/** Soft reference from a `{{ }}` variable to its blueprint field declaration, scope/page aware. */
class AntlersBlueprintFieldReference(
    element: PsiElement,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val field = AntlersFieldContext.resolveField(element, handle, element.project) ?: return null
        return AntlersFieldDeclaration(element.project, field)
    }

    override fun isReferenceTo(target: PsiElement): Boolean {
        if (target !is AntlersFieldDeclaration) return false
        val field = AntlersFieldContext.resolveField(element, handle, element.project) ?: return false
        return field.handle == target.handle && field.namespace == target.namespace
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val leaf = element as? LeafPsiElement ?: return element
        return leaf.replaceWithText(newElementName).psi
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
