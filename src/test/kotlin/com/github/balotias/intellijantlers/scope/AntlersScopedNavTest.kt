package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersScopedNavTest : BasePlatformTestCase() {

    private fun setupTwoCollections() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: Blog Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: News Title\n"
        )
    }

    fun testScopedGoToDefPicksTheEnclosingCollection() {
        setupTwoCollections()
        val text = "{{ collection:news }}{{ ti<caret>tle }}{{ /collection }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertNotNull("title should resolve within scope", target)
        assertEquals("news.yaml", target!!.name)
    }

    fun testScopedDocNamesTheNamespace() {
        setupTwoCollections()
        val text = "{{ collection:news }}{{ ti<caret>tle }}{{ /collection }}"
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("doc names news collection: $doc", doc!!.contains("news"))
        assertTrue("doc shows the scoped display: $doc", doc.contains("News Title"))
    }
}
