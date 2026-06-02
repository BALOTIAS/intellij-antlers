package com.github.balotias.intellijantlers

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("t.antlers.html", text)
        val elements = myFixture.completeBasic()
        return elements?.map { it.lookupString } ?: emptyList()
    }

    fun testTagNameCompletionInsideBraces() {
        assertTrue(lookups("{{ <caret> }}").contains("collection"))
    }

    fun testTagNamesNotOfferedInPlainHtml() {
        assertFalse(lookups("<p><caret></p>").contains("collection"))
    }

    /**
     * Regression for the original bug: the outer HTML must be split out to the HTML data language so
     * the platform's HTML/CSS completion engages there. We assert the split at the PSI level (the
     * mechanism the fix restored); end-to-end HTML completion is verified manually via runIde, since
     * the test fixture's completeBasic does not route into the template-data HTML sub-tree.
     */
    fun testHtmlTemplateDataSplitWorks() {
        val file = myFixture.configureByText("t.antlers.html", "<div class=\"x\">{{ title }}</div>")
        val langs = file.viewProvider.languages.map { it.id }
        val diag = "fileType=${file.fileType.name}, provider=${file.viewProvider.javaClass.simpleName}, langs=$langs"
        assertTrue("HTML must be a data language. $diag", langs.any { it.equals("HTML", ignoreCase = true) })
        val htmlRoot = file.viewProvider.getPsi(com.intellij.lang.html.HTMLLanguage.INSTANCE)
        assertNotNull("HTML PSI root must exist", htmlRoot)
        assertTrue("HTML tree must contain the outer <div>: ${htmlRoot.text}", htmlRoot.text.contains("div"))
    }

    fun testParameterCompletion() {
        assertTrue(lookups("{{ collection <caret> }}").contains("limit"))
    }

    fun testParameterNotOfferedForUnknownTag() {
        assertFalse(lookups("{{ somethingcustom <caret> }}").contains("limit"))
    }

    fun testModifierCompletion() {
        assertTrue(lookups("{{ title | <caret> }}").contains("upper"))
    }

    fun testTagMethodCompletion() {
        assertTrue(lookups("{{ collection:<caret> }}").contains("count"))
    }

    fun testCollectionInsertsClosingTag() {
        myFixture.configureByText("test.antlers.html", "{{ collec<caret>")
        myFixture.completeBasic()
        myFixture.checkResult("{{ collection <caret> }}{{ /collection }}")
    }
}
