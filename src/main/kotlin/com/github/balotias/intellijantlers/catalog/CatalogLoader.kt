package com.github.balotias.intellijantlers.catalog

/** Provides the bundled catalog. Data lives in [CatalogData] (no external JSON parser needed). */
object CatalogLoader {
    fun loadTags(): List<TagDef> = CatalogData.TAGS
    fun loadModifiers(): List<ModifierDef> = CatalogData.MODIFIERS
}
