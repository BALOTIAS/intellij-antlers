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

    fun testCustomModifierResolves() {
        myFixture.addFileToProject(
            "app/Modifiers/MyFormat.php",
            "<?php\nnamespace App\\Modifiers;\nuse Statamic\\Modifiers\\Modifier;\nclass MyFormat extends Modifier {}\n"
        )
        val text = "{{ title | my_for<caret>mat }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("mpage.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile
        assertNotNull("custom modifier should resolve to its PHP file", target)
        assertEquals("MyFormat.php", target!!.name)
    }
}
