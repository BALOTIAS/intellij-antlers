package com.github.balotias.intellijantlers.view

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ViewFrontMatterServiceTest : BasePlatformTestCase() {

    fun testTopLevelNamesFromFrontMatter() {
        val file = myFixture.configureByText("p.antlers.html", "---\nfoo: bar\nbaz: qux\n---\n{{ view:foo }}")
        val names = ViewFrontMatterService.getInstance(project).topLevel(file).map { it.name }
        assertEquals(listOf("foo", "baz"), names)
    }

    fun testNoFrontMatterIsNull() {
        val file = myFixture.configureByText("p.antlers.html", "{{ title }}")
        assertNull(ViewFrontMatterService.getInstance(project).frontMatter(file))
        assertEquals(emptyList<FmEntry>(), ViewFrontMatterService.getInstance(project).topLevel(file))
    }
}
