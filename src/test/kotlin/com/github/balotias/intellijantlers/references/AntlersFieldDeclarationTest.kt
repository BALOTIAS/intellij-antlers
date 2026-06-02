package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFieldDeclarationTest : BasePlatformTestCase() {

    private fun addBlueprint(handle: String) = myFixture.addFileToProject(
        "resources/blueprints/collections/blog/blog.yaml",
        "fields:\n  - handle: $handle\n    field:\n      type: text\n      display: X\n"
    )

    private fun fieldDecl(handle: String): AntlersFieldDeclaration {
        val field = BlueprintService.getInstance(project).field(handle) ?: error("field not scanned")
        return AntlersFieldDeclaration(project, field)
    }

    fun testNameOffsetAndContainingFile() {
        val bp = addBlueprint("hero_title")
        val decl = fieldDecl("hero_title")
        assertEquals("hero_title", decl.name)
        assertEquals(bp.text.indexOf("hero_title"), decl.textOffset)
        assertEquals("blog.yaml", decl.containingFile?.name)
        assertTrue(decl.isValid)
    }

    fun testEquality() {
        addBlueprint("hero_title")
        assertEquals(fieldDecl("hero_title"), fieldDecl("hero_title"))
        assertTrue(fieldDecl("hero_title").isEquivalentTo(fieldDecl("hero_title")))
    }

    fun testSetNameEditsYaml() {
        val bp = addBlueprint("hero_title")
        val decl = fieldDecl("hero_title")
        WriteCommandAction.runWriteCommandAction(project) { decl.setName("hero_subtitle") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue("yaml handle renamed: ${bp.text}", bp.text.contains("handle: hero_subtitle"))
    }
}
