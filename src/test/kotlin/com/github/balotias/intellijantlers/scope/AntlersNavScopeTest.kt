package com.github.balotias.intellijantlers.scope

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersNavScopeTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject(
            "resources/blueprints/navigation/main.yaml",
            "fields:\n  - handle: link_text\n    field:\n      type: text\n      display: Link Text\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: Blog Title\n"
        )
    }

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testNavOffersFieldsMetaAndLoop() {
        setup()
        val l = lookups("{{ nav:main }}{{ <caret> }}{{ /nav }}")
        assertTrue("nav blueprint field: $l", l.contains("link_text"))
        assertTrue("nav-only meta is_external: $l", l.contains("is_external"))
        assertTrue("loop var index: $l", l.contains("index"))
    }

    fun testNavCollectionOffersCollectionFields() {
        setup()
        val l = lookups("{{ nav:collection:blog }}{{ <caret> }}{{ /nav }}")
        assertTrue("collection field via nav: $l", l.contains("title"))
        assertTrue("nav-meta still offered: $l", l.contains("has_entries"))
    }

    fun testCollectionLoopHasNoNavMeta() {
        setup()
        val l = lookups("{{ collection:blog }}{{ <caret> }}{{ /collection }}")
        assertTrue("loop var present: $l", l.contains("index"))
        assertFalse("no nav-only meta in a plain collection loop: $l", l.contains("is_external"))
    }

    fun testNavFieldGoToDef() {
        setup()
        val text = "{{ nav:collection:blog }}{{ ti<caret>tle }}{{ /nav }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertEquals("blog.yaml", target!!.name)
    }
}
