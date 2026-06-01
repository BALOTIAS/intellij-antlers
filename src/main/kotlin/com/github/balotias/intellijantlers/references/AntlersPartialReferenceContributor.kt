package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar

/**
 * Attaches partial references to `{{ partial:src="path" }}` and `{{ partial:path }}` forms.
 *
 * Note: Reference resolution is implemented directly on leaf PSI elements (AntlersStringLeaf,
 * AntlersIdentLeaf) via overridden getReferences(), rather than via the contributor mechanism.
 * This is because PsiReferenceContributor-based providers are not reliably invoked for
 * non-composite leaf elements in the IntelliJ 2025.2 test environment.
 *
 * The leaf-based approach via AntlersASTFactory + AntlersPartialReferenceHelper provides the
 * same functionality and is fully tested by AntlersPartialReferenceTest.
 *
 * This class is kept registered in plugin.xml as a placeholder for potential future use
 * (e.g. registering providers for composite PSI nodes).
 */
class AntlersPartialReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // References are provided via AntlersStringLeaf.getReferences() and
        // AntlersIdentLeaf.getReferences() — see AntlersASTFactory and
        // AntlersPartialReferenceHelper.
    }
}
