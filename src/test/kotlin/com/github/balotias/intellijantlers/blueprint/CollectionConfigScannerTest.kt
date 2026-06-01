package com.github.balotias.intellijantlers.blueprint

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CollectionConfigScannerTest : BasePlatformTestCase() {

    fun testExtractsHandleAndTemplate() {
        myFixture.addFileToProject("content/collections/blog.yaml", "title: Blog\ntemplate: blog/show\n")
        val blog = CollectionConfigScanner.scan(project).firstOrNull { it.handle == "blog" }
        assertNotNull("blog config found", blog)
        assertEquals("blog/show", blog!!.template)
    }

    fun testIgnoresConfigWithoutTemplate() {
        myFixture.addFileToProject("content/collections/pages.yaml", "title: Pages\n")
        assertTrue("no entry for template-less config", CollectionConfigScanner.scan(project).none { it.handle == "pages" })
    }

    fun testIgnoresEntryFilesInSubdirectory() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        myFixture.addFileToProject("content/collections/blog/some-entry.yaml", "template: should/ignore\n")
        val configs = CollectionConfigScanner.scan(project)
        assertEquals("only the top-level config", 1, configs.count { it.handle == "blog" })
        assertTrue("entry file ignored", configs.none { it.template == "should/ignore" })
    }

    fun testStripsQuotes() {
        myFixture.addFileToProject("content/collections/news.yaml", "template: \"news/show\"\n")
        assertEquals("news/show", CollectionConfigScanner.scan(project).first { it.handle == "news" }.template)
    }
}
