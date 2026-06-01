package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/** Soft reference from a dotted/colon sub-field segment to its blueprint declaration. */
class AntlersBlueprintMemberReference(
    element: PsiElement,
    private val namespace: BlueprintNamespace,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {
    override fun resolve(): PsiElement? {
        val field = BlueprintService.getInstance(element.project).fieldsFor(namespace)
            .firstOrNull { it.handle == handle } ?: return null
        val psiFile = PsiManager.getInstance(element.project).findFile(field.file) ?: return null
        return psiFile.findElementAt(field.offset) ?: psiFile
    }
    override fun getVariants(): Array<Any> = emptyArray()
}
