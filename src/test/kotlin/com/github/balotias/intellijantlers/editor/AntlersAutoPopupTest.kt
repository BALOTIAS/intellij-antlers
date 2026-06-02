package com.github.balotias.intellijantlers.editor

import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase

class AntlersAutoPopupTest : CompletionAutoPopupTestCase() {
    fun testPopupInsideBraces() {
        myFixture.configureByText("p.antlers.html", "{{ <caret> }}")
        type("c")
        assertNotNull("completion should auto-popup inside {{ }}", lookup)
    }
}
