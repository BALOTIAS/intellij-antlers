package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersViewCompletionTest : BasePlatformTestCase() {

    private fun complete(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testViewColonOffersFrontMatterKeys() {
        val items = complete("---\nfoo: bar\nbaz: qux\n---\n{{ view:<caret> }}")
        assertTrue("offers front-matter keys, got $items", items.containsAll(listOf("foo", "baz")))
    }

    fun testViewColonWithoutFrontMatterOffersNothing() {
        val items = complete("{{ view:<caret> }}")
        assertTrue("no view keys without front matter, got $items", items.none { it == "foo" })
    }

    fun testHeadOffersViewNamespaceWhenFrontMatterPresent() {
        val items = complete("---\nfoo: bar\n---\n{{ <caret> }}")
        assertTrue("offers the 'view' namespace, got ${items.take(40)}", items.contains("view"))
    }

    fun testHeadOmitsViewNamespaceWithoutFrontMatter() {
        val items = complete("{{ <caret> }}")
        assertFalse("no 'view' namespace without front matter", items.contains("view"))
    }
}
