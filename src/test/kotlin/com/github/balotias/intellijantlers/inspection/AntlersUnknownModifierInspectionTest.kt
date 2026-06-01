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
}
