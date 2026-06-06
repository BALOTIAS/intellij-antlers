package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersInlineTagGotoDeclTest : BasePlatformTestCase() {

    // The scanner is text-based (no PHP plugin): a `.php` file whose path contains `/Tags/` with
    // `class ObfuscateLink extends Tags` maps handle `obfuscate_link` -> this class.
    private fun addTagClass() {
        myFixture.addFileToProject(
            "app/Tags/ObfuscateLink.php",
            "<?php\nnamespace App\\Tags;\nuse Statamic\\Tags\\Tags;\nclass ObfuscateLink extends Tags {}\n"
        )
    }

    private fun identNamed(text: String, name: String): PsiElement {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.collectElements(antlers) {
            it.text == name && it.references.isNotEmpty()
        }.firstOrNull()
            ?: PsiTreeUtil.collectElements(antlers) { it.text == name }.first()
    }

    private fun resolvedFileName(ident: PsiElement): String? =
        ident.references.firstNotNullOfOrNull { it.resolve() }?.containingFile?.name

    fun testInlineTagCallResolvesToPhpClass() {
        // addTagClass() must precede identNamed(): isInlineTagHead gates on catalog membership, and
        // obfuscate_link only becomes a known tag once its /Tags/ class is scanned/indexed.
        addTagClass()
        val ident = identNamed("{{ href = {obfuscate_link href=\"x\"} }}", "obfuscate_link")
        assertEquals("ObfuscateLink.php", resolvedFileName(ident))
    }

    fun testStandaloneTagStillResolves() {
        addTagClass()
        val ident = identNamed("{{ obfuscate_link href=\"x\" }}", "obfuscate_link")
        // Regression: the already-working standalone form.
        assertEquals("ObfuscateLink.php", resolvedFileName(ident))
    }

    fun testArrayKeyGetsNoPhpClassReference() {
        // collection is a bundled tag, but as an array key (followed by ':') it must get NO PHP-class reference.
        addTagClass()
        val ident = identNamed("{{ x = {collection: 'y'} }}", "collection")
        assertFalse(
            "array key must not get a PHP-class reference",
            ident.references.any { it is AntlersPhpClassReference }
        )
    }
}
