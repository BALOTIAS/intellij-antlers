package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModifierCompletionParamTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    private fun completeModifier(textWithCaret: String, modifierName: String) {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        myFixture.lookup?.let { lk ->
            val item = lk.items.firstOrNull { it.lookupString == modifierName } ?: return
            lk.currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }

    fun testRequiredParamsInsertedAsTemplate() {
        completeModifier("{{ title | replac<caret> }}", "replace")
        // Finish the live template so the document is fully committed
        TemplateManagerImpl.getTemplateState(myFixture.editor)?.gotoEnd(false)
        assertEquals("{{ title | replace(search, replacement) }}", myFixture.editor.document.text)
    }

    fun testNoArgModifierInsertsNoParens() {
        completeModifier("{{ title | uppe<caret> }}", "upper")
        assertEquals("{{ title | upper }}", myFixture.editor.document.text)
    }

    fun testSignatureShownAsTailText() {
        myFixture.configureByText("p.antlers.html", "{{ title | re<caret> }}")
        val tails = myFixture.completeBasic()?.mapNotNull { le ->
            val p = com.intellij.codeInsight.lookup.LookupElementPresentation()
            le.renderElement(p); p.tailText
        } ?: emptyList()
        assertTrue("a lookup tail shows the signature args, got $tails",
            tails.any { it != null && it.contains("(search, replacement)") })
    }
}
