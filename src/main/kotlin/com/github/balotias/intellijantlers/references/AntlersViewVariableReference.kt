package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.view.ViewFrontMatterService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafPsiElement

/** Soft reference from `{{ view:<name> }}` to the front-matter `<name>:` key in the same file. */
class AntlersViewVariableReference(
    element: PsiElement,
    private val name: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val file = element.containingFile ?: return null
        val entry = ViewFrontMatterService.getInstance(element.project).topLevel(file)
            .firstOrNull { it.name == name } ?: return null
        val vf = file.virtualFile ?: return null
        return AntlersViewVarDeclaration(element.project, name, vf, entry.key.start)
    }

    override fun isReferenceTo(target: PsiElement): Boolean =
        target is AntlersViewVarDeclaration && target.varName == name && resolve() == target

    override fun handleElementRename(newElementName: String): PsiElement {
        val leaf = element as? LeafPsiElement ?: return element
        return leaf.replaceWithText(newElementName).psi
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
