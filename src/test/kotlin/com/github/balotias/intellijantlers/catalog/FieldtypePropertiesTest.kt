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

    @Test fun codeFieldtypeHasCodeAndMode() {
        val names = FieldtypeProperties.forType("code").map { it.name }
        assertTrue(names.contains("code"))
        assertTrue(names.contains("mode"))
    }

    // Source-verified asset augmentation keys (AugmentedAsset.php) that were missing.
    @Test fun assetsHaveSourceVerifiedImageProps() {
        val names = FieldtypeProperties.forType("assets").map { it.name }.toSet()
        for (p in listOf("ratio", "orientation", "focus_css", "is_svg", "is_audio", "is_video",
                "edit_url", "folder", "size_kb", "size_b")) {
            assertTrue("missing asset prop: $p", names.contains(p))
        }
        // The previously-questioned keys are real in source — they must stay.
        assertTrue("mime_type is a real key", names.contains("mime_type"))
        assertTrue("size_bytes is a real key", names.contains("size_bytes"))
    }
}
