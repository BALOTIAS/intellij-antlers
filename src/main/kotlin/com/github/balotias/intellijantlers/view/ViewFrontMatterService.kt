package com.github.balotias.intellijantlers.view

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/** Cached, per-file access to a view's front matter. */
@Service(Service.Level.PROJECT)
class ViewFrontMatterService(private val project: Project) {

    fun frontMatter(file: PsiFile): FrontMatter? =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(
                ViewFrontMatterScanner.scan(file.text),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }

    /** Top-level keys only — the names accessible as `{{ view:<name> }}`. */
    fun topLevel(file: PsiFile): List<FmEntry> =
        frontMatter(file)?.entries?.filter { it.indent == 0 } ?: emptyList()

    companion object {
        fun getInstance(project: Project): ViewFrontMatterService = project.service()
    }
}
