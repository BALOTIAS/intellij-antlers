package com.github.balotias.intellijantlers.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCatalogServiceTest : BasePlatformTestCase() {

    fun testBundledTagsAndModifiers() {
        val svc = AntlersCatalogService.getInstance(project)
        assertNotNull("collection tag", svc.tag("collection"))
        assertTrue("has modifiers", svc.modifiers().any { it.name == "upper" })
        assertTrue("tag names include nav", svc.tagNames().contains("nav"))
        assertTrue(svc.tag("collection")!!.parameters.any { it.name == "limit" })
    }
}
