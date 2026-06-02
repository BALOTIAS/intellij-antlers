package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersClosingTagTest : BasePlatformTestCase() {

    private fun firstSuggestion(text: String): String? {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings?.firstOrNull()
    }

    fun testNearestUnclosedPreselected() {
        assertEquals("collection", firstSuggestion("{{ collection:blog }}{{ /<caret> }}"))
    }

    fun testInnermostOfNested() {
        assertEquals("if", firstSuggestion("{{ collection:blog }}{{ if x }}{{ /<caret> }}"))
    }

    fun testNoUnclosedNoCrash() {
        firstSuggestion("{{ /<caret> }}")   // must not throw
    }

    fun testNoDuplicateForPreselectedCloser() {
        myFixture.configureByText("p.antlers.html", "{{ collection:blog }}{{ /<caret> }}")
        myFixture.completeBasic()
        val collectionEntries = myFixture.lookupElementStrings?.count { it == "collection" } ?: 0
        assertEquals("collection should appear exactly once (no normal-list duplicate)", 1, collectionEntries)
    }
}
