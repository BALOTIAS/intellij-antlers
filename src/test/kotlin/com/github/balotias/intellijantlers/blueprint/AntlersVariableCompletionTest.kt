package com.github.balotias.intellijantlers.blueprint

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersVariableCompletionTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testOffersBlueprintFieldsAndSystemVars() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: Hero Title\n"
        )
        val l = lookups("{{ <caret> }}")
        assertTrue("offers blueprint field: $l", l.contains("hero_title"))
        assertTrue("offers system var title: $l", l.contains("title"))
        assertTrue("still offers tags: $l", l.contains("collection"))
    }
}
