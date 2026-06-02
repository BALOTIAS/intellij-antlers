package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersParamTabTest : BasePlatformTestCase() {

    private fun complete(textWithCaret: String, tag: String) {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        myFixture.lookup?.let { lookup ->
            val item = lookup.items.firstOrNull { it.lookupString == tag } ?: return
            lookup.currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }

    private fun tab() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)

    fun testNoSessionTabDelegates() {
        // No completion ran, so no session is armed: Tab must behave normally and not throw.
        myFixture.configureByText("p.antlers.html", "<caret>hello")
        assertNull(AntlersParamSession.of(myFixture.editor))
        tab()
        assertNull(AntlersParamSession.of(myFixture.editor))
        assertTrue("document keeps its content", myFixture.file.text.contains("hello"))
    }
}
