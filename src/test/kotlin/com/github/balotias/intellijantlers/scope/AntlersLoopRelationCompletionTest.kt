package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersLoopRelationCompletionTest : BasePlatformTestCase() {

    private fun setupBlueprints() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: Hero Title\n"
        )
    }

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    // Inside a loop, `next`/`prev` reach the adjacent iteration — offer them like loop vars.
    fun testNextPrevOfferedInsideLoop() {
        setupBlueprints()
        val l = lookups("{{ collection:blog }}{{ <caret> }}{{ /collection }}")
        assertTrue("next offered: $l", l.contains("next"))
        assertTrue("prev offered: $l", l.contains("prev"))
    }

    fun testNextPrevNotOfferedAtTopLevel() {
        setupBlueprints()
        val l = lookups("{{ <caret> }}")
        assertFalse("next not offered outside a loop: $l", l.contains("next"))
        assertFalse("prev not offered outside a loop: $l", l.contains("prev"))
    }

    // `{{ next:<field> }}` resolves the loop's scoped fields.
    fun testNextColonOffersLoopFields() {
        setupBlueprints()
        val l = lookups("{{ collection:blog }}{{ next:<caret> }}{{ /collection }}")
        assertTrue("scoped field after next:: $l", l.contains("hero_title"))
    }

    fun testPrevColonOffersLoopFields() {
        setupBlueprints()
        val l = lookups("{{ collection:blog }}{{ prev:<caret> }}{{ /collection }}")
        assertTrue("scoped field after prev:: $l", l.contains("hero_title"))
    }
}
