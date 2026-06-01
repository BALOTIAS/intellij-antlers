package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldtypePropertiesTest {

    @Test fun assetAliasMatchesAssets() {
        assertEquals(FieldtypeProperties.forType("asset"), FieldtypeProperties.forType("assets"))
    }

    @Test fun assetsHasUrl() {
        assertTrue(FieldtypeProperties.forType("assets").any { it.name == "url" })
    }

    @Test fun entriesHasTitleAndUrl() {
        val names = FieldtypeProperties.forType("entries").map { it.name }
        assertTrue(names.contains("title"))
        assertTrue(names.contains("url"))
    }

    @Test fun unknownTypeIsEmpty() {
        assertTrue(FieldtypeProperties.forType("text").isEmpty())
    }
}
