package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersParamModifierInsertTest : BasePlatformTestCase() {

    private fun completeItem(textWithCaret: String, predicate: (String) -> Boolean): Boolean {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        val lookup = myFixture.lookup ?: return false
        val item = lookup.items.firstOrNull { predicate(it.lookupString) } ?: return false
        lookup.currentItem = item
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        return true
    }

    fun testParameterCaretInsideQuotes() {
        assertTrue("a parameter should be offered", completeItem("{{ collection <caret> }}") { true })
        val text = myFixture.file.text
        val caret = myFixture.caretOffset
        assertTrue("inserted name=\"\": $text", text.contains("=\"\""))
        assertEquals("char before caret is opening quote", '"', text[caret - 1])
        assertEquals("char at caret is closing quote", '"', text[caret])
    }

    fun testModifierCaretInsideParens() {
        assertTrue("truncate modifier offered", completeItem("{{ title | <caret> }}") { it == "truncate" })
        val text = myFixture.file.text
        val caret = myFixture.caretOffset
        assertTrue("inserted (): $text", text.contains("truncate()"))
        assertEquals('(', text[caret - 1])
        assertEquals(')', text[caret])
    }
}
