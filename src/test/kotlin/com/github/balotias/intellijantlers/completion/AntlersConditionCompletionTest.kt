package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersConditionCompletionTest : BasePlatformTestCase() {

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

    // `{{ collection:blog title:<caret> }}` — after a field's colon, offer the query-condition operators.
    fun testOperatorsOfferedAfterFieldColon() {
        val l = lookups("{{ collection:blog title:<caret> }}")
        assertTrue("contains offered: $l", l.contains("contains"))
        assertTrue("is offered: $l", l.contains("is"))
        assertTrue("starts_with offered: $l", l.contains("starts_with"))
    }

    // The tag's own colon (`collection:<caret>`) must still offer methods/handles, NOT operators.
    fun testTagHeadColonStillOffersMethodsNotOperators() {
        val l = lookups("{{ collection:<caret> }}")
        assertFalse("operators must not leak into tag-method position: $l", l.contains("contains"))
    }

    // Operators only on condition-capable tags (collection/taxonomy/users), not e.g. nav.
    fun testOperatorsNotOfferedOnNonConditionTag() {
        val l = lookups("{{ nav:main foo:<caret> }}")
        assertFalse("nav is not condition-capable: $l", l.contains("contains"))
    }

    // `{{ collection:blog <caret> }}` — offer the collection's blueprint fields as condition targets.
    fun testFieldNamesOfferedAsConditionTargets() {
        setupBlueprints()
        val l = lookups("{{ collection:blog <caret> }}")
        assertTrue("blueprint field offered as condition target: $l", l.contains("hero_title"))
        // …alongside the tag's own params.
        assertTrue("tag params still offered: $l", l.contains("limit"))
    }
}
