package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenamePsiElementProcessor

/**
 * Claims AntlersFieldDeclaration for rename and force-collects its (soft) references through
 * ReferencesSearch so every `{{ handle }}` usage is renamed. The default renameElement then edits
 * the YAML via the element's setName and rewrites each usage via its handleElementRename.
 *
 * Robustness guarantee: on the current SDK the default rename pipeline already collects our soft
 * references via the registered AntlersFieldReferenceSearcher, so rename works even without this
 * processor — the rename tests pass either way. This processor exists so the soft-reference
 * collection is explicit and survives SDK versions whose default flow skips soft references.
 */
class AntlersFieldRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean =
        element is AntlersFieldDeclaration

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> =
        ReferencesSearch.search(element, searchScope).findAll()
}
