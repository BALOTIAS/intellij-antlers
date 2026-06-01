package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSplitTest {

    @Test fun catalogDataDelegatesToSplitObjects() {
        assertSame(CatalogTags.ALL, CatalogData.TAGS)
        assertSame(CatalogModifiers.ALL, CatalogData.MODIFIERS)
    }

    @Test fun existingEntriesPreserved() {
        assertTrue(CatalogData.TAGS.any { it.name == "collection" })
        assertTrue(CatalogData.TAGS.any { it.name == "partial" })
        assertTrue(CatalogData.MODIFIERS.any { it.name == "upper" })
        // no duplicate tag names
        assertEquals(CatalogData.TAGS.size, CatalogData.TAGS.map { it.name }.toSet().size)
    }
}
