package com.github.balotias.intellijantlers.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialParamDocTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject(
            "resources/views/components/_button.antlers.html",
            "{{#\n@param* label The caption label.\n@param as The wrapping element.\n" +
                "@deprecated old_icon Use the icon param instead.\n#}}\n<button></button>"
        )
    }

    private fun docAt(text: String): String? {
        setup()
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val element = file.findElementAt(caret) ?: file.findElementAt(caret - 1)!!
        return AntlersDocumentationProvider().generateDoc(element, element)
    }

    fun testOptionalParamDoc() {
        val doc = docAt("{{ partial:components/button a<caret>s=\"h2\" }}")
        assertNotNull("expected doc for partial param `as`", doc)
        assertTrue(doc!!.contains("The wrapping element."))
        assertTrue(doc.contains("optional"))
        assertTrue(doc.contains("_button.antlers.html"))
    }

    fun testRequiredParamDoc() {
        val doc = docAt("{{ partial:components/button l<caret>abel=\"Go\" }}")
        assertNotNull(doc)
        assertTrue(doc!!.contains("The caption label."))
        assertTrue(doc.contains("required"))
    }

    fun testUnknownParamNoDoc() {
        val doc = docAt("{{ partial:components/button no<caret>pe=\"x\" }}")
        assertNull("unknown partial param has no doc", doc)
    }

    fun testSrcFormParamDoc() {
        val doc = docAt("{{ partial src=\"components/button\" a<caret>s=\"h2\" }}")
        assertNotNull("expected doc for `as` in the src= include form", doc)
        assertTrue(doc!!.contains("The wrapping element."))
    }

    fun testDeprecatedParamDoc() {
        val doc = docAt("{{ partial:components/button old_i<caret>con=\"x\" }}")
        assertNotNull("expected doc for the deprecated param", doc)
        assertTrue("marked deprecated: $doc", doc!!.contains("deprecated"))
        assertTrue("shows the migration note: $doc", doc.contains("Use the icon param instead."))
    }
}
