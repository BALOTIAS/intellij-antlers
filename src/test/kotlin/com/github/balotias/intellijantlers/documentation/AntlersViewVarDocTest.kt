package com.github.balotias.intellijantlers.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersViewVarDocTest : BasePlatformTestCase() {

    fun testViewVarDocShowsNameAndValue() {
        myFixture.configureByText("p.antlers.html", "---\nfoo: bar\n---\n{{ view:fo<caret>o }}")
        val ident = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val doc = AntlersDocumentationProvider().generateDoc(ident, ident)
        assertNotNull("view:foo has documentation", doc)
        assertTrue("mentions it is a view variable, got: $doc", doc!!.contains("View variable"))
        assertTrue("shows the value, got: $doc", doc.contains("bar"))
    }
}
