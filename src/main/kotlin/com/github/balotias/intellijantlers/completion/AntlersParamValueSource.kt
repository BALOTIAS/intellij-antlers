package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.references.StatamicProject
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

data class ParamValue(val text: String, val typeText: String)

/** Maps a tag parameter to its candidate values. Pure routing; no PSI/UI concerns. */
object AntlersParamValueSource {

    private val HANDLE_PARAMS = setOf("from", "in", "folder", "collection", "use", "handle")
    private val SORT_PARAMS = setOf("sort", "order_by")
    private val BOOLEAN_PARAMS = setOf("paginate", "show_unpublished", "show_future", "show_past", "disable_paging")
    private val SORT_KEYS = listOf("title", "date", "random")

    fun valuesFor(tagHead: String?, paramName: String?, position: PsiElement, project: Project): List<ParamValue> {
        val param = paramName?.lowercase() ?: return emptyList()
        return when {
            param == "src" && tagHead == "partial" ->
                StatamicProject.listPartials(position).map { ParamValue(it, "Partial") }

            param in HANDLE_PARAMS ->
                StatamicProject.listCollectionHandles(position).map { ParamValue(it, "Collection") } +
                    StatamicProject.listTaxonomyHandles(position).map { ParamValue(it, "Taxonomy") }

            param in SORT_PARAMS -> sortValues(position, project)

            param in BOOLEAN_PARAMS -> listOf(ParamValue("true", "Boolean"), ParamValue("false", "Boolean"))

            else -> emptyList()
        }
    }

    private fun sortValues(position: PsiElement, project: Project): List<ParamValue> {
        val fields = (AntlersFieldContext.fieldsInScope(position, project)?.map { it.handle }
            ?: BlueprintService.getInstance(project).fields().map { it.handle })
        val keys = (fields + SORT_KEYS).distinct()
        val out = mutableListOf<ParamValue>()
        for (k in keys) {
            out.add(ParamValue(k, "Sort"))
            out.add(ParamValue("$k:asc", "Sort"))
            out.add(ParamValue("$k:desc", "Sort"))
        }
        return out
    }
}
