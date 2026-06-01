package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.catalog.scan.ModifierScanner
import com.github.balotias.intellijantlers.catalog.scan.TagScanner
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/**
 * Soft reference from a custom tag/modifier name to its PHP class file.
 * Soft so native/unknown names are not flagged as unresolved; the scan happens lazily in resolve().
 */
class AntlersPhpClassReference(
    element: PsiElement,
    private val name: String,
    private val isModifier: Boolean
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true /* soft */) {

    override fun resolve(): PsiElement? {
        val target = (if (isModifier) ModifierScanner.find(element.project, name)
                      else TagScanner.find(element.project, name)) ?: return null
        val psiFile = PsiManager.getInstance(element.project).findFile(target.file) ?: return null
        return psiFile.findElementAt(target.offset) ?: psiFile
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
