package com.github.balotias.intellijantlers.completion

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersParamValueSourceTest : BasePlatformTestCase() {

    private fun setup(): PsiFile {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "<div>card</div>")
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n")
        myFixture.addFileToProject("resources/blueprints/taxonomies/topics/topics.yaml", "fields: []\n")
        return myFixture.addFileToProject("resources/views/page.antlers.html", "{{ }}")
    }

    private fun values(tag: String?, param: String?): List<String> {
        val el = setup().firstChild!!
        return AntlersParamValueSource.valuesFor(tag, param, el, project).map { it.text }
    }

    fun testPartialSrc() = assertTrue(values("partial", "src").contains("blog/card"))

    fun testCollectionFrom() {
        val v = values("collection", "from")
        assertTrue("blog: $v", v.contains("blog"))
        assertTrue("topics: $v", v.contains("topics"))
    }

    fun testSort() {
        val v = values("collection", "sort")
        assertTrue("title: $v", v.contains("title"))
        assertTrue("title:asc: $v", v.contains("title:asc"))
        assertTrue("date: $v", v.contains("date"))
    }

    fun testBoolean() = assertEquals(listOf("true", "false"), values("collection", "paginate"))

    fun testUnknownParamEmpty() = assertTrue(values("collection", "limit").isEmpty())
}
