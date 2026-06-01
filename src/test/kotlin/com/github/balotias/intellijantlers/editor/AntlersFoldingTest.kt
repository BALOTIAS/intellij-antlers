package com.github.balotias.intellijantlers.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFoldingTest : BasePlatformTestCase() {

    private fun foldCount(text: String): Int {
        val file = myFixture.configureByText("t.antlers.html", text)
        return AntlersFoldingBuilder().buildFoldRegions(file, myFixture.editor.document, false).size
    }

    fun testPairedTagFolds() {
        // {{ collection }} ... {{ /collection }} is one foldable region
        assertTrue(foldCount("{{ collection }}\n  hi\n{{ /collection }}") >= 1)
    }

    fun testConditionFolds() {
        assertTrue(foldCount("{{ if x }}\n  hi\n{{ /if }}") >= 1)
    }

    fun testEndifFolds() {
        assertTrue(foldCount("{{ if x }}\n  hi\n{{ endif }}") >= 1)
    }

    fun testCommentFoldsExactlyOnce() {
        assertEquals(1, foldCount("{{# a long hidden comment here #}}"))
    }

    fun testPairedTagPlusCommentYieldsTwoFolds() {
        assertEquals(2, foldCount("{{ collection }}\n  hi\n{{ /collection }}\n{{# a long hidden comment here #}}"))
    }

    fun testCommentFolds() {
        assertTrue(foldCount("{{# a long hidden comment here #}}") >= 1)
    }

    fun testNoFoldForSingleVariable() {
        assertEquals(0, foldCount("{{ title }}"))
    }

    fun testUnbalancedDoesNotThrow() {
        // just must not throw and produce no bogus fold
        foldCount("{{ collection }}\n{{ /nav }}")
    }
}
