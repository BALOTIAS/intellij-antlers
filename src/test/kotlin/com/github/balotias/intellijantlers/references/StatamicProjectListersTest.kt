package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StatamicProjectListersTest : BasePlatformTestCase() {

    private fun template(): PsiFile {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "<div>card</div>")
        myFixture.addFileToProject("resources/views/shared/hero.antlers.html", "<div>hero</div>")
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", "fields: []\n")
        myFixture.addFileToProject("resources/blueprints/collections/news/news.yaml", "fields: []\n")
        myFixture.addFileToProject("resources/blueprints/taxonomies/topics/topics.yaml", "fields: []\n")
        return myFixture.addFileToProject("resources/views/page.antlers.html", "{{ }}")
    }

    fun testListPartials() {
        val el = template().firstChild!!
        val partials = StatamicProject.listPartials(el)
        assertTrue("blog/card: $partials", partials.contains("blog/card"))
        assertTrue("shared/hero: $partials", partials.contains("shared/hero"))
    }

    fun testListCollectionHandles() {
        val el = template().firstChild!!
        val handles = StatamicProject.listCollectionHandles(el)
        assertTrue("blog: $handles", handles.contains("blog"))
        assertTrue("news: $handles", handles.contains("news"))
    }

    fun testListTaxonomyHandles() {
        val el = template().firstChild!!
        assertTrue(StatamicProject.listTaxonomyHandles(el).contains("topics"))
    }
}
