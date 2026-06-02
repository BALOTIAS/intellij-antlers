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

    fun testSingleTagStartsInParamSlot() {
        completeTag("{{ yield<caret> }}", "yield")
        assertEquals("{{ yield  }}", myFixture.file.text)
        assertEquals("{{ yield ".length, myFixture.caretOffset)
    }

    fun testCollectionDefaultIsParamSlot() {
        completeTag("{{ collection<caret> }}", "collection")
        assertEquals("{{ collection  }}{{ /collection }}", myFixture.file.text)
        assertEquals("{{ collection ".length, myFixture.caretOffset)
    }

    fun testPartialStartsInParamSlot() {
        completeTag("{{ partial<caret> }}", "partial")
        assertEquals("{{ partial  }}", myFixture.file.text)
        assertEquals("{{ partial ".length, myFixture.caretOffset)
    }

    fun testNonHandleTagKeepsParamSlot() {
        // 'cache' is a pair tag → param-slot behavior (no colon).
        completeTag("{{ cache<caret> }}", "cache")
        assertEquals("{{ cache  }}{{ /cache }}", myFixture.file.text)
        assertEquals("{{ cache ".length, myFixture.caretOffset)
    }
}
