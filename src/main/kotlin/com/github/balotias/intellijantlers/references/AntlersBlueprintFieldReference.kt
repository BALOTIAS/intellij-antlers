package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.scope.AntlersScopeResolver
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/** Soft reference from a `{{ }}` variable to its blueprint field declaration, scope-aware. */
class AntlersBlueprintFieldReference(
    element: PsiElement,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {
    override fun resolve(): PsiElement? {
        val field = scopedField() ?: BlueprintService.getInstance(element.project).field(handle) ?: return null
        val psiFile = PsiManager.getInstance(element.project).findFile(field.file) ?: return null
        return psiFile.findElementAt(field.offset) ?: psiFile
    }

    /** Look the handle up within the enclosing scopes (innermost first); null if not in scope. */
    private fun scopedField(): BlueprintField? {
        val scopes = AntlersScopeResolver.scopesAt(element)
        if (scopes.isEmpty()) return null
        val svc = BlueprintService.getInstance(element.project)
        return scopes.firstNotNullOfOrNull { s -> svc.fieldsFor(s.namespace).firstOrNull { it.handle == handle } }
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
