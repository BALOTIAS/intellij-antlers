package com.github.balotias.intellijantlers.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersUnknownModifierInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(AntlersUnknownModifierInspection())
    }

    private fun unknownModifierWarnings(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Unknown modifier") }
    }

    fun testUnknownModifierFlagged() {
        assertTrue("expected an unknown-modifier warning",
            unknownModifierWarnings("{{ x | unknownmod }}").isNotEmpty())
    }

    fun testKnownModifierNotFlagged() {
        assertTrue("upper is a catalog modifier",
            unknownModifierWarnings("{{ x | upper }}").isEmpty())
    }

    fun testKnownModifierWithArgsNotFlagged() {
        assertTrue("truncate is a catalog modifier",
            unknownModifierWarnings("{{ x | truncate(10) }}").isEmpty())
    }

    fun testBogusModifierFlaggedWithName() {
        assertTrue("expected an unknown-modifier warning naming 'bogusmod'",
            unknownModifierWarnings("{{ title | bogusmod }}")
                .any { it.contains("Unknown modifier 'bogusmod'") })
    }

    fun testBundledModifierNotFlagged() {
        assertTrue("upper is a bundled catalog modifier",
            unknownModifierWarnings("{{ title | upper }}").isEmpty())
    }

    fun testScannedCustomModifierNotFlagged() {
        myFixture.addFileToProject("app/Modifiers/MyFmt.php", "<?php\nclass MyFmt extends Modifier {}")
        assertTrue("my_fmt is discovered by the project scan",
            unknownModifierWarnings("{{ title | my_fmt }}").isEmpty())
    }

    fun testKnownModifierWithColonArgNotFlagged() {
        assertTrue("truncate is a catalog modifier even with a colon-arg",
            unknownModifierWarnings("{{ title | truncate:10 }}").isEmpty())
    }

    fun testBarePipeNoNameNotFlagged() {
        assertTrue("a bare pipe with no modifier name produces no warning",
            unknownModifierWarnings("{{ title | }}").isEmpty())
    }

    // A close typo of a real modifier offers a "Change to '…'" quick-fix that rewrites the name.
    fun testQuickFixSuggestsClosestModifier() {
        myFixture.configureByText("p.antlers.html", "{{ title | uppr }}")
        val fixes = myFixture.getAllQuickFixes()
        val fix = fixes.firstOrNull { it.text == "Change to 'upper'" }
        assertNotNull("expected a 'Change to upper' fix: ${fixes.map { it.text }}", fix)
        myFixture.launchAction(fix!!)
        myFixture.checkResult("{{ title | upper }}")
    }

    // A name nowhere near a real modifier offers no spurious suggestion.
    fun testNoSuggestionForUnrelatedName() {
        myFixture.configureByText("p.antlers.html", "{{ title | zzzqqqxyz }}")
        assertTrue("no 'Change to' fix for an unrelated name",
            myFixture.getAllQuickFixes().none { it.text.startsWith("Change to ") })
    }
}
