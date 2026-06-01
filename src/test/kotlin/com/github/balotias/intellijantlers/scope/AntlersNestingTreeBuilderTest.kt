package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersNestingTreeBuilderTest : BasePlatformTestCase() {

    private fun tree(text: String): NestingTree {
        myFixture.configureByText("p.antlers.html", text)
        return AntlersNestingTreeBuilder.build(myFixture.file, project)
    }

    fun testNestedPair() {
        val t = tree("{{ collection:blog }}{{ if x }}{{ /if }}{{ /collection }}")
        assertEquals(1, t.roots.size)
        val collection = t.roots[0]
        assertEquals("collection", collection.name)
        assertNotNull("collection closed", collection.closer)
        assertEquals(1, collection.children.size)
        assertEquals("if", collection.children[0].name)
        assertNotNull("if closed", collection.children[0].closer)
        assertTrue(t.unmatchedClosers.isEmpty())
    }

    fun testUnmatchedCloser() {
        val t = tree("{{ /collection }}")
        assertTrue(t.roots.isEmpty())
        assertEquals(1, t.unmatchedClosers.size)
    }

    fun testUnclosedOpener() {
        val t = tree("{{ collection:blog }}")
        assertEquals(1, t.roots.size)
        assertNull("collection is unclosed", t.roots[0].closer)
    }

    fun testInnerUnmatchedDropped() {
        // {{ collection }}{{ if }}{{ /collection }} — the unclosed inner if is DROPPED when /collection
        // pops past it (matching the original folding/balance behavior: it had no closed children to
        // hoist, so it just vanishes — no fold, no warning, not a structure node).
        val t = tree("{{ collection:blog }}{{ if x }}{{ /collection }}")
        assertEquals(1, t.roots.size)
        assertNotNull(t.roots[0].closer)               // collection closed
        assertTrue("inner unclosed if dropped", t.roots[0].children.isEmpty())
        assertTrue(t.unmatchedClosers.isEmpty())
    }
}
