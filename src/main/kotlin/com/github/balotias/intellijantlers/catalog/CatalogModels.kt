package com.github.balotias.intellijantlers.catalog

data class TagDef(
    val name: String,
    val description: String = "",
    val docUrl: String = "",
    val isPair: Boolean = false,
    val methods: List<String> = emptyList(),
    val parameters: List<ParamDef> = emptyList()
)

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
    val takesArguments: Boolean = false
)
