package com.github.balotias.intellijantlers.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersTypedHandlerTest : BasePlatformTestCase() {

    fun testAutoCloseOnSecondBrace() {
        myFixture.configureByText("t.antlers.html", "<caret>")
        myFixture.type("{{")
        myFixture.checkResult("{{ <caret> }}")
    }

    fun testNoDoubleCloseWhenAlreadyClosed() {
        myFixture.configureByText("t.antlers.html", "{<caret>}}")
        myFixture.type("{")
        myFixture.checkResult("{{<caret>}}")
    }

    fun testNoTripleBrace() {
        myFixture.configureByText("t.antlers.html", "{{<caret>")
        myFixture.type("{")
        myFixture.checkResult("{{{<caret>")
    }
}
