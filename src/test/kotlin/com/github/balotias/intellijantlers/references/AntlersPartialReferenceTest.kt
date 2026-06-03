package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialReferenceTest : BasePlatformTestCase() {

    private fun setupViews() {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "<div>card</div>")
    }

    private fun resolveAt(path: String, text: String): PsiFile? {
        setupViews()
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val ref = file.findReferenceAt(caret)
        return ref?.resolve() as? PsiFile
    }

    fun testResolveSrcForm() {
        val target = resolveAt("blog/card", "{{ partial:src=\"blog/c<caret>ard\" }}")
        assertNotNull("src= partial should resolve", target)
        assertEquals("card.antlers.html", target!!.name)
    }

    fun testResolveStaticSpaceForm() {
        // `{{ partial src="..." }}` (no colon) — a static `src` parameter resolves too.
        val target = resolveAt("blog/card", "{{ partial src=\"blog/c<caret>ard\" }}")
        assertNotNull("static src= partial should resolve", target)
        assertEquals("card.antlers.html", target!!.name)
    }

    fun testNonPartialSrcNotResolved() {
        // A `src=` on a non-partial tag must NOT get a partial reference.
        assertNull(resolveAt("blog/card", "{{ asset src=\"blog/c<caret>ard\" }}"))
    }

    fun testResolveMethodForm() {
        val target = resolveAt("blog/card", "{{ partial:blog/c<caret>ard }}")
        assertNotNull(":path partial should resolve", target)
        assertEquals("card.antlers.html", target!!.name)
    }

    fun testUnknownPartialUnresolved() {
        assertNull(resolveAt("blog/card", "{{ partial:src=\"no/su<caret>ch\" }}"))
    }

    private fun resolvePartialAt(text: String): PsiFile? {
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return file.findReferenceAt(caret)?.resolve() as? PsiFile
    }

    fun testResolvesShortNameFromPartialsFolder() {
        myFixture.addFileToProject("resources/views/partials/btn.antlers.html", "<button></button>")
        val t = resolvePartialAt("{{ partial:b<caret>tn }}")
        assertNotNull("partials/ short name should resolve", t)
        assertEquals("btn.antlers.html", t!!.name)
        assertEquals("resolved from the partials folder", "partials", t.virtualFile.parent.name)
    }

    fun testPartialsFolderWinsOnNameClash() {
        myFixture.addFileToProject("resources/views/btn.antlers.html", "root")
        myFixture.addFileToProject("resources/views/partials/btn.antlers.html", "in-partials")
        val t = resolvePartialAt("{{ partial:b<caret>tn }}")
        assertNotNull(t)
        assertEquals("partials/ wins over views root", "partials", t!!.virtualFile.parent.name)
    }

    fun testRootFallbackStillResolves() {
        myFixture.addFileToProject("resources/views/btn.antlers.html", "root")
        val t = resolvePartialAt("{{ partial:b<caret>tn }}")
        assertNotNull(t)
        assertEquals("falls back to views root", "views", t!!.virtualFile.parent.name)
    }

    fun testCompletionOffersPartialsFolderShortName() {
        myFixture.addFileToProject("resources/views/partials/btn.antlers.html", "x")
        myFixture.addFileToProject("resources/views/partials/card.antlers.html", "x")
        myFixture.configureByText("page.antlers.html", "{{ partial:src=\"<caret>\" }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue("offers btn (short name): $variants", variants.contains("btn"))
        assertFalse("not the partials/ path form: $variants", variants.contains("partials/btn"))
    }

    fun testCompletionListsPartials() {
        setupViews()
        myFixture.configureByText("page.antlers.html", "{{ partial:src=\"<caret>\" }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue("offers blog/card: $variants", variants.any { it.contains("blog/card") })
    }
}
