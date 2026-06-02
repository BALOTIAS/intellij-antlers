package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersFileType
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * Makes ReferencesSearch find blueprint-field references when the target is an
 * AntlersFieldDeclaration. Iterates Antlers files and reports field/member references that resolve
 * to the same (handle, namespace).
 */
class AntlersFieldReferenceSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val target = params.elementToSearch as? AntlersFieldDeclaration ?: return
        val project = params.project
        val scope = params.effectiveSearchScope as? GlobalSearchScope
            ?: GlobalSearchScope.allScope(project)
        val psiManager = PsiManager.getInstance(project)
        FileTypeIndex.getFiles(AntlersFileType.INSTANCE, scope).forEach { vf ->
            val psiFile = psiManager.findFile(vf) ?: return@forEach
            psiFile.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    for (ref in element.references) {
                        if ((ref is AntlersBlueprintFieldReference || ref is AntlersBlueprintMemberReference) &&
                            ref.isReferenceTo(target)
                        ) {
                            consumer.process(ref)
                        }
                    }
                }
            })
        }
    }
}
