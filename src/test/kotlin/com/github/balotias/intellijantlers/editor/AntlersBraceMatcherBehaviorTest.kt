package com.github.balotias.intellijantlers.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersBraceMatcherBehaviorTest : BasePlatformTestCase() {

    fun testMatchBraceJumpsToClosing() {
        // Caret on the opening `{{` of `{{ collection }}`.
        myFixture.configureByText("p.antlers.html", "<caret>{{ collection }}")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MATCH_BRACE)
        // text: `{{ collection }}`
        //        0         1
        //        0123456789012345
        // closing `}}` starts at offset 14. MATCH_BRACE lands the caret after the
        // matched closing brace pair (offset 16 = end of `}}`).
        assertEquals(16, myFixture.caretOffset)
    }

    fun testMatchBraceFromClosing() {
        // Caret at the start of the closing `}}`.
        myFixture.configureByText("p.antlers.html", "{{ collection <caret>}}")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MATCH_BRACE)
        // Jumps back to the opening `{{` (offset 0).
        assertEquals(0, myFixture.caretOffset)
    }
}
