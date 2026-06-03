package com.github.balotias.intellijantlers.catalog

import com.github.balotias.intellijantlers.catalog.scan.ModifierScanner
import com.github.balotias.intellijantlers.catalog.scan.TagScanner
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/** Source of truth for tag/modifier completion: bundled JSON merged with project-scanned customs. */
@Service(Service.Level.PROJECT)
class AntlersCatalogService(private val project: Project) {

    private val bundledTags: List<TagDef> by lazy { CatalogLoader.loadTags() }
    private val bundledModifiers: List<ModifierDef> by lazy { CatalogLoader.loadModifiers() }

    fun tags(): List<TagDef> {
        val major = StatamicVersionService.getInstance(project).majorVersion()
        val bundled = bundledTags.filter { it.appliesTo(major) }
        val custom = scannedTagNames().filter { name -> bundled.none { it.name == name } }
            .map { TagDef(name = it, description = "Custom tag") }
        return bundled + custom
    }

    fun tagNames(): List<String> = tags().map { it.name }

    fun tag(name: String): TagDef? = tags().firstOrNull { it.name == name }

    /** O(1) membership test backed by a cached name set — for hot callers (e.g. the highlight pass). */
    fun isTag(name: String): Boolean = tagNameSet().contains(name)

    private fun tagNameSet(): Set<String> =
        CachedValuesManager.getManager(project).getCachedValue(project, TAG_NAMES_KEY, {
            CachedValueProvider.Result.create(tags().mapTo(HashSet()) { it.name }, PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    fun modifiers(): List<ModifierDef> {
        val major = StatamicVersionService.getInstance(project).majorVersion()
        val bundled = bundledModifiers.filter { it.appliesTo(major) }
        val custom = scannedModifierNames().filter { name -> bundled.none { it.name == name } }
            .map { ModifierDef(name = it, description = "Custom modifier") }
        return bundled + custom
    }

    private fun scannedTagNames(): List<String> =
        CachedValuesManager.getManager(project).getCachedValue(project, TAG_KEY, {
            CachedValueProvider.Result.create(TagScanner.scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    private fun scannedModifierNames(): List<String> =
        CachedValuesManager.getManager(project).getCachedValue(project, MOD_KEY, {
            CachedValueProvider.Result.create(ModifierScanner.scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    companion object {
        private val TAG_KEY = Key.create<CachedValue<List<String>>>("antlers.scannedTags")
        private val MOD_KEY = Key.create<CachedValue<List<String>>>("antlers.scannedModifiers")
        private val TAG_NAMES_KEY = Key.create<CachedValue<Set<String>>>("antlers.tagNameSet")
        fun getInstance(project: Project): AntlersCatalogService = project.service()
    }
}
