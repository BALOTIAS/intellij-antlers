package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.inspection.AntlersUnresolvedPartialInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCreatePartialFixTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(AntlersUnresolvedPartialInspection())
    }

    // An unresolved `{{ partial:… }}` offers a "Create partial" fix that creates the view file.
    fun testCreatePartialFix() {
        // An existing view establishes the resources/views root.
        myFixture.addFileToProject("resources/views/layout.antlers.html", "x")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", "{{ partial:blog/card }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val fixes = myFixture.getAllQuickFixes()
        val fix = fixes.firstOrNull { it.text.startsWith("Create partial") }
        assertNotNull("expected a create-partial fix: ${fixes.map { it.text }}", fix)
        myFixture.launchAction(fix!!)

        assertNotNull("partial file created under views",
            myFixture.findFileInTempDir("resources/views/blog/card.antlers.html"))
    }

    // A partial that resolves is not flagged (no false "Cannot resolve" / create fix).
    fun testResolvedPartialNotFlagged() {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "card")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", "{{ partial:blog/card }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val warnings = myFixture.doHighlighting().filter { (it.description ?: "").contains("Cannot resolve partial") }
        assertTrue("a resolvable partial must not be flagged: ${warnings.map { it.description }}", warnings.isEmpty())
    }
}
