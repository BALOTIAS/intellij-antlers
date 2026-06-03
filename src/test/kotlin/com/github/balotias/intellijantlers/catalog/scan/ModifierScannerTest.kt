package com.github.balotias.intellijantlers.catalog.scan

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModifierScannerTest : BasePlatformTestCase() {

    fun testScanFindsClassUnderModifiersDir() {
        myFixture.addFileToProject("app/Modifiers/ShoutLoud.php", "<?php\nclass ShoutLoud extends Modifier {}")
        assertTrue(ModifierScanner.scan(project).contains("shout_loud"))
    }

    fun testScanFindsFqnExtends() {
        myFixture.addFileToProject("app/Modifiers/Quiet.php", "<?php\nclass Quiet extends \\Statamic\\Modifiers\\Modifier {}")
        assertTrue(ModifierScanner.scan(project).contains("quiet"))
    }

    fun testScanIgnoresClassNotUnderModifiersDir() {
        myFixture.addFileToProject("app/Other/Nope.php", "<?php\nclass Nope extends Modifier {}")
        assertFalse(ModifierScanner.scan(project).contains("nope"))
    }

    fun testFindReturnsNavTarget() {
        myFixture.addFileToProject("app/Modifiers/ShoutLoud.php", "<?php\nclass ShoutLoud extends Modifier {}")
        val target = ModifierScanner.find(project, "shout_loud")
        assertNotNull(target)
        val text = VfsUtilCore.loadText(target!!.file)
        assertTrue(text.substring(target.offset).startsWith("ShoutLoud"))
    }
}
