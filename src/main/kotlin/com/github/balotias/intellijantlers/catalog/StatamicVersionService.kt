package com.github.balotias.intellijantlers.catalog

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Resolves the project's Statamic MAJOR version for catalog tailoring: `composer.json`'s
 * `statamic/cms` constraint first, then `composer.lock`'s exact version, else [LATEST_MAJOR].
 * Cached on the PSI modification count (re-resolves after composer edits).
 */
@Service(Service.Level.PROJECT)
class StatamicVersionService(private val project: Project) {

    fun majorVersion(): Int =
        CachedValuesManager.getManager(project).getCachedValue(project, KEY, {
            CachedValueProvider.Result.create(resolve(), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    private fun resolve(): Int {
        shallowest("composer.json")?.let { f -> majorFromJson(load(f))?.let { return it } }
        shallowest("composer.lock")?.let { f -> majorFromLock(load(f))?.let { return it } }
        return LATEST_MAJOR
    }

    private fun shallowest(name: String): VirtualFile? =
        try {
            FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))
                .minByOrNull { it.path.length }
        } catch (e: Exception) {
            null
        }

    private fun load(file: VirtualFile): String =
        try { VfsUtilCore.loadText(file) } catch (e: Exception) { "" }

    private fun majorFromJson(text: String): Int? =
        JSON_CONSTRAINT.find(text)?.groupValues?.get(1)
            ?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

    private fun majorFromLock(text: String): Int? =
        LOCK_VERSION.find(text)?.groupValues?.get(1)?.toIntOrNull()

    companion object {
        const val LATEST_MAJOR = 6

        private val KEY = Key.create<CachedValue<Int>>("antlers.statamicVersion")
        private val JSON_CONSTRAINT = Regex("\"statamic/cms\"\\s*:\\s*\"([^\"]+)\"")
        // composer.lock: { "name": "statamic/cms", … "version": "v6.1.2" }. The tempered `(?!"name")`
        // skip stops at the next package boundary, so we read statamic/cms's OWN version and never
        // bleed into a sibling package's version. The closing quote in "statamic/cms" already excludes
        // prefixed packages like "statamic/cms-eloquent-driver".
        private val LOCK_VERSION =
            Regex("\"name\"\\s*:\\s*\"statamic/cms\"(?:(?!\"name\")[\\s\\S])*?\"version\"\\s*:\\s*\"v?(\\d+)")

        fun getInstance(project: Project): StatamicVersionService = project.service()
    }
}
