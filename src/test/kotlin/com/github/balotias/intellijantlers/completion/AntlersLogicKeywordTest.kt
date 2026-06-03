package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.lookup.Lookup

class AntlersLogicKeywordTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testOpenersOffered() {
        assertTrue(lookups("{{ i<caret> }}").contains("if"))
        assertTrue(lookups("{{ un<caret> }}").contains("unless"))
    }

    fun testFollowersSuppressedAtTopLevel() {
        val l = lookups("{{ e<caret> }}")
        assertFalse("else not offered with nothing open", l.contains("else"))
        assertFalse("elseif not offered with nothing open", l.contains("elseif"))
        assertFalse("endif not offered with nothing open", l.contains("endif"))
    }

    fun testFollowersOfferedInsideIf() {
        val l = lookups("{{ if x }}{{ e<caret> }}")
        assertTrue(l.contains("else"))
        assertTrue(l.contains("elseif"))
        assertTrue(l.contains("endif"))
        assertFalse("endunless belongs to unless", l.contains("endunless"))
    }

    fun testFollowersOfferedInsideUnless() {
        val l = lookups("{{ unless x }}{{ e<caret> }}")
        assertTrue(l.contains("else"))
        assertTrue(l.contains("endunless"))
        assertFalse("elseif not valid for unless", l.contains("elseif"))
        assertFalse("endif belongs to if", l.contains("endif"))
    }

    fun testFollowersSuppressedWhenPairTagInnermost() {
        val l = lookups("{{ if x }}{{ collection }}{{ e<caret> }}")
        assertFalse("else suppressed when a pair tag is innermost", l.contains("else"))
        assertFalse("endif suppressed when a pair tag is innermost", l.contains("endif"))
        // Separate probe: the IDEA prefix matcher filters by the typed prefix, and `if` shares no
        // letter with `e`, so it cannot surface under an `e` prefix regardless of what is offered.
        // Use an `i` prefix to assert openers are still offered when a pair tag is innermost.
        assertTrue("openers still offered", lookups("{{ if x }}{{ collection }}{{ i<caret> }}").contains("if"))
    }

    private fun insert(text: String, keyword: String) {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        myFixture.lookup?.let { lk ->
            val item = lk.items.firstOrNull { it.lookupString == keyword } ?: return
            lk.currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }

    private fun docText() = myFixture.editor.document.text

    fun testOpenerInsertsConditionSlotAndCloser() {
        insert("{{ i<caret> }}", "if")
        assertEquals("{{ if  }}{{ /if }}", docText())
        assertEquals("{{ if ".length, myFixture.caretOffset)
        // A condition-mode session is armed so Tab jumps into the block (not a repeating-param slot).
        val session = AntlersParamSession.of(myFixture.editor)
        assertNotNull(session)
        assertTrue("condition-mode (single jump, no slot repeating)", session!!.conditionMode)
    }

    fun testElseifInsertsConditionSlotNoCloser() {
        insert("{{ if x }}{{ el<caret> }}", "elseif")
        assertEquals("{{ if x }}{{ elseif  }}", docText())
        assertEquals("{{ if x }}{{ elseif ".length, myFixture.caretOffset)
    }

    fun testEndifInsertsPlainAndCaretAfterBraces() {
        insert("{{ if x }}{{ end<caret> }}", "endif")
        assertEquals("{{ if x }}{{ endif }}", docText())
        assertEquals("{{ if x }}{{ endif }}".length, myFixture.caretOffset)
    }
}
