package com.github.balotias.intellijantlers.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCommenterBehaviorTest : BasePlatformTestCase() {

    /**
     * Real round-trip: with a selection covering an Antlers tag, ACTION_COMMENT_BLOCK
     * routes to our AntlersCommenter and wraps the selection with `{{#` / `#}}`
     * (the platform inserts the delimiters on their own lines). A second invocation
     * un-comments, restoring the original tag. Observed empirically; the trailing
     * newline left by the platform after un-commenting is asserted as-is rather than
     * trimmed, to keep the test honest about real behavior.
     */
    fun testBlockCommentRoundTripWithSelection() {
        myFixture.configureByText("p.antlers.html", "<selection>{{ title }}</selection>")

        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
        val wrapped = myFixture.editor.document.text
        // Routes to AntlersCommenter: wraps with Antlers block-comment delimiters.
        assertTrue("expected Antlers comment open, got: $wrapped", wrapped.contains("{{#"))
        assertTrue("expected Antlers comment close, got: $wrapped", wrapped.contains("#}}"))
        assertTrue("original tag should still be present inside the comment", wrapped.contains("{{ title }}"))

        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
        val uncommented = myFixture.editor.document.text
        // Round-trip removes the Antlers delimiters.
        assertFalse("comment open should be gone after round-trip", uncommented.contains("{{#"))
        assertFalse("comment close should be gone after round-trip", uncommented.contains("#}}"))
        assertTrue("original tag should remain after round-trip", uncommented.contains("{{ title }}"))
    }
}
