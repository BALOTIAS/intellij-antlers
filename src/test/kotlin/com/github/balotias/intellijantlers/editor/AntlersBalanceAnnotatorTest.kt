package com.github.balotias.intellijantlers.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersBalanceAnnotatorTest : BasePlatformTestCase() {

    /** Highlights whose description is one of F's balance diagnostics. */
    private fun balance(text: String): List<HighlightInfo> {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting().filter {
            val d = it.description ?: ""
            d.contains("has no matching") || d.contains("is never closed")
        }
    }

    fun testUnopenedTagCloser() {
        val d = balance("{{ /collection }}")
        assertTrue("expected ERROR: ${d.map { it.description }}",
            d.any { it.severity == HighlightSeverity.ERROR && it.description.contains("no matching") })
    }

    fun testUnopenedConditionCloser() {
        val d = balance("{{ endif }}")
        assertTrue(d.any { it.severity == HighlightSeverity.ERROR && it.description.contains("no matching") })
    }

    fun testUnclosedTagOpener() {
        val d = balance("{{ collection:blog }}")
        assertTrue(d.any { it.severity == HighlightSeverity.WARNING && it.description.contains("never closed") })
    }

    fun testUnclosedConditionOpener() {
        val d = balance("{{ if foo }}")
        assertTrue(d.any { it.severity == HighlightSeverity.WARNING && it.description.contains("never closed") })
    }

    fun testBalancedHasNoDiagnostics() {
        assertTrue(balance("{{ if foo }}{{ /if }}").isEmpty())
        assertTrue(balance("{{ collection:blog }}{{ /collection }}").isEmpty())
        assertTrue(balance("{{ collection:blog }}{{ if x }}{{ /if }}{{ /collection }}").isEmpty())
    }

    // The `once` block construct is a known pair, so it gets balance-checked like if/collection.
    fun testUnclosedOnceWarns() {
        val d = balance("{{ once }}")
        assertTrue(d.any { it.severity == HighlightSeverity.WARNING && it.description.contains("never closed") })
    }

    fun testBalancedOnceAndPushHaveNoDiagnostics() {
        assertTrue(balance("{{ once }}{{ /once }}").isEmpty())
        assertTrue(balance("{{ push:scripts }}{{ /push:scripts }}").isEmpty())
    }

    fun testUnknownConstructIgnored() {
        assertTrue("stray /unknownaddon must not be flagged", balance("{{ /unknownaddon }}").isEmpty())
        assertTrue("bare unknown tag must not be flagged", balance("{{ unknownaddon }}").isEmpty())
    }

    // #regression: a slash-path closing partial inside a paired tag used to break parser recovery,
    // making the *enclosing* tag look unclosed. The inner partial close must not corrupt balance.
    fun testSlashPathCloserDoesNotBreakEnclosingBalance() {
        assertTrue("enclosing {{ if }} wrongly flagged: " +
            balance("{{ if x }}{{ partial:components/notification }}hi{{ /partial:components/notification }}{{ /if }}")
                .map { it.description },
            balance("{{ if x }}{{ partial:components/notification }}hi{{ /partial:components/notification }}{{ /if }}").isEmpty())
    }

    // #regression: the `%` tag-disambiguation prefix used to error and break recovery, making the
    // enclosing paired tag look unclosed.
    fun testPercentPrefixDoesNotBreakEnclosingBalance() {
        assertTrue("enclosing {{ if }} wrongly flagged: " +
            balance("{{ if x }}{{ %collection:blog }}{{ /%collection:blog }}{{ /if }}").map { it.description },
            balance("{{ if x }}{{ %collection:blog }}{{ /%collection:blog }}{{ /if }}").isEmpty())
    }

    private fun mismatch(text: String): List<com.intellij.codeInsight.daemon.impl.HighlightInfo> {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting().filter { (it.description ?: "").contains("does not match") }
    }

    fun testHandleMismatchWarns() {
        val d = mismatch("{{ collection:blog }}{{ /collection:news }}")
        assertTrue("mismatch flagged: ${d.map { it.description }}",
            d.any { it.severity == com.intellij.lang.annotation.HighlightSeverity.WARNING })
    }

    fun testHeadOnlyCloserNotFlagged() =
        assertTrue(mismatch("{{ collection:blog }}{{ /collection }}").isEmpty())

    fun testMatchingHandleNotFlagged() =
        assertTrue(mismatch("{{ collection:blog }}{{ /collection:blog }}").isEmpty())

    fun testMultiSegmentMismatchWarns() =
        assertTrue(mismatch("{{ nav:collection:blog }}{{ /nav:collection:news }}")
            .any { it.severity == com.intellij.lang.annotation.HighlightSeverity.WARNING })

    fun testMultiSegmentMatchNotFlagged() =
        assertTrue(mismatch("{{ nav:collection:blog }}{{ /nav:collection:blog }}").isEmpty())
}
