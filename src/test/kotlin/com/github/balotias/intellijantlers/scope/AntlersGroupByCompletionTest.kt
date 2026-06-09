package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersGroupByCompletionTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    // After a `groupby`, each group exposes `key` and a loopable `values`.
    fun testKeyAndValuesOfferedAfterGroupBy() {
        val l = lookups("{{ items = players groupby (team) }}{{ <caret> }}")
        assertTrue("key offered: $l", l.contains("key"))
        assertTrue("values offered: $l", l.contains("values"))
    }

    // Without a groupby in the template, the group vars aren't suggested.
    fun testKeyAndValuesNotOfferedWithoutGroupBy() {
        val l = lookups("{{ <caret> }}")
        assertFalse("key not offered without groupby: $l", l.contains("key"))
        assertFalse("values not offered without groupby: $l", l.contains("values"))
    }
}
