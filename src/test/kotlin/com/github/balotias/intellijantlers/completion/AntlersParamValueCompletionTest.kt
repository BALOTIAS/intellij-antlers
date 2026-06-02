package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersParamValueCompletionTest : BasePlatformTestCase() {

    private fun blueprints() {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "<div>card</div>")
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n")
    }

    private fun complete(text: String): List<String> {
        blueprints()
        myFixture.configureByText("page.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testPartialSrcValue() =
        assertTrue(complete("{{ partial:src=\"<caret>\" }}").contains("blog/card"))

    fun testCollectionFromValue() =
        assertTrue(complete("{{ collection from=\"<caret>\" }}").contains("blog"))

    fun testSortValue() {
        val v = complete("{{ collection:blog sort=\"<caret>\" }}")
        assertTrue("title: $v", v.contains("title"))
        assertTrue("title:asc: $v", v.contains("title:asc"))
    }

    fun testBooleanValue() {
        val v = complete("{{ collection paginate=\"<caret>\" }}")
        assertTrue("true: $v", v.contains("true"))
        assertTrue("false: $v", v.contains("false"))
    }

    fun testNoParamValuesAtTagHead() {
        val v = complete("{{ <caret> }}")
        assertTrue("collection tag offered: $v", v.contains("collection"))
        assertFalse("no stray partial path at tag head: $v", v.contains("blog/card"))
    }
}
