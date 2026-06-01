package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersVariableNavTest : BasePlatformTestCase() {

    private fun setupBlueprint() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: Hero Title\n"
        )
    }

    fun testFieldGoToDef() {
        setupBlueprint()
        val text = "{{ hero_<caret>title }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertNotNull("blueprint field should resolve", target)
        assertEquals("blog.yaml", target!!.name)
    }

    fun testUnknownVariableNoResolve() {
        setupBlueprint()
        val text = "{{ totally_unkno<caret>wn }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        assertNull(file.findReferenceAt(caret)?.resolve())
    }

    fun testFieldDoc() {
        setupBlueprint()
        val text = "{{ hero_<caret>title }}"
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("mentions display: $doc", doc!!.contains("Hero Title"))
    }

    fun testSystemVarDoc() {
        val text = "{{ ur<caret>l }}"
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("mentions url description: $doc", doc!!.contains("URL"))
    }
}
