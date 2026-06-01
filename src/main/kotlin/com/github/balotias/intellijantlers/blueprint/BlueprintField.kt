package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.vfs.VirtualFile

/** A field declared in a Statamic blueprint/fieldset: the `handle` is the `{{ variable }}` name. */
data class BlueprintField(
    val handle: String,
    val display: String,
    val type: String,
    val file: VirtualFile,
    val offset: Int,
    val namespace: BlueprintNamespace,
    val linkedNamespaces: List<BlueprintNamespace> = emptyList()
)

/** Fieldtypes that nest sub-fields (Grid / Group / Replicator / Bard). Shared by the scope + member resolvers. */
val CONTAINER_FIELD_TYPES = setOf("grid", "group", "replicator", "bard")
