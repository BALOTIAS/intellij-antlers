package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.balotias.intellijantlers.editor.AntlersParamSession

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

    fun testNoParamSingleCaretAfterTag() {
        // `yield` has no params → caret lands after the tag, no slot, no session.
        completeTag("{{ yield<caret> }}", "yield")
        assertEquals("{{ yield }}", myFixture.file.text)
        assertEquals("{{ yield }}".length, myFixture.caretOffset)
        assertNull(AntlersParamSession.of(myFixture.editor))
    }

    fun testNoParamPairCaretInBlock() {
        // `nocache` has no params → caret lands in the block, no slot, no session.
        completeTag("{{ nocache<caret> }}", "nocache")
        assertEquals("{{ nocache }}{{ /nocache }}", myFixture.file.text)
        assertEquals("{{ nocache }}".length, myFixture.caretOffset)
        assertNull(AntlersParamSession.of(myFixture.editor))
    }

    fun testNoParamSingleNoSession() {
        completeTag("{{ dump<caret> }}", "dump")
        assertEquals("{{ dump }}", myFixture.file.text)
        assertNull(AntlersParamSession.of(myFixture.editor))
    }

    fun testParamTagStillArmsSession() {
        completeTag("{{ collection<caret> }}", "collection")
        assertEquals("{{ collection  }}{{ /collection }}", myFixture.file.text)
        assertEquals("{{ collection ".length, myFixture.caretOffset)
        assertNotNull(AntlersParamSession.of(myFixture.editor))
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
