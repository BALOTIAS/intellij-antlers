package com.github.balotias.intellijantlers.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCatalogVersionFilterTest : BasePlatformTestCase() {

    private fun catalog() = AntlersCatalogService.getInstance(project)

    fun testFiveShowsRelateHidesV6Modifier() {
        myFixture.addFileToProject("composer.json", """{ "require": { "statamic/cms": "^5.0" } }""")
        assertNotNull("relate is a Statamic 5 tag", catalog().tag("relate"))
        assertFalse(
            "urlencode_except_slashes is v6-only",
            catalog().modifiers().any { it.name == "urlencode_except_slashes" },
        )
    }

    fun testSixHidesRelateShowsV6Modifier() {
        myFixture.addFileToProject("composer.json", """{ "require": { "statamic/cms": "^6.0" } }""")
        assertNull("relate was removed in Statamic 6", catalog().tag("relate"))
        assertTrue(
            "urlencode_except_slashes exists in v6",
            catalog().modifiers().any { it.name == "urlencode_except_slashes" },
        )
    }

    fun testDefaultIsLatest() {
        assertNull(catalog().tag("relate"))
        assertTrue(catalog().modifiers().any { it.name == "urlencode_except_slashes" })
    }
}
