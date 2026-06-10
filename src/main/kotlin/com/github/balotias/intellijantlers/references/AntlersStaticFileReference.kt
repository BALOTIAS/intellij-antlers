package com.github.balotias.intellijantlers.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/**
 * Resolves a literal media path string (glide `src`, asset `url`, a bare path) to the file under the web
 * root, via [StatamicProject.resolvePublicFile]. Soft — an unresolved path is left unflagged.
 */
class AntlersStaticFileReference(element: PsiElement, range: TextRange, private val path: String) :
    PsiReferenceBase<PsiElement>(element, range, true) {

    override fun resolve(): PsiElement? {
        val vf = StatamicProject.resolvePublicFile(element, path) ?: return null
        return PsiManager.getInstance(element.project).findFile(vf)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
