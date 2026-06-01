package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersDefinitionReferenceTest : BasePlatformTestCase() {

    private fun setupPhpTag(className: String, snakeName: String): PsiFile {
        return myFixture.addFileToProject(
            "app/Tags/$className.php",
            """<?php
namespace App\Tags;
use Statamic\Tags\Tags;
class $className extends Tags
{
    public function index() {}
}
"""
        )
    }

    private fun setupPhpModifier(className: String): PsiFile {
        val dollar = "$"
        return myFixture.addFileToProject(
            "app/Modifiers/$className.php",
            """<?php
namespace App\Modifiers;
use Statamic\Modifiers\Modifier;
class $className extends Modifier
{
    public function index(${dollar}value) { return ${dollar}value; }
}
"""
        )
    }

    private fun resolveTagAt(antlersText: String): PsiFile? {
        val caret = antlersText.indexOf("<caret>")
        val file = myFixture.addFileToProject(
            "resources/views/page.antlers.html",
            antlersText.replace("<caret>", "")
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return file.findReferenceAt(caret)?.resolve()?.containingFile
    }

    fun testCustomTagResolves() {
        setupPhpTag("MyThing", "my_thing")
        val resolved = resolveTagAt("{{ my_<caret>thing }}")
        assertNotNull("Custom tag should resolve to its PHP class file", resolved)
        assertEquals("MyThing.php", resolved!!.name)
    }

    fun testNativeTagNotResolved() {
        // Native tag "collection" has no PHP class in the project, so resolve returns null
        val resolved = resolveTagAt("{{ collec<caret>tion }}")
        assertNull("Native tag should not resolve (soft reference)", resolved)
    }

    fun testUnknownNameNotResolved() {
        val resolved = resolveTagAt("{{ totally_unkn<caret>own_tag }}")
        assertNull("Unknown tag should not resolve", resolved)
    }
}
