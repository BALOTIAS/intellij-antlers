package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Go-to-declaration for the Statamic `svg` tag — `{{ svg:trash }}` / `{{ svg src="trash" }}` resolves to
 * the SVG file through Statamic's path cascade (resources/svg → resources → public/svg → public).
 */
class AntlersSvgReferenceTest : BasePlatformTestCase() {

    private fun resolvedName(svgPath: String, template: String, token: String): String? {
        myFixture.addFileToProject(svgPath, "<svg></svg>")
        myFixture.addFileToProject("resources/views/layout.antlers.html", "x")  // establishes the views root
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", template)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val resolved = file.findReferenceAt(file.text.indexOf(token))?.resolve()
        return (resolved as? PsiFile)?.name
    }

    fun testColonShorthandResolves() =
        assertEquals("trash.svg", resolvedName("resources/svg/trash.svg", "{{ svg:trash }}", "trash"))

    fun testSrcParamResolves() =
        assertEquals("trash.svg", resolvedName("resources/svg/trash.svg", "{{ svg src=\"trash\" }}", "trash"))

    // `.svg` already present in the name is not doubled.
    fun testSrcWithExtensionResolves() =
        assertEquals("trash.svg", resolvedName("resources/svg/trash.svg", "{{ svg src=\"trash.svg\" }}", "trash"))

    // The cascade falls through to public/svg.
    fun testPublicSvgResolves() =
        assertEquals("logo.svg", resolvedName("public/svg/logo.svg", "{{ svg:logo }}", "logo"))

    // A nested path in the src value resolves under resources/svg.
    fun testNestedPathResolves() =
        assertEquals("trash.svg", resolvedName("resources/svg/icons/trash.svg", "{{ svg src=\"icons/trash\" }}", "icons/trash"))

    fun testUnresolvedSvgIsNull() {
        myFixture.addFileToProject("resources/views/layout.antlers.html", "x")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", "{{ svg:missing }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        assertNull(file.findReferenceAt(file.text.indexOf("missing"))?.resolve())
    }
}
