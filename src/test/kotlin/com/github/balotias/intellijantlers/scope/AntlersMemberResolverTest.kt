package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMemberResolverTest : BasePlatformTestCase() {

    fun testChildNamespaces() {
        val vf = myFixture.addFileToProject("dummy.yaml", "x").virtualFile
        val blog = BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog")

        val container = BlueprintField("hero", "", "group", vf, 0, blog)
        assertEquals(listOf(blog.copy(path = listOf("hero"))), AntlersMemberResolver.childNamespaces(container))

        val team = BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "team")
        val relation = BlueprintField("author", "", "entries", vf, 0, blog, listOf(team))
        assertEquals(listOf(team), AntlersMemberResolver.childNamespaces(relation))

        val plain = BlueprintField("title", "", "text", vf, 0, blog)
        assertTrue(AntlersMemberResolver.childNamespaces(plain).isEmpty())
    }
}
