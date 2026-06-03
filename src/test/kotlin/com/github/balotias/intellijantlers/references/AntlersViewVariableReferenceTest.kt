package com.github.balotias.intellijantlers.references

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersViewVariableReferenceTest : BasePlatformTestCase() {

    fun testViewVarResolvesToFrontMatterKey() {
        myFixture.configureByText("p.antlers.html", "---\nfoo: bar\n---\n{{ view:fo<caret>o }}")
        val ref = myFixture.getReferenceAtCaretPosition()
        assertNotNull("a reference is attached to view:foo", ref)
        val target = ref!!.resolve()
        assertNotNull("view:foo resolves", target)
        assertEquals("lands on the `foo:` key (offset 4)", 4, target!!.textOffset)
    }

    fun testUndefinedViewVarUnresolved() {
        myFixture.configureByText("p.antlers.html", "---\nfoo: bar\n---\n{{ view:no<caret>pe }}")
        val ref = myFixture.getReferenceAtCaretPosition()
        assertNull("undefined key does not resolve", ref?.resolve())
    }
}
