package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersStatementsTest : BasePlatformTestCase() {

    fun testScopesAtMemoizedAtSameOffset() {
        val file = myFixture.configureByText(
            "p.antlers.html",
            "{{ collection:blog as=\"posts\" }}{{ posts:tit<caret>le }}{{ /collection }}"
        )
        val antlers = file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val el = antlers.findElementAt(myFixture.caretOffset)!!
        val a = AntlersScopeResolver.scopesAt(el)
        // Same offset within one modification generation -> the memoized List instance is reused.
        assertSame("scopesAt is memoized per caret offset", a, AntlersScopeResolver.scopesAt(el))
    }

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
