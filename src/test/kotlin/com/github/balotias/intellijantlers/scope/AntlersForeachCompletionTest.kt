package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersForeachCompletionTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    // Inside a `{{ foreach }}` loop, the default `key` and `value` variables are offered.
    fun testKeyValueOfferedInsideForeach() {
        val l = lookups("{{ foreach:items }}{{ <caret> }}{{ /foreach:items }}")
        assertTrue("key offered: $l", l.contains("key"))
        assertTrue("value offered: $l", l.contains("value"))
    }

    // `as="k|v"` renames them — offer the aliases instead.
    fun testAliasedKeyValueOffered() {
        val l = lookups("{{ foreach:items as=\"k|v\" }}{{ <caret> }}{{ /foreach:items }}")
        assertTrue("alias k offered: $l", l.contains("k"))
        assertTrue("alias v offered: $l", l.contains("v"))
    }

    // Outside any foreach, the loop variables aren't suggested.
    fun testKeyValueNotOfferedOutsideForeach() {
        val l = lookups("{{ <caret> }}")
        assertFalse("value not offered outside foreach: $l", l.contains("value"))
    }
}
