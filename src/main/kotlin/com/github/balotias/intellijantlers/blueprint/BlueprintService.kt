package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/** Project-cached blueprint fields. Registered via @Service (NOT plugin.xml). */
@Service(Service.Level.PROJECT)
class BlueprintService(private val project: Project) {

    fun fields(): List<BlueprintField> = scanned().distinctBy { it.handle }

    fun field(handle: String): BlueprintField? = scanned().firstOrNull { it.handle == handle }

    /** All fields in a given namespace (union across its blueprints), deduped by handle (first wins). */
    fun fieldsFor(ns: BlueprintNamespace): List<BlueprintField> =
        scanned().filter { it.namespace == ns }.distinctBy { it.handle }

    private fun scanned(): List<BlueprintField> =
        CachedValuesManager.getManager(project).getCachedValue(project, KEY, {
            CachedValueProvider.Result.create(BlueprintScanner.scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    companion object {
        private val KEY = Key.create<CachedValue<List<BlueprintField>>>("antlers.blueprintFields")
        fun getInstance(project: Project): BlueprintService = project.service()
    }
}
