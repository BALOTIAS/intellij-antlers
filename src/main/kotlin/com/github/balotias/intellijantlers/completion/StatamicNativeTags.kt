package com.github.balotias.intellijantlers.completion

object StatamicNativeTags {
    val TAGS = listOf(
        "collection",
        "nav",
        "asset",
        "user",
        "form",
        "partial",
        "yield",
        "section",
        "loop",
        "if",
        "else",
        "elseif",
        "unless",
        "glide",
        "cache",
        "increment",
        "redirect",
        "session",
        "obfuscate",
        "markdown",
        "theme",
        "mix",
        "vite",
        "foreach",
        "dump",
        "dd",
        "cookie",
        "protect",
        "relate",
        "search",
        "taxonomies",
        "terms"
    )

    /**
     * Block/pair tags that require a matching `{{ /tag }}`. Everything else is treated as a single tag.
     */
    val PAIR_TAGS = setOf(
        "collection",
        "nav",
        "foreach",
        "loop",
        "if",
        "unless",
        "form",
        "user",
        "cache",
        "search",
        "section",
        "protect",
        "taxonomies",
        "terms",
        "relate"
    )
}
