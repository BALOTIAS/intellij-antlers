package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersInterpolationScopeTest : BasePlatformTestCase() {

    private fun blueprint(handle: String, field: String) {
        myFixture.addFileToProject(
            "resources/blueprints/collections/$handle/$handle.yaml",
            "fields:\n  - handle: $field\n    field:\n      type: text\n      display: X\n"
        )
    }

    /** An element INSIDE the injected interpolation fragment whose host string contains [marker]. */
    private fun injectedElement(text: String, marker: String): PsiElement {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val host = PsiTreeUtil.collectElements(antlers) {
            it is AntlersStringLeaf && it.text.contains(marker)
        }.first()
        val files = mutableListOf<PsiFile>()
        InjectedLanguageManager.getInstance(project).enumerate(host) { f, _ -> files.add(f) }
        val root = files.first().viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.collectElements(root) { it.text == marker }.first()
    }

    // The interpolation host is a T_STRING inside `{{ }}` (e.g. a tag param value or a bare string
    // expression) — that's where Antlers interpolation lives. A `{…}` in raw HTML is just outer HTML.
    fun testLoopScopeCrossesIntoInterpolation() {
        blueprint("blog", "hero_title")
        val el = injectedElement(
            "{{ collection:blog }}\n{{ \"{zz}\" }}\n{{ /collection }}", "zz")
        val handles = AntlersFieldContext.fieldsInScope(el, project)?.map { it.handle }
        assertEquals(listOf("hero_title"), handles)
    }

    fun testOtherCollectionFieldNotInScope() {
        blueprint("blog", "hero_title")
        blueprint("news", "headline")
        val el = injectedElement(
            "{{ collection:blog }}\n{{ \"{zz}\" }}\n{{ /collection }}", "zz")
        val handles = AntlersFieldContext.fieldsInScope(el, project)?.map { it.handle }
        assertEquals(listOf("hero_title"), handles)
        assertFalse("news field must not leak into the blog loop", handles!!.contains("headline"))
    }

    fun testInterpolationOutsideLoopFallsBackToGlobal() {
        blueprint("blog", "hero_title")
        // No enclosing loop → no namespaces → global fallback (null), exactly as before this feature.
        val el = injectedElement("{{ \"{zz}\" }}", "zz")
        assertNull("no enclosing loop → global fallback", AntlersFieldContext.fieldsInScope(el, project))
    }
}
