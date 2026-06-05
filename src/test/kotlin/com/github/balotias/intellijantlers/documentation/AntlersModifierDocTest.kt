package com.github.balotias.intellijantlers.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersModifierDocTest : BasePlatformTestCase() {

    /** Quick-doc HTML for the modifier identifier in [text] (caret marked with <caret>). */
    private fun docFor(text: String): String? {
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val caret = text.indexOf("<caret>")
        val el = myFixture.file.findElementAt(caret)!!
        val provider = AntlersDocumentationProvider()
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, el, caret) ?: el
        return provider.generateDoc(target, el)
    }

    fun testSignatureInTitle() {
        val doc = docFor("{{ title | rep<caret>lace('a', 'b') }}")!!
        assertTrue("signature present, got: $doc", doc.contains("replace(search, replacement)"))
    }

    fun testParameterDescriptionsListed() {
        val doc = docFor("{{ title | rep<caret>lace('a', 'b') }}")!!
        assertTrue("search param documented", doc.contains("search") && doc.contains("The substring to find."))
        assertTrue("replacement param documented", doc.contains("replacement"))
    }

    fun testNoParamModifierStillDocuments() {
        val doc = docFor("{{ title | up<caret>per }}")!!
        assertTrue("name shown", doc.contains("upper"))
        assertFalse("no Parameters block for a no-arg modifier", doc.contains("<b>Parameters</b>"))
    }
}
