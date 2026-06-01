package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionContextTest : BasePlatformTestCase() {

    private fun classifyAt(text: String): AntlersCompletionInfo {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret) ?: error("no element at caret")
        return AntlersCompletionContext.classify(el)
    }

    fun testDotProducesFieldPath() {
        val info = classifyAt("{{ a.b.<caret> }}")
        assertEquals(AntlersCompletionKind.FIELD_PATH, info.kind)
        assertEquals(listOf("a", "b"), info.pathPrefix)
    }

    fun testColonCarriesPathPrefix() {
        val info = classifyAt("{{ group:<caret> }}")
        assertEquals(AntlersCompletionKind.TAG_METHOD, info.kind)
        assertEquals(listOf("group"), info.pathPrefix)
    }

    private fun kindAt(text: String): AntlersCompletionKind {
        val caret = text.indexOf("<caret>")
        // Simulate the dummy identifier the platform inserts at the caret during real completion,
        // so a caret right after a partial name parses as part of that token (not as whitespace).
        val withDummy = text.replace("<caret>", "IntellijIdeaRulezzz")
        myFixture.configureByText("t.antlers.html", withDummy)
        val element = myFixture.file.findElementAt(caret) ?: myFixture.file.findElementAt(caret - 1)!!
        return AntlersCompletionContext.classify(element).kind
    }

    fun testTagNameAtStart() = assertEquals(AntlersCompletionKind.TAG_NAME, kindAt("{{ <caret> }}"))
    fun testTagNameTyping() = assertEquals(AntlersCompletionKind.TAG_NAME, kindAt("{{ coll<caret> }}"))
    fun testClosingTagName() = assertEquals(AntlersCompletionKind.TAG_NAME, kindAt("{{ /<caret> }}"))
    fun testTagMethod() = assertEquals(AntlersCompletionKind.TAG_METHOD, kindAt("{{ collection:<caret> }}"))
    fun testParameter() = assertEquals(AntlersCompletionKind.PARAMETER, kindAt("{{ collection <caret> }}"))
    fun testParameterAfterMethod() = assertEquals(AntlersCompletionKind.PARAMETER, kindAt("{{ collection:blog <caret> }}"))
    fun testModifier() = assertEquals(AntlersCompletionKind.MODIFIER, kindAt("{{ title | <caret> }}"))
    fun testNoneInHtml() = assertEquals(AntlersCompletionKind.NONE, kindAt("<div <caret>></div>"))
    fun testNoneInValue() = assertEquals(AntlersCompletionKind.NONE, kindAt("{{ collection limit=\"<caret>\" }}"))
    fun testClosingTagNoParameter() = assertEquals(AntlersCompletionKind.NONE, kindAt("{{ /collection <caret> }}"))
}
