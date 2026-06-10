package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.inspection.AntlersUnresolvedPartialInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCreatePartialFixTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(AntlersUnresolvedPartialInspection())
    }

    // An unresolved `{{ partial:… }}` offers a "Create partial" fix that creates the view file.
    fun testCreatePartialFix() {
        // An existing view establishes the resources/views root.
        myFixture.addFileToProject("resources/views/layout.antlers.html", "x")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", "{{ partial:blog/card }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val fixes = myFixture.getAllQuickFixes()
        val fix = fixes.firstOrNull { it.text.startsWith("Create partial") }
        assertNotNull("expected a create-partial fix: ${fixes.map { it.text }}", fix)
        myFixture.launchAction(fix!!)

        assertNotNull("partial file created under views",
            myFixture.findFileInTempDir("resources/views/blog/card.antlers.html"))
    }

    // A dynamic path (`{interpolation}`) can't be a static view — not flagged, no create fix.
    fun testDynamicPartialNotFlagged() {
        myFixture.addFileToProject("resources/views/layout.antlers.html", "x")
        val file = myFixture.addFileToProject(
            "resources/views/page.antlers.html", "{{ partial src=\"page_builder/{type}\" }}"
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val warnings = myFixture.doHighlighting().filter { (it.description ?: "").contains("Cannot resolve partial") }
        assertTrue("dynamic partial must not be flagged: ${warnings.map { it.description }}", warnings.isEmpty())
    }

    // A vendor-namespaced partial (`ns::path`) that isn't published locally is not flagged (it may live in
    // vendor/), and offers no "create" fix.
    fun testVendorNamespacePartialNotFlagged() {
        myFixture.addFileToProject("resources/views/layout.antlers.html", "x")
        val file = myFixture.addFileToProject(
            "resources/views/page.antlers.html", "{{ partial:statamic-peak-seo::snippets/seo }}"
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val warnings = myFixture.doHighlighting().filter { (it.description ?: "").contains("Cannot resolve partial") }
        assertTrue("vendor partial must not be flagged: ${warnings.map { it.description }}", warnings.isEmpty())
    }

    // A vendor partial published to resources/views/vendor/<ns>/… resolves (go-to-declaration).
    fun testVendorNamespacePartialResolvesToPublishedView() {
        myFixture.addFileToProject("resources/views/vendor/statamic-peak-seo/snippets/seo.antlers.html", "seo")
        val file = myFixture.addFileToProject(
            "resources/views/page.antlers.html", "{{ partial:statamic-peak-seo::snippets/seo }}"
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val resolved = file.findReferenceAt(file.text.indexOf("statamic-peak-seo"))?.resolve()
        assertNotNull("vendor partial resolves to its published view", resolved)
        assertEquals("seo.antlers.html", (resolved as? com.intellij.psi.PsiFile)?.name)
    }

    // A vendor partial that is NOT published resolves to the addon's own view under
    // vendor/<org>/<namespace>/resources/views/… (the namespace is the package dir name).
    fun testVendorPartialResolvesToAddonSource() {
        myFixture.addFileToProject(
            "vendor/studio1902/statamic-peak-seo/resources/views/snippets/seo.antlers.html", "seo"
        )
        val file = myFixture.addFileToProject(
            "resources/views/page.antlers.html", "{{ partial:statamic-peak-seo::snippets/seo }}"
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val resolved = file.findReferenceAt(file.text.indexOf("statamic-peak-seo"))?.resolve()
        assertNotNull("vendor partial resolves to the addon source", resolved)
        assertEquals("seo.antlers.html", (resolved as? com.intellij.psi.PsiFile)?.name)
    }

    // A partial that resolves is not flagged (no false "Cannot resolve" / create fix).
    fun testResolvedPartialNotFlagged() {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "card")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", "{{ partial:blog/card }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val warnings = myFixture.doHighlighting().filter { (it.description ?: "").contains("Cannot resolve partial") }
        assertTrue("a resolvable partial must not be flagged: ${warnings.map { it.description }}", warnings.isEmpty())
    }
}
