package com.github.balotias.intellijantlers.catalog

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/** Loads the bundled JSON catalog from plugin resources. Jackson's Kotlin module honors the
 *  data classes' default values for keys absent from the JSON. */
object CatalogLoader {
    private val mapper = jacksonObjectMapper()

    fun loadTags(): List<TagDef> = read("/catalog/tags.json")

    fun loadModifiers(): List<ModifierDef> = read("/catalog/modifiers.json")

    private inline fun <reified T> read(resource: String): List<T> {
        val stream = CatalogLoader::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled catalog resource: $resource")
        return stream.bufferedReader().use { mapper.readValue(it.readText()) }
    }
}
