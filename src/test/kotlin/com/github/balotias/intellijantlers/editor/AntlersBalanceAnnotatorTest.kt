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

    // #regression (user-reported): a `{var}` string interpolation whose variable shares a pair-tag's name
    // (`{taxonomy}` in `from="{taxonomy}"`) is a variable access, not an unclosed tag — its injected
    // fragment must not be balance-checked, so the enclosing `{{ taxonomy }}` still balances.
    fun testInterpolationMatchingPairTagNameNotFlagged() {
        val text = "{{ taxonomy from=\"{taxonomy}\" collection=\"{handle}\" as=\"r\" }}{{ /taxonomy }}"
        assertTrue("interpolation {taxonomy} wrongly flagged: ${balance(text).map { it.description }}",
            balance(text).isEmpty())
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

    // #regression (user-reported): a whitespace-separated leading-colon sub-field condition
    // `:field.sub="x"` must NOT be absorbed into the tag's name path, so the tag still balances against
    // its `{{ /collection:events }}` closer.
    fun testLeadingColonSubfieldConditionDoesNotBreakBalance() {
        val text = "{{ collection:events :event_date.start=\"today\" }}{{ /collection:events }}"
        assertTrue("condition absorbed into the opener handle: ${mismatch(text).map { it.description }}",
            mismatch(text).isEmpty())
    }

    // A stray closer (no opener) offers a fix that removes it.
    fun testRemoveStrayCloserFix() {
        myFixture.configureByText("p.antlers.html", "{{ /collection }}")
        val fixes = myFixture.getAllQuickFixes()
        val fix = fixes.firstOrNull { it.text.startsWith("Remove stray") }
        assertNotNull("expected a remove-stray-closer fix: ${fixes.map { it.text }}", fix)
        myFixture.launchAction(fix!!)
        assertFalse("stray closer removed: ${myFixture.file.text}", myFixture.file.text.contains("/collection"))
    }

    // An unclosed pair tag offers an "Insert closing" fix that appends the matching closer — keeping the
    // shorthand handle (`{{ /collection:blog }}`, not `{{ /collection }}`).
    fun testInsertCloserFixForUnclosedTag() {
        myFixture.configureByText("p.antlers.html", "{{ collection:blog }}")
        val fixes = myFixture.getAllQuickFixes()
        val fix = fixes.firstOrNull { it.text.startsWith("Insert closing") }
        assertNotNull("expected an insert-closer fix: ${fixes.map { it.text }}", fix)
        myFixture.launchAction(fix!!)
        assertTrue("closer keeps the shorthand: ${myFixture.file.text}", myFixture.file.text.contains("{{ /collection:blog }}"))
    }

    // The closer respects the shorthand even with condition/parameter tail — params are not part of it.
    fun testInsertCloserKeepsShorthandWithParams() {
        myFixture.configureByText(
            "p.antlers.html", "{{ collection:drinks type:is=\"tiki\" ingredients:in=\"Orgeat\" }}"
        )
        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.startsWith("Insert closing") }
        assertNotNull(fix)
        myFixture.launchAction(fix!!)
        assertTrue("closer is the full handle, no params: ${myFixture.file.text}",
            myFixture.file.text.contains("{{ /collection:drinks }}"))
    }

    // A plain tag with no shorthand still closes head-only.
    fun testInsertCloserPlainTag() {
        myFixture.configureByText("p.antlers.html", "{{ nocache }}")
        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.startsWith("Insert closing") }
        assertNotNull(fix)
        myFixture.launchAction(fix!!)
        assertTrue("plain closer: ${myFixture.file.text}", myFixture.file.text.contains("{{ /nocache }}"))
    }

    fun testInsertCloserFixForUnclosedCondition() {
        myFixture.configureByText("p.antlers.html", "{{ if x }}")
        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.startsWith("Insert closing") }
        assertNotNull(fix)
        myFixture.launchAction(fix!!)
        assertTrue("if closer appended: ${myFixture.file.text}", myFixture.file.text.contains("{{ /if }}"))
    }

    // The name path of such an opener stops at the tag handle (`collection:events`), not the condition.
    fun testConditionNotAbsorbedIntoNamePath() {
        myFixture.configureByText("p.antlers.html", "{{ collection:events :event_date.start=\"today\" }}")
        val np = com.intellij.psi.util.PsiTreeUtil.findChildOfType(
            myFixture.file, com.github.balotias.intellijantlers.psi.AntlersNamePathMixin::class.java
        )!!
        assertEquals(listOf("collection", "events"), np.segments)
    }
}
