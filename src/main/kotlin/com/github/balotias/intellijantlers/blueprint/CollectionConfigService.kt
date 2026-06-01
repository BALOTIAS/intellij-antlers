package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/** Project-cached collection configs. Registered via @Service (NOT plugin.xml). */
@Service(Service.Level.PROJECT)
class CollectionConfigService(private val project: Project) {

    fun configs(): List<CollectionConfig> =
        CachedValuesManager.getManager(project).getCachedValue(project, KEY, {
            CachedValueProvider.Result.create(CollectionConfigScanner.scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    companion object {
        private val KEY = Key.create<CachedValue<List<CollectionConfig>>>("antlers.collectionConfigs")
        fun getInstance(project: Project): CollectionConfigService = project.service()
    }
}
