package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/** Soft reference from a `{{ }}` variable to its blueprint field declaration, scope/page aware. */
class AntlersBlueprintFieldReference(
    element: PsiElement,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {
    override fun resolve(): PsiElement? {
        val field = AntlersFieldContext.resolveField(element, handle, element.project) ?: return null
        val psiFile = PsiManager.getInstance(element.project).findFile(field.file) ?: return null
        return psiFile.findElementAt(field.offset) ?: psiFile
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
