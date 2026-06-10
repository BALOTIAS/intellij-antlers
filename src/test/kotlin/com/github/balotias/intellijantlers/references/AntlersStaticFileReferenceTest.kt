package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Go-to-declaration for a literal media path written in an Antlers expression — glide `src`, asset `url`,
 * or a bare path string — resolving to the file under `public/` (and the default `public/assets/`).
 */
class AntlersStaticFileReferenceTest : BasePlatformTestCase() {

    private fun resolvedName(filePath: String, template: String, token: String): String? {
        myFixture.addFileToProject(filePath, "binary")
        myFixture.addFileToProject("resources/views/layout.antlers.html", "x")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", template)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val resolved = file.findReferenceAt(file.text.indexOf(token))?.resolve()
        return (resolved as? PsiFile)?.name
    }

    fun testGlideLiteralSrcResolvesToPublic() =
        assertEquals("hero.jpg", resolvedName("public/img/hero.jpg", "{{ glide src=\"/img/hero.jpg\" }}", "img/hero.jpg"))

    fun testBarePathStringResolves() =
        assertEquals("logo.png", resolvedName("public/img/logo.png", "{{ \"/img/logo.png\" }}", "img/logo.png"))

    // Asset container paths default to public/assets/<path>.
    fun testAssetUrlResolvesUnderPublicAssets() =
        assertEquals("x.webp", resolvedName("public/assets/photos/x.webp", "{{ asset url=\"photos/x.webp\" }}", "photos/x.webp"))

    // A non-path string is not turned into a (broken) file reference.
    fun testNonPathStringNotResolved() {
        myFixture.addFileToProject("resources/views/layout.antlers.html", "x")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", "{{ \"hello world\" }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        assertNull(file.findReferenceAt(file.text.indexOf("hello"))?.resolve())
    }
}
