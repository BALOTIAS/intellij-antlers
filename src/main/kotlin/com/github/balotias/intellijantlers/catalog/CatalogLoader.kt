package com.github.balotias.intellijantlers.catalog

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CatalogLoader {
    private val gson = Gson()

    fun loadTags(): List<TagDef> = read("/catalog/tags.json", object : TypeToken<List<TagDef>>() {}.type)

    fun loadModifiers(): List<ModifierDef> = read("/catalog/modifiers.json", object : TypeToken<List<ModifierDef>>() {}.type)

    private fun <T> read(resource: String, type: java.lang.reflect.Type): T {
        val stream = CatalogLoader::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled catalog resource: $resource")
        return stream.bufferedReader().use { gson.fromJson(it, type) }
    }
}
