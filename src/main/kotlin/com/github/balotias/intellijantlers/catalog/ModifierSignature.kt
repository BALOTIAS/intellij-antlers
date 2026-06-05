package com.github.balotias.intellijantlers.catalog

/** Renders a modifier's positional signature, e.g. `truncate(length, ellipsis = '…')`. Pure / IntelliJ-free. */
object ModifierSignature {

    /** The label for one parameter: `name`, `name = default`, or `name?` (optional, no default). */
    fun paramLabel(p: ModifierParam): String = when {
        !p.optional -> p.name
        p.default.isNotBlank() -> "${p.name} = ${p.default}"
        else -> "${p.name}?"
    }

    /** `name` when there are no params, otherwise `name(p1, p2, …)`. */
    fun render(def: ModifierDef): String =
        if (def.parameters.isEmpty()) def.name
        else "${def.name}(${def.parameters.joinToString(", ") { paramLabel(it) }})"
}
