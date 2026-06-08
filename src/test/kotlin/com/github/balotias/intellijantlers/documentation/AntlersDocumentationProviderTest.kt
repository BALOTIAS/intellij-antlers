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

    // `{{ switch between=… }}` is the cycling tag — hover shows the tag docs.
    fun testSwitchTagDoc() {
        val doc = docAt("{{ swi<caret>tch between='a|b' }}")
        assertNotNull(doc)
        assertTrue("is the tag doc: $doc", doc!!.contains("tags/switch"))
    }

    // `switch(…)` is the inline operator, NOT the tag — hover must not show the tag docs.
    fun testSwitchOperatorDocIsNotTagDoc() {
        val doc = docAt("{{ swi<caret>tch((s == 'md') => 'a', () => 'b') }}")
        assertNotNull("the operator should have its own doc: $doc", doc)
        assertFalse("must not be the switch *tag* doc: $doc", doc!!.contains("tags/switch"))
        assertTrue("describes the operator: $doc", doc.contains("operator"))
        assertTrue("links the language docs: $doc", doc.contains("frontend/antlers"))
    }

    fun testHoverResolvesModifierDocElement() {
        val text = "{{ title | up<caret>per }}"
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("t.antlers.html", text.replace("<caret>", ""))
        val provider = AntlersDocumentationProvider()
        val ctx = myFixture.file.findElementAt(caret)
        val el = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, ctx, caret)
        assertNotNull("hover must resolve a documentation element on the modifier", el)
        val doc = provider.generateDoc(el!!, el)
        assertNotNull(doc)
        assertTrue("mentions upper: $doc", doc!!.contains("upper"))
        assertTrue("links to docs: $doc", doc.contains("statamic.dev"))
    }

    fun testHoverNoDocElementOnNonIdent() {
        val text = "{{ title <caret>| upper }}"
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("t.antlers.html", text.replace("<caret>", ""))
        val provider = AntlersDocumentationProvider()
        val ctx = myFixture.file.findElementAt(caret)
        assertNull(provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, ctx, caret))
    }
}
