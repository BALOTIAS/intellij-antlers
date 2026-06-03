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
}
