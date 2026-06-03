package com.github.balotias.intellijantlers.catalog.scan

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TagScannerTest : BasePlatformTestCase() {

    fun testScanFindsClassUnderTagsDir() {
        myFixture.addFileToProject("app/Tags/FooBar.php", "<?php\nclass FooBar extends Tags {}")
        assertTrue(TagScanner.scan(project).contains("foo_bar"))
    }

    fun testScanFindsFqnExtends() {
        myFixture.addFileToProject("app/Tags/Baz.php", "<?php\nclass Baz extends \\Statamic\\Tags\\Tags {}")
        assertTrue(TagScanner.scan(project).contains("baz"))
    }

    fun testScanIgnoresClassNotUnderTagsDir() {
        myFixture.addFileToProject("app/Other/Nope.php", "<?php\nclass Nope extends Tags {}")
        assertFalse(TagScanner.scan(project).contains("nope"))
    }

    fun testCamelToSnake() {
        assertEquals("foo_bar", TagScanner.camelToSnake("FooBar"))
        // No lowercase-then-uppercase boundary, so it stays a single lowercased token.
        assertEquals("foo", TagScanner.camelToSnake("Foo"))
    }

    fun testFindReturnsNavTarget() {
        myFixture.addFileToProject("app/Tags/FooBar.php", "<?php\nclass FooBar extends Tags {}")
        val target = TagScanner.find(project, "foo_bar")
        assertNotNull(target)
        val text = VfsUtilCore.loadText(target!!.file)
        assertTrue(text.substring(target.offset).startsWith("FooBar"))
    }
}
