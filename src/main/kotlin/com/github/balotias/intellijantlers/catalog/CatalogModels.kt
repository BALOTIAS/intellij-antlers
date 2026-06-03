package com.github.balotias.intellijantlers.catalog

data class TagDef(
    val name: String,
    val description: String = "",
    val docUrl: String = "",
    val isPair: Boolean = false,
    val methods: List<String> = emptyList(),
    val parameters: List<ParamDef> = emptyList(),
    val introducedIn: Int? = null,   // first Statamic major that has it (null = always)
    val removedIn: Int? = null,      // first Statamic major that DROPPED it (null = never)
) {
    /** True when this entry exists in Statamic major version [major]. */
    fun appliesTo(major: Int): Boolean =
        (introducedIn == null || major >= introducedIn) && (removedIn == null || major < removedIn)
}

data class ParamDef(
    val name: String,
    val description: String = "",
    val type: String = "",
    val required: Boolean = false
)

data class ModifierDef(
    val name: String,
    val description: String = "",
    val docUrl: String = "",
    val takesArguments: Boolean = false,
    val introducedIn: Int? = null,
    val removedIn: Int? = null,
) {
    /** True when this entry exists in Statamic major version [major]. */
    fun appliesTo(major: Int): Boolean =
        (introducedIn == null || major >= introducedIn) && (removedIn == null || major < removedIn)
}
