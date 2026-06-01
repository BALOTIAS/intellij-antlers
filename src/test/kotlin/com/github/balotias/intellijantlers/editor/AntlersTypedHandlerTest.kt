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

    /**
     * Regression: stray-"}" replacement must not eat a double-close "}}" that was legitimately present.
     * This covers the "already closed" case where the cursor is placed BEFORE an existing "}}".
     */
    fun testDoesNotEatExistingBrace() {
        // File already contains "}}" — typing "{{" before it must not delete either brace.
        // The "don't double if }}" guard fires first, so we get {{<caret>}} (no extra insertion).
        myFixture.configureByText("t.antlers.html", "<caret>}}")
        myFixture.type("{{")
        myFixture.checkResult("{{<caret>}}")
    }
}
