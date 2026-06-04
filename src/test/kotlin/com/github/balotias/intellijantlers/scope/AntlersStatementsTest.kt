package com.github.balotias.intellijantlers.scope

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersStatementsTest : BasePlatformTestCase() {

    fun testSortedAndCachedWithinOneModification() {
        val file = myFixture.configureByText("p.antlers.html", "{{ a }}{{ if x }}{{ b }}{{ /if }}")
        val first = AntlersStatements.sortedIn(file)
        assertTrue("has statements", first.isNotEmpty())
        assertEquals("sorted by start offset", first.map { it.textRange.startOffset },
            first.map { it.textRange.startOffset }.sorted())
        // Cached: same List instance until the PSI changes.
        assertSame("second call returns the cached instance", first, AntlersStatements.sortedIn(file))
    }

    fun testRefreshesAfterEdit() {
        myFixture.configureByText("p.antlers.html", "{{ a }}<caret>")
        val file = myFixture.file
        val before = AntlersStatements.sortedIn(file).size
        myFixture.type("{{ b }}")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("the new statement is picked up after the edit", before + 1, AntlersStatements.sortedIn(file).size)
    }
}
