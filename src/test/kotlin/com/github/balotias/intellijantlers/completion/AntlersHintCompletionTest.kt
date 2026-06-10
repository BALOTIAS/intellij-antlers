package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersHintCompletionTest : BasePlatformTestCase() {
    private fun complete(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testDirectivesOfferedAfterAtInComment() {
        val items = complete("{{# @<caret> #}}")
        assertTrue("offers hint directives, got $items", items.containsAll(listOf("name", "collection", "blueprint")))
    }

    fun testDeprecatedDirectiveOffered() {
        assertTrue("offers @deprecated", complete("{{# @<caret> #}}").contains("deprecated"))
    }

    fun testFiltersByTypedPrefix() {
        // 'col' → only 'collection' matches; completeBasic auto-inserts the single match.
        myFixture.configureByText("p.antlers.html", "{{# @col<caret> #}}")
        myFixture.completeBasic()
        assertTrue("collection inserted, got: ${myFixture.editor.document.text}",
            myFixture.editor.document.text.contains("@collection"))
    }

    fun testNormalTagUnaffected() {
        val items = complete("{{ <caret> }}")
        assertFalse("a plain tag position does not offer hint directives", items.contains("blueprint"))
    }
}
