package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialParamCompletionTest : BasePlatformTestCase() {

    private val button = """
        {{#
            @param* label The caption label.
            @param as The wrapping element.
            @param button_type Inline if needed.
            @param faux Boolean.
        #}}
        <button>{{ label }}</button>
    """.trimIndent()

    private fun setup() {
        myFixture.addFileToProject("resources/views/components/_button.antlers.html", button)
    }

    private fun completeIn(text: String): List<String> {
        setup()
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    private fun completeElementsIn(text: String): Array<com.intellij.codeInsight.lookup.LookupElement> {
        setup()
        val file = myFixture.addFileToProject("resources/views/p2.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))
        return myFixture.completeBasic() ?: emptyArray()
    }

    fun testColonFormOffersPartialParamsAndSuppressesSrc() {
        val items = completeIn("{{ partial:components/button <caret> }}")
        assertTrue("offers declared params: $items",
            items.containsAll(listOf("label", "as", "button_type", "faux")))
        assertFalse("colon form must not offer src: $items", items.contains("src"))
    }

    fun testSrcFormOffersPartialParamsAndKeepsSrc() {
        val items = completeIn("{{ partial src=\"components/button\" <caret> }}")
        assertTrue("offers declared params: $items",
            items.containsAll(listOf("label", "as", "button_type", "faux")))
        assertTrue("src form keeps src: $items", items.contains("src"))
    }

    fun testRequiredMarkerShown() {
        val label = completeElementsIn("{{ partial:components/button <caret> }}").first { it.lookupString == "label" }
        val p = LookupElementPresentation(); label.renderElement(p)
        assertEquals("Param*", p.typeText)
    }

    fun testUnresolvedPartialDoesNotCrash() {
        val items = completeIn("{{ partial:does/not/exist <caret> }}")
        assertFalse(items.contains("label"))   // no params, and no exception
    }
}
