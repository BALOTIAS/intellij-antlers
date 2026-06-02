package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersShorthandInsertTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    private fun completeShorthand(textWithCaret: String, lookupString: String) {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        myFixture.lookup?.let { lk ->
            val item = lk.items.firstOrNull { it.lookupString == lookupString } ?: return
            lk.currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }

    // Asserts the live-template skeleton right after finishLookup. The synced mirror of a repeated
    // $HANDLE$ variable (empty until the user types) is a platform guarantee; the test harness does
    // not route subsequent typing into the template field, so we assert the inserted template text.
    fun testCollectionShorthandMirrorsCloser() {
        completeShorthand("{{ :coll<caret> }}", "collection")
        assertEquals("{{ collection: }}{{ /collection: }}", myFixture.file.text)
    }

    fun testPartialShorthandSingleNoCloser() {
        completeShorthand("{{ :part<caret> }}", "partial")
        assertEquals("{{ partial: }}", myFixture.file.text)
    }
}
