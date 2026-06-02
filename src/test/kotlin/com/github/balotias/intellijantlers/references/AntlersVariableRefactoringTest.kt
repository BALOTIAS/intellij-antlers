package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersVariableRefactoringTest : BasePlatformTestCase() {

    private fun addBlueprint(handle: String) = myFixture.addFileToProject(
        "resources/blueprints/collections/blog/blog.yaml",
        "fields:\n  - handle: $handle\n    field:\n      type: text\n      display: X\n"
    )

    private fun addTemplate(name: String, text: String): PsiFile =
        myFixture.addFileToProject("resources/views/$name.antlers.html", text)

    private fun declAt(file: PsiFile, caret: Int): AntlersFieldDeclaration =
        file.findReferenceAt(caret)?.resolve() as? AntlersFieldDeclaration
            ?: error("no AntlersFieldDeclaration at $caret")

    fun testResolvesToDeclarationWithPreciseOffset() {
        val bp = addBlueprint("hero_title")
        val file = addTemplate("page", "{{ hero_title }}")
        val decl = declAt(file, "{{ hero_title }}".indexOf("hero"))
        assertEquals("hero_title", decl.handle)
        assertEquals(bp.text.indexOf("hero_title"), decl.textOffset)
        assertEquals("blog.yaml", decl.containingFile?.name)
    }

    fun testIsReferenceToMatchesSameHandle() {
        addBlueprint("hero_title")
        val a = addTemplate("a", "{{ hero_title }}")
        val b = addTemplate("b", "{{ hero_title }}")
        val declA = declAt(a, "{{ hero_title }}".indexOf("hero"))
        val refB = b.findReferenceAt("{{ hero_title }}".indexOf("hero"))!!
        assertTrue(refB.isReferenceTo(declA))
    }

    fun testUnknownHandleStillNull() {
        addBlueprint("hero_title")
        val file = addTemplate("c", "{{ totally_unknown }}")
        assertNull(file.findReferenceAt("{{ totally_unknown }}".indexOf("tot"))?.resolve())
    }

    fun testFindUsagesTopLevelField() {
        addBlueprint("hero_title")
        val file = addTemplate("page", "{{ hero_title }}")
        val decl = declAt(file, "{{ hero_title }}".indexOf("hero"))
        val usages = myFixture.findUsages(decl)
        assertTrue("expected a usage: ${usages.map { it.element?.text }}", usages.isNotEmpty())
    }

    fun testFindUsagesDistinctByHandle() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n" +
                "  - handle: subtitle\n    field:\n      type: text\n"
        )
        val file = addTemplate("page", "{{ title }} {{ subtitle }}")
        val titleDecl = declAt(file, "{{ title }}".indexOf("title"))
        val usages = myFixture.findUsages(titleDecl)
        assertEquals("only the title usage, not subtitle: ${usages.map { it.element?.text }}",
            1, usages.size)
    }
}
