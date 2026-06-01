package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLoaderTest {

    @Test
    fun loadsBundledTags() {
        val tags = CatalogLoader.loadTags()
        assertTrue("expected core tags", tags.size >= 10)
        val collection = tags.firstOrNull { it.name == "collection" }
        assertNotNull("collection tag present", collection)
        assertTrue("collection is a pair tag", collection!!.isPair)
        assertTrue("collection has a 'from' or 'limit' param",
            collection.parameters.any { it.name == "limit" || it.name == "from" })
    }

    @Test
    fun loadsBundledModifiers() {
        val mods = CatalogLoader.loadModifiers()
        assertTrue("expected modifiers", mods.size >= 10)
        assertNotNull("upper modifier present", mods.firstOrNull { it.name == "upper" })
    }
}
