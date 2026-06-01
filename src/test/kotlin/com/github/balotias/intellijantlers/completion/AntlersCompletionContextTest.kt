package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionContextTest : BasePlatformTestCase() {

    private fun kindAt(text: String): AntlersCompletionKind {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("t.antlers.html", text)
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
}
