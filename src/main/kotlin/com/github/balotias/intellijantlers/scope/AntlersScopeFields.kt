package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Single decision point for "which blueprint fields apply at this caret". */
object AntlersScopeFields {

    /**
     * null     → no iterating scope encloses the caret: caller uses the global fallback (all fields).
     * non-null → restrict to exactly these scoped fields (innermost-first, deduped by handle); do NOT
     *            fall back to global. May be empty when the scope is known but defines no fields.
     */
    fun fieldsInScope(element: PsiElement, project: Project): List<BlueprintField>? {
        val scopes = AntlersScopeResolver.scopesAt(element)
        if (scopes.isEmpty()) return null
        val svc = BlueprintService.getInstance(project)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<BlueprintField>()
        for (scope in scopes) { // innermost first
            for (f in svc.fieldsFor(scope.namespace)) {
                if (seen.add(f.handle)) out.add(f)
            }
        }
        return out
    }
}
