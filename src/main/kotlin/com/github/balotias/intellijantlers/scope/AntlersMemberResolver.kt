package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.CONTAINER_FIELD_TYPES
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Resolves a dotted/colon path to the field it names, following container sub-fields and relationships. */
object AntlersMemberResolver {

    /** The single sub-namespace of a container field (kept for callers migrating to childNamespaces). */
    fun childNamespace(field: BlueprintField): BlueprintNamespace =
        field.namespace.copy(path = field.namespace.path + field.handle)

    /** The namespace(s) whose fields are [field]'s members: container sub-fields OR linked blueprint(s). */
    fun childNamespaces(field: BlueprintField): List<BlueprintNamespace> = when {
        field.type.lowercase() in CONTAINER_FIELD_TYPES ->
            listOf(field.namespace.copy(path = field.namespace.path + field.handle))
        field.linkedNamespaces.isNotEmpty() -> field.linkedNamespaces
        else -> emptyList()
    }

    /** The field that [pathPrefix] names at [element], or null if any segment doesn't resolve. */
    fun resolveField(element: PsiElement, pathPrefix: List<String>, project: Project): BlueprintField? {
        if (pathPrefix.isEmpty()) return null
        var current = AntlersFieldContext.resolveField(element, pathPrefix[0], project) ?: return null
        val svc = BlueprintService.getInstance(project)
        for (i in 1 until pathPrefix.size) {
            current = childNamespaces(current)
                .firstNotNullOfOrNull { ns -> svc.fieldsFor(ns).firstOrNull { it.handle == pathPrefix[i] } }
                ?: return null
        }
        return current
    }
}
