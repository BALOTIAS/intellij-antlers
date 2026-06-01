package com.github.balotias.intellijantlers.structure

import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersStructureViewTest : BasePlatformTestCase() {

    private fun rootChildren(text: String): List<StructureViewTreeElement> {
        myFixture.configureByText("p.antlers.html", text)
        return AntlersStructureViewModel(myFixture.file).root.children
            .toList().filterIsInstance<StructureViewTreeElement>()
    }

    private fun label(e: StructureViewTreeElement) = e.presentation.presentableText ?: ""

    fun testPairedTagWithNestedCondition() {
        val children = rootChildren("{{ collection:blog }}{{ if x }}{{ /if }}{{ /collection }}")
        val collection = children.firstOrNull { label(it) == "collection:blog" }
        assertNotNull("top-level collection:blog: ${children.map { label(it) }}", collection)
        val childLabels = collection!!.children.toList()
            .filterIsInstance<StructureViewTreeElement>().map { label(it) }
        assertTrue("nested if: $childLabels", childLabels.any { it == "if" })
    }

    fun testPartialLeaf() {
        val labels = rootChildren("{{ partial:src=\"blog/card\" }}").map { label(it) }
        assertTrue("partial leaf: $labels", labels.any { it.contains("partial") && it.contains("blog/card") })
    }

    fun testPlainVariableNotShown() {
        val labels = rootChildren("{{ title }}").map { label(it) }
        assertTrue("no plain variable nodes: $labels", labels.none { it == "title" })
    }

    fun testValueIsOpenerStatement() {
        val node = rootChildren("{{ collection:blog }}{{ /collection }}")
            .first { label(it) == "collection:blog" }
        assertTrue(node.value is AntlersStatement)
    }
}
