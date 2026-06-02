package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafPsiElement

/** Soft reference from a dotted/colon sub-field segment to its blueprint declaration. */
class AntlersBlueprintMemberReference(
    element: PsiElement,
    private val namespace: BlueprintNamespace,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val field = BlueprintService.getInstance(element.project).fieldsFor(namespace)
            .firstOrNull { it.handle == handle } ?: return null
        return AntlersFieldDeclaration(element.project, field)
    }

    override fun isReferenceTo(target: PsiElement): Boolean {
        if (target !is AntlersFieldDeclaration) return false
        val field = BlueprintService.getInstance(element.project).fieldsFor(namespace)
            .firstOrNull { it.handle == handle } ?: return false
        return field.file == target.file && field.offset == target.offset
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val leaf = element as? LeafPsiElement ?: return element
        return leaf.replaceWithText(newElementName).psi
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
