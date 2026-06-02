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

    /**
     * The live document text — the source of truth the Tab handler edits. We assert against this, not
     * `myFixture.file.text`, because `myFixture.type` updates the document without committing the PSI,
     * so `file.text` lags behind by the uncommitted edits (e.g. it would show `from` for `from="x"`).
     */
    private fun docText() = myFixture.editor.document.text

    fun testNoSessionTabDelegates() {
        // No completion ran, so no session is armed: Tab must behave normally and not throw.
        myFixture.configureByText("p.antlers.html", "<caret>hello")
        assertNull(AntlersParamSession.of(myFixture.editor))
        tab()
        assertNull(AntlersParamSession.of(myFixture.editor))
        assertTrue("document keeps its content", docText().contains("hello"))
    }

    fun testPairTabOpensNewSlot() {
        complete("{{ collection<caret> }}", "collection")
        // {{ collection  }}{{ /collection }}, caret centred.
        myFixture.type("from=\"blog\"")
        // {{ collection from="blog" }}{{ /collection }}, caret after the value, one trailing space.
        tab()
        assertEquals(
            "{{ collection from=\"blog\"  }}{{ /collection }}",
            docText(),
        )
        assertEquals("{{ collection from=\"blog\" ".length, myFixture.caretOffset)
    }

    fun testPairEmptySlotJumpsIntoBlock() {
        complete("{{ collection<caret> }}", "collection")
        myFixture.type("from=\"blog\"")
        tab()                  // opens a fresh empty slot
        tab()                  // empty slot → collapse + jump into the block
        assertEquals(
            "{{ collection from=\"blog\" }}{{ /collection }}",
            docText(),
        )
        assertEquals("{{ collection from=\"blog\" }}".length, myFixture.caretOffset)
    }

    fun testTerminalTabEndsSession() {
        complete("{{ collection<caret> }}", "collection")
        myFixture.type("from=\"blog\"")
        tab()                  // new slot
        tab()                  // into block, reachedTerminal = true
        assertNotNull(AntlersParamSession.of(myFixture.editor))
        tab()                  // terminal → end session, normal indent
        assertNull(AntlersParamSession.of(myFixture.editor))
    }

    fun testSingleTagFlow() {
        complete("{{ partial<caret> }}", "partial")
        // {{ partial  }}, caret centred.
        myFixture.type("foo=\"x\"")
        tab()                  // new slot
        tab()                  // empty slot → terminal is just after "}}"
        assertEquals("{{ partial foo=\"x\" }}", docText())
        assertEquals("{{ partial foo=\"x\" }}".length, myFixture.caretOffset)
    }

    fun testClickAwayFallsBackToNormalTab() {
        complete("{{ collection<caret> }}", "collection")
        assertNotNull(AntlersParamSession.of(myFixture.editor))
        myFixture.editor.caretModel.moveToOffset(0)   // click away
        tab()
        assertNull("session cleared when caret left the tag", AntlersParamSession.of(myFixture.editor))
        assertTrue(
            "the tag itself is untouched",
            docText().contains("{{ collection  }}{{ /collection }}"),
        )
    }
}
