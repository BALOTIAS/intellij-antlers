package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionGapsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The handle listers derive `resources/` from `resources/views`; a real project always has it.
        myFixture.addFileToProject("resources/views/_layout.antlers.html", "")
    }

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testCollectionColonOffersHandles() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", "title: Blog\n")
        val l = lookups("{{ collection:<caret> }}")
        assertTrue("offers collection handle blog: $l", l.contains("blog"))
        assertTrue("still offers a method (count): $l", l.contains("count"))
    }

    fun testTaxonomyColonOffersHandles() {
        myFixture.addFileToProject("resources/blueprints/taxonomies/tags/tags.yaml", "title: Tags\n")
        assertTrue(lookups("{{ taxonomy:<caret> }}").contains("tags"))
    }

    fun testFormColonOffersHandles() {
        myFixture.addFileToProject("resources/blueprints/forms/contact.yaml", "title: Contact\n")
        assertTrue(lookups("{{ form:<caret> }}").contains("contact"))
    }

    fun testFormScopeOffersFormVars() {
        val l = lookups("{{ form:contact }}{{ <caret> }}{{ /form:contact }}")
        assertTrue("success: $l", l.contains("success"))
        assertTrue("errors: $l", l.contains("errors"))
        assertTrue("fields: $l", l.contains("fields"))
    }

    fun testNonFormScopeHasNoFormVars() {
        val l = lookups("{{ collection:blog }}{{ <caret> }}{{ /collection }}")
        assertFalse("no form var in a collection scope: $l", l.contains("success"))
        assertFalse("no form var in a collection scope: $l", l.contains("submission_created"))
    }
}
