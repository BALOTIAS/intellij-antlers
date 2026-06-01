package com.github.balotias.intellijantlers

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("t.antlers.html", text)
        val elements = myFixture.completeBasic()
        return elements?.map { it.lookupString } ?: emptyList()
    }

    fun testTagNameCompletionInsideBraces() {
        assertTrue(lookups("{{ <caret> }}").contains("collection"))
    }

    fun testTagNamesNotOfferedInPlainHtml() {
        assertFalse(lookups("<p><caret></p>").contains("collection"))
    }

    fun testHtmlTagCompletionStillWorks() {
        assertTrue(lookups("<d<caret>").contains("div"))
    }

    fun testParameterCompletion() {
        assertTrue(lookups("{{ collection <caret> }}").contains("limit"))
    }

    fun testParameterNotOfferedForUnknownTag() {
        assertFalse(lookups("{{ somethingcustom <caret> }}").contains("limit"))
    }

    fun testModifierCompletion() {
        assertTrue(lookups("{{ title | <caret> }}").contains("upper"))
    }

    fun testTagMethodCompletion() {
        assertTrue(lookups("{{ collection:<caret> }}").contains("count"))
    }

    fun testCollectionInsertsClosingTag() {
        myFixture.configureByText("test.antlers.html", "{{ collec<caret>")
        myFixture.completeBasic()
        myFixture.checkResult("{{ collection }}\n    <caret>\n{{ /collection }}")
    }
}
