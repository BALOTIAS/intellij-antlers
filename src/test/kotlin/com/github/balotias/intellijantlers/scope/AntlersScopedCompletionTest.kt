package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersScopedCompletionTest : BasePlatformTestCase() {

    private fun setupBlueprints() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: Hero Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/taxonomies/tags/tags.yaml",
            "fields:\n  - handle: tag_color\n    field:\n      type: text\n      display: Tag Color\n"
        )
    }

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testInsideCollectionOffersScopedFieldsLoopAndSystemVars() {
        setupBlueprints()
        val l = lookups("{{ collection:blog }}{{ <caret> }}{{ /collection }}")
        assertTrue("blog field: $l", l.contains("hero_title"))
        assertFalse("not the taxonomy-only field: $l", l.contains("tag_color"))
        assertTrue("loop var index: $l", l.contains("index"))
        assertTrue("system var title: $l", l.contains("title"))
        assertTrue("still offers tags: $l", l.contains("collection"))
    }

    fun testTopLevelStillOffersEverything() {
        setupBlueprints()
        val l = lookups("{{ <caret> }}")
        assertTrue("blog field: $l", l.contains("hero_title"))
        assertTrue("taxonomy field too: $l", l.contains("tag_color"))
        assertFalse("no loop vars at top level: $l", l.contains("index"))
    }
}
