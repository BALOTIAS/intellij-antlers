package com.github.balotias.intellijantlers.scope

/** A nav-tree variable available inside a `{{ nav … }}` loop. */
data class NavVariable(val name: String, val description: String)

object NavVariables {
    val ALL: List<NavVariable> = listOf(
        NavVariable("depth", "The item's depth in the nav tree (1-based)."),
        NavVariable("is_current", "True when this item is the current URL."),
        NavVariable("is_parent", "True when this item is an ancestor of the current URL."),
        NavVariable("is_published", "Whether the item's entry is published."),
        NavVariable("is_page", "Whether the item is a page (vs a manual link)."),
        NavVariable("is_entry", "Whether the item references an entry."),
        NavVariable("is_external", "Whether the item is an external link."),
        NavVariable("has_entries", "Whether the item has child entries."),
        NavVariable("children", "The item's child nav items."),
        NavVariable("parent", "The item's parent nav item."),
        NavVariable("url", "The item's URL.")
    )
}
