package com.github.balotias.intellijantlers.catalog

/** Backward-compatible aggregate surface; the data lives in CatalogTags / CatalogModifiers. */
object CatalogData {
    val TAGS: List<TagDef> = CatalogTags.ALL
    val MODIFIERS: List<ModifierDef> = CatalogModifiers.ALL
}
