package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace.Kind
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PageBlueprintResolverTest : BasePlatformTestCase() {

    /** Adds a blog config with [templateValue] + a blog/show view, returns namespaces at offset 0. */
    private fun namespacesFor(templateValue: String): List<BlueprintNamespace> {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: $templateValue\n")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", "{{ title }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(0)!!
        return PageBlueprintResolver.namespacesFor(el)
    }

    fun testSlashTemplateMatches() {
        assertEquals(listOf(BlueprintNamespace(Kind.COLLECTION, "blog")), namespacesFor("blog/show"))
    }

    fun testDotTemplateMatches() {
        assertEquals(listOf(BlueprintNamespace(Kind.COLLECTION, "blog")), namespacesFor("blog.show"))
    }

    fun testNoMatchIsEmpty() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: other/page\n")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", "{{ title }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(0)!!
        assertTrue(PageBlueprintResolver.namespacesFor(el).isEmpty())
    }
}
