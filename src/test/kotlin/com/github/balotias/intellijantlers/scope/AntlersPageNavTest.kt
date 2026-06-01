package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPageNavTest : BasePlatformTestCase() {

    private fun setupTwoCollectionsBothTitle() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: Blog Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: News Title\n"
        )
    }

    fun testTopLevelResolvesToPageCollection() {
        setupTwoCollectionsBothTitle()
        val text = "{{ ti<caret>tle }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertNotNull("title resolves via page mapping", target)
        assertEquals("blog.yaml", target!!.name)
    }

    fun testTopLevelDocNamesCollection() {
        setupTwoCollectionsBothTitle()
        val text = "{{ ti<caret>tle }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("names blog collection: $doc", doc!!.contains("blog"))
        assertTrue("blog title display: $doc", doc.contains("Blog Title"))
    }

    fun testLoopStillWinsOverPageMapping() {
        setupTwoCollectionsBothTitle()
        val text = "{{ collection:news }}{{ ti<caret>tle }}{{ /collection }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertEquals("loop scope (news) wins over page (blog)", "news.yaml", target!!.name)
    }
}
