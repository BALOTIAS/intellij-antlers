package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersInterpolationInjectionTest : BasePlatformTestCase() {

    /** The injected PSI element at the offset of [marker] inside [text], or null if none. */
    private fun injectedAt(text: String, marker: String): com.intellij.psi.PsiElement? {
        myFixture.configureByText("p.antlers.html", text)
        val offset = text.indexOf(marker)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return InjectedLanguageManager.getInstance(project).findInjectedElementAt(antlers, offset)
    }

    fun testInterpolationIsInjected() {
        assertNotNull("expression inside {…} should be injected",
            injectedAt("{{ \"{title | upper}\" }}", "title"))
    }

    fun testPlainStringNotInjected() {
        assertNull("a string without interpolation injects nothing",
            injectedAt("{{ \"hello world\" }}", "hello"))
    }

    fun testInjectedFragmentParsesAsAntlers() {
        // Antlers is a TEMPLATE language (Antlers root + HTML data root), so the injected fragment is
        // reached via its Antlers root, not findInjectedElementAt (which yields the near-empty HTML root).
        myFixture.configureByText("p.antlers.html", "{{ \"{title | upper}\" }}")
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val host = PsiTreeUtil.collectElements(antlers) { it is AntlersStringLeaf }.first()
        val files = mutableListOf<com.intellij.psi.PsiFile>()
        InjectedLanguageManager.getInstance(project).enumerate(host) { f, _ -> files.add(f) }
        assertEquals("one injected fragment", 1, files.size)
        val root = files[0].viewProvider.getPsi(AntlersLanguage.INSTANCE)
        assertEquals("injected fragment is Antlers", AntlersLanguage.INSTANCE, root.language)
        assertEquals("wrapped as a tag expression", "{{ title | upper }}", root.text)
        val upper = PsiTreeUtil.collectElements(root) { it.text == "upper" }.first()
        assertNotNull("`upper` is a real modifier in the injected Antlers PSI",
            PsiTreeUtil.getParentOfType(upper, AntlersModifierMixin::class.java, false))
    }

    fun testTwoInterpolationsInjectedIndependently() {
        assertNotNull(injectedAt("{{ \"a {one} b {two} c\" }}", "one"))
        assertNotNull(injectedAt("{{ \"a {one} b {two} c\" }}", "two"))
        assertNull("plain text between interpolations is not injected",
            injectedAt("{{ \"a {one} b {two} c\" }}", " b "))
    }

    fun testEmptyInterpolationNotInjected() {
        assertNull(injectedAt("{{ \"x {} y\" }}", "} y"))
    }
}
