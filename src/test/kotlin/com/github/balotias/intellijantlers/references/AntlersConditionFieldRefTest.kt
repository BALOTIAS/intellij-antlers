package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A blueprint field used as the left-hand side of a tag query condition
 * (`{{ collection:blog title:contains="…" }}`) resolves to its blueprint declaration — so go-to-def,
 * Find Usages, and Rename work on it, even though the grammar parses it as a loose ident.
 */
class AntlersConditionFieldRefTest : BasePlatformTestCase() {

    private fun addBlueprint(handle: String) = myFixture.addFileToProject(
        "resources/blueprints/collections/blog/blog.yaml",
        "fields:\n  - handle: $handle\n    field:\n      type: text\n      display: X\n"
    )

    private fun addTemplate(text: String): PsiFile =
        myFixture.addFileToProject("resources/views/page.antlers.html", text)

    private fun declAt(file: PsiFile, caret: Int): AntlersFieldDeclaration? =
        file.findReferenceAt(caret)?.resolve() as? AntlersFieldDeclaration

    private fun commit() = PsiDocumentManager.getInstance(project).commitAllDocuments()

    fun testConditionFieldResolvesToBlueprint() {
        addBlueprint("title")
        val file = addTemplate("{{ collection:blog title:contains=\"x\" }}")
        val decl = declAt(file, file.text.indexOf("title"))
        assertNotNull("condition field resolves to its blueprint field", decl)
        assertEquals("title", decl!!.handle)
        assertEquals("blog.yaml", decl.containingFile?.name)
    }

    // The operator (`contains`) is not a field — it must not resolve to one.
    fun testOperatorDoesNotResolveToField() {
        addBlueprint("title")
        val file = addTemplate("{{ collection:blog title:contains=\"x\" }}")
        assertNull(declAt(file, file.text.indexOf("contains")))
    }

    fun testFindUsagesIncludesConditionField() {
        addBlueprint("title")
        val file = addTemplate("{{ collection:blog title:contains=\"x\" }}")
        val decl = declAt(file, file.text.indexOf("title"))!!
        val usages = myFixture.findUsages(decl)
        assertTrue("condition field is a usage: ${usages.map { it.element?.text }}", usages.isNotEmpty())
    }

    fun testRenameUpdatesConditionFieldAndYaml() {
        val bp = addBlueprint("title")
        val tmpl = addTemplate("{{ collection:blog title:contains=\"x\" }}")
        val decl = declAt(tmpl, tmpl.text.indexOf("title"))!!
        myFixture.renameElement(decl, "headline")
        commit()
        assertTrue("condition field renamed: ${tmpl.text}", tmpl.text.contains("headline:contains"))
        assertTrue("yaml handle renamed: ${bp.text}", bp.text.contains("handle: headline"))
    }
}
