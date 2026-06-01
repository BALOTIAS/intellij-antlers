package com.github.balotias.intellijantlers

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionTest : BasePlatformTestCase() {

    // --- Antlers tag completion (only inside {{ }}) ---

    fun testAntlersTagsOfferedInsideBraces() {
        myFixture.configureByText("test.antlers.html", "Hello {{ <caret> }}")
        val lookups = myFixture.completeBasic()
        assertNotNull("Lookups should not be null inside braces", lookups)
        val strings = lookups.map { it.lookupString }
        assertTrue("Should contain 'collection'", strings.contains("collection"))
    }

    fun testAntlersTagsNotOfferedInPlainHtml() {
        myFixture.configureByText("test.antlers.html", "<p><caret></p>")
        val lookups = myFixture.completeBasic()
        val strings = lookups?.map { it.lookupString } ?: emptyList()
        assertFalse("Antlers tags must not pollute plain HTML completion", strings.contains("collection"))
    }

    // --- HTML completion must work in the data language (regression for the inverted template params) ---

    fun testHtmlTagCompletionWorks() {
        myFixture.configureByText("test.antlers.html", "<d<caret>")
        val lookups = myFixture.completeBasic()
        assertNotNull("HTML tag completion should produce results", lookups)
        assertTrue("Should offer the <div> tag", lookups.map { it.lookupString }.contains("div"))
    }

    // --- Insert handlers produce valid Antlers, never the broken `asset src=""` output ---

    fun testSingleTagInsertsClosingBracesOnly() {
        myFixture.configureByText("test.antlers.html", "{{ asse<caret>")
        myFixture.completeBasic() // single match -> auto-inserts 'asset'
        myFixture.checkResult("{{ asset<caret> }}")
    }

    fun testPairTagInsertsMatchingClose() {
        myFixture.configureByText("test.antlers.html", "{{ collec<caret>")
        myFixture.completeBasic() // single match -> auto-inserts 'collection'
        myFixture.checkResult("{{ collection }}\n    <caret>\n{{ /collection }}")
    }
}
