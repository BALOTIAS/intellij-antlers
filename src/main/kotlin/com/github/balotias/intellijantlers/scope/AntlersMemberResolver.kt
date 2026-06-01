package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Resolves a dotted/colon path (e.g. [hero, bio]) to the field it names, walking the E3a sub-field tree. */
object AntlersMemberResolver {

    /** The namespace whose fields are [field]'s direct children (its sub-fields). */
    fun childNamespace(field: BlueprintField): BlueprintNamespace =
        field.namespace.copy(path = field.namespace.path + field.handle)

    /** The field that [pathPrefix] names at [element], or null if any segment doesn't resolve. */
    fun resolveField(element: PsiElement, pathPrefix: List<String>, project: Project): BlueprintField? {
        if (pathPrefix.isEmpty()) return null
        var current = AntlersFieldContext.resolveField(element, pathPrefix[0], project) ?: return null
        val svc = BlueprintService.getInstance(project)
        for (i in 1 until pathPrefix.size) {
            current = svc.fieldsFor(childNamespace(current)).firstOrNull { it.handle == pathPrefix[i] } ?: return null
        }
        return current
    }
}
