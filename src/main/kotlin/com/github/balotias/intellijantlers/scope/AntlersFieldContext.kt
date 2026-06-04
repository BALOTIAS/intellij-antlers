package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.PageBlueprintResolver
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * The single authority for which blueprint namespaces/fields apply at an element.
 * Precedence: E1 loop scope (innermost first) -> E2 page mapping -> global.
 */
object AntlersFieldContext {

    /**
     * null -> no loop scope and no page mapping: callers use the global fallback (all fields).
     * non-null -> restrict to these namespaces, most-specific first.
     */
    fun namespacesFor(element: PsiElement): List<BlueprintNamespace>? {
        val scopes = AntlersScopeResolver.scopesAt(element)
        if (scopes.isNotEmpty()) return scopes.map { it.namespace }
        val hints = element.containingFile?.let { com.github.balotias.intellijantlers.blueprint.AntlersViewHints.declaredNamespaces(it) }
        if (!hints.isNullOrEmpty()) return hints
        val page = PageBlueprintResolver.namespacesFor(element)
        if (page.isNotEmpty()) return page
        return null
    }

    /** Fields visible at [element]: scoped union (deduped by handle), or null for the global fallback. */
    fun fieldsInScope(element: PsiElement, project: Project): List<BlueprintField>? {
        val namespaces = namespacesFor(element) ?: return null
        val svc = BlueprintService.getInstance(project)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<BlueprintField>()
        for (ns in namespaces) {
            for (f in svc.fieldsFor(ns)) if (seen.add(f.handle)) out.add(f)
        }
        return out
    }

    /** Resolve [handle] within the applicable namespaces, else the global lookup; null if unknown. */
    fun resolveField(element: PsiElement, handle: String, project: Project): BlueprintField? {
        val svc = BlueprintService.getInstance(project)
        val namespaces = namespacesFor(element)
            ?: return svc.field(handle)
        return namespaces.firstNotNullOfOrNull { ns -> svc.fieldsFor(ns).firstOrNull { it.handle == handle } }
    }
}
