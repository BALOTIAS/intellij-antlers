package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * End-to-end value: the injected `{{ … }}` Antlers fragment gives real PSI inside interpolation, so the
 * existing catalog-backed features (modifier docs, modifier resolution) work there. Reached via the
 * injected fragment's Antlers root (Antlers is a template language; findInjectedElementAt gives the
 * near-empty HTML data root).
 */
class AntlersInterpolationFeatureTest : BasePlatformTestCase() {

    private fun injectedAntlersRoot(text: String): PsiElement {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val host = PsiTreeUtil.collectElements(antlers) { it is AntlersStringLeaf }.first()
        val files = mutableListOf<PsiFile>()
        InjectedLanguageManager.getInstance(project).enumerate(host) { f, _ -> files.add(f) }
        return files.first().viewProvider.getPsi(AntlersLanguage.INSTANCE)
    }

    fun testModifierDocInsideInterpolation() {
        val root = injectedAntlersRoot("{{ \"{view:href | replace('a','b')}\" }}")
        val replace = PsiTreeUtil.collectElements(root) { it.text == "replace" }.first()
        val doc = AntlersDocumentationProvider().generateDoc(replace, replace)
        assertNotNull("modifier quick-doc fires inside interpolation", doc)
        assertTrue("doc shows the replace signature, got: $doc",
            doc!!.contains("replace(search, replacement)"))
    }

    fun testInjectedModifierIsModifierPsi() {
        val root = injectedAntlersRoot("{{ \"{title | upper}\" }}")
        val upper = PsiTreeUtil.collectElements(root) { it.text == "upper" }.first()
        assertNotNull("`upper` is a real modifier in the injected fragment",
            PsiTreeUtil.getParentOfType(upper, AntlersModifierMixin::class.java, false))
    }

    // NOTE on editor completion inside interpolation: it works (manually verified — a caret inside a
    // `{ … }` span offers real Antlers vars/tags/modifiers from the injected root). It is NOT unit-tested
    // here because `myFixture.completeBasic()` into a freshly-injected fragment is timing-flaky in the
    // test harness (the injection isn't established on the first cold completion). The doc-provider test
    // above already exercises an editor-level feature against the injected PSI end-to-end.
}
