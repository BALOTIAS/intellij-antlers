package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersTagInsertTest : BasePlatformTestCase() {

    private fun completeTag(textWithCaret: String, tag: String) {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        myFixture.lookup?.let { lookup ->
            val item = lookup.items.firstOrNull { it.lookupString == tag }
            if (item != null) {
                lookup.currentItem = item
                myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
            }
        }
    }

    fun testPairTagCaretInParamSlot() {
        completeTag("{{ collection<caret> }}", "collection")
        assertEquals("{{ collection  }}{{ /collection }}", myFixture.file.text)
        assertEquals("{{ collection ".length, myFixture.caretOffset)
    }

    fun testSingleTagUnchanged() {
        completeTag("{{ partial<caret> }}", "partial")
        assertEquals("{{ partial }}", myFixture.file.text)
        assertEquals("{{ partial".length, myFixture.caretOffset)
    }
}
