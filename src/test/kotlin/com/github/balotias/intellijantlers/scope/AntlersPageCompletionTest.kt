package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPageCompletionTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: Hero Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - handle: news_only\n    field:\n      type: text\n"
        )
    }

    private fun lookupsAt(path: String, textWithCaret: String): List<String> {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject(path, textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testTopLevelRestrictsToPageCollection() {
        setup()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ <caret> }}")
        assertTrue("page field hero_title: $l", l.contains("hero_title"))
        assertFalse("not another collection's field: $l", l.contains("news_only"))
        assertFalse("no loop vars on a page: $l", l.contains("index"))
        assertTrue("still offers tags: $l", l.contains("collection"))
    }

    fun testUnmappedFileFallsBackToGlobal() {
        setup()
        val l = lookupsAt("resources/views/blog/unmapped.antlers.html", "{{ <caret> }}")
        assertTrue("global includes hero_title: $l", l.contains("hero_title"))
        assertTrue("global includes news_only: $l", l.contains("news_only"))
    }
}
