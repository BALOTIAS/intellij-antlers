package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersParamModifierInsertTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

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
        // pin to a known collection parameter so the test exercises ParameterInsertHandler, not whatever sorts first
        assertTrue("the 'from' parameter should be offered", completeItem("{{ collection <caret> }}") { it == "from" })
        val text = myFixture.file.text
        val caret = myFixture.caretOffset
        assertTrue("inserted name=\"\": $text", text.contains("=\"\""))
        assertEquals("char before caret is opening quote", '"', text[caret - 1])
        assertEquals("char at caret is closing quote", '"', text[caret])
    }

    fun testModifierCaretInsideParens() {
        assertTrue("truncate modifier offered", completeItem("{{ title | <caret> }}") { it == "truncate" })
        // truncate has one required param (length): a live template (length) is started; finish it.
        TemplateManagerImpl.getTemplateState(myFixture.editor)?.gotoEnd(false)
        val text = myFixture.file.text
        assertTrue("inserted required param: $text", text.contains("truncate(length)"))
    }
}
