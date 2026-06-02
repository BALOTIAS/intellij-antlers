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

    /** Type the handle into the live-template field and confirm it mirrors into the closer. */
    private fun typeHandle(handle: String) {
        TemplateManagerImpl.getTemplateState(myFixture.editor)!!.let { state ->
            myFixture.type(handle)
            state.nextTab()
        }
    }

    fun testCollectionShorthandMirrorsCloser() {
        completeShorthand("{{ :coll<caret> }}", "collection")
        typeHandle("blog")
        // the handle typed once fills the opener AND the mirrored closer
        assertEquals("{{ collection:blog }}{{ /collection:blog }}", myFixture.file.text)
    }

    fun testPartialShorthandSingleNoCloser() {
        completeShorthand("{{ :part<caret> }}", "partial")
        typeHandle("cards")
        assertEquals("{{ partial:cards }}", myFixture.file.text)
    }
}
