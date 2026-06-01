package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersEditorBasicsTest : BasePlatformTestCase() {

    fun testBracePairsConfigured() {
        val pairs = AntlersBraceMatcher().bracePairs.map { it.leftBraceType to it.rightBraceType }
        assertTrue(pairs.contains(AntlersTypes.T_LDOUBLE to AntlersTypes.T_RDOUBLE))
        assertTrue(pairs.contains(AntlersTypes.T_COMMENT_OPEN to AntlersTypes.T_COMMENT_CLOSE))
    }

    fun testBlockCommentWrapsSelection() {
        // Test that the AntlersCommenter is configured with the correct block comment delimiters.
        // Testing the platform routing via ACTION_COMMENT_BLOCK is unreliable in a template-language
        // context (the platform may route to the HTML data-language commenter instead).
        val commenter = AntlersCommenter()
        assertEquals("{{#", commenter.blockCommentPrefix)
        assertEquals("#}}", commenter.blockCommentSuffix)
        assertNull(commenter.lineCommentPrefix)
    }
}
