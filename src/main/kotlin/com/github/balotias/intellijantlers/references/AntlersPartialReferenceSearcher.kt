package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersFileType
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * Makes ReferencesSearch find AntlersPartialReferences when the target is a Statamic partial file.
 * Iterates all Antlers files in the project scope and checks each for references resolving to target.
 */
class AntlersPartialReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(params: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val target = params.elementToSearch as? PsiFile ?: return
        val vf = target.virtualFile ?: return
        if (!vf.name.endsWith(".antlers.html") && !vf.name.endsWith(".html")) return

        val project = params.project
        val scope = params.effectiveSearchScope as? GlobalSearchScope
            ?: GlobalSearchScope.allScope(project)

        val psiManager = PsiManager.getInstance(project)
        FileTypeIndex.getFiles(AntlersFileType.INSTANCE, scope).forEach { antlersVf ->
            val psiFile = psiManager.findFile(antlersVf) ?: return@forEach
            iterateReferences(psiFile, target, consumer)
        }
    }

    private fun iterateReferences(file: PsiFile, target: PsiFile, consumer: Processor<in PsiReference>) {
        file.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                super.visitElement(element)
                for (ref in element.references) {
                    if (ref is AntlersPartialReference && ref.isReferenceTo(target)) {
                        consumer.process(ref)
                    }
                }
            }
        })
    }
}
