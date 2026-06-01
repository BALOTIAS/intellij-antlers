package com.github.balotias.intellijantlers.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersDocumentationProviderTest : BasePlatformTestCase() {

    private fun docAt(text: String): String? {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("t.antlers.html", text.replace("<caret>", ""))
        val element = myFixture.file.findElementAt(caret) ?: myFixture.file.findElementAt(caret - 1)!!
        return AntlersDocumentationProvider().generateDoc(element, element)
    }

    fun testTagDoc() {
        val doc = docAt("{{ colle<caret>ction }}")
        assertNotNull(doc)
        assertTrue("has description: $doc", doc!!.contains("collection"))
        assertTrue("has doc url: $doc", doc.contains("statamic.dev"))
    }

    fun testParameterDoc() {
        val doc = docAt("{{ collection li<caret>mit=\"5\" }}")
        assertNotNull(doc)
        assertTrue("mentions limit: $doc", doc!!.contains("limit"))
    }

    fun testModifierDoc() {
        val doc = docAt("{{ title | up<caret>per }}")
        assertNotNull(doc)
        assertTrue("mentions upper: $doc", doc!!.contains("upper"))
    }

    fun testNoDocForPlainVariable() {
        assertNull(docAt("{{ some_random_var<caret> }}"))
    }
}
