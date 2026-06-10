package com.github.balotias.intellijantlers.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/**
 * Resolves the Statamic `svg` tag's name (`{{ svg:trash }}` / `{{ svg src="trash" }}`) to the SVG file,
 * via [StatamicProject.resolveSvg]. Soft: an unresolved name is left unflagged (it may resolve to a path
 * the plugin doesn't index, or be inline markup).
 */
class AntlersSvgReference(element: PsiElement, range: TextRange, private val name: String) :
    PsiReferenceBase<PsiElement>(element, range, true) {

    override fun resolve(): PsiElement? {
        val vf = StatamicProject.resolveSvg(element, name) ?: return null
        return PsiManager.getInstance(element.project).findFile(vf)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
