package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersConditionTabTest : BasePlatformTestCase() {

    private fun complete(textWithCaret: String, keyword: String) {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        myFixture.lookup?.let { lk ->
            val item = lk.items.firstOrNull { it.lookupString == keyword } ?: return
            lk.currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }

    private fun tab() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)
    private fun doc() = myFixture.editor.document.text

    fun testTabAfterConditionJumpsIntoBlock() {
        complete("{{ i<caret> }}", "if")
        myFixture.type("test")
        tab()
        assertEquals("{{ if test }}{{ /if }}", doc())
        assertEquals("{{ if test }}".length, myFixture.caretOffset)
    }

    fun testEmptyConditionTabJumpsIntoBlock() {
        complete("{{ i<caret> }}", "if")
        tab()
        assertEquals("{{ if }}{{ /if }}", doc())
        assertEquals("{{ if }}".length, myFixture.caretOffset)
    }

    fun testSecondTabEndsConditionSession() {
        complete("{{ i<caret> }}", "if")
        myFixture.type("x")
        tab()
        assertNotNull(AntlersParamSession.of(myFixture.editor))
        tab()
        assertNull(AntlersParamSession.of(myFixture.editor))
    }
}
