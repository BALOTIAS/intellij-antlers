package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.editor.AntlersInterpolationScanner
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Injects the Antlers language into each `{ … }` interpolation span of a string literal, wrapped as
 * `{{ … }}`, so the interpolation parses as a real Antlers expression — enabling completion, go-to-def,
 * hover, and modifier docs/Ctrl+P inside interpolation. Each span is its own injected fragment. Field
 * scope inside the fragment is global/page (not the enclosing loop) — a documented limitation.
 */
class AntlersStringInterpolationInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(AntlersStringLeaf::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is AntlersStringLeaf) return
        val host = context as PsiLanguageInjectionHost
        if (!host.isValidHost) return
        for (span in AntlersInterpolationScanner.scan(context.text)) {
            if (span.contentStart >= span.contentEnd) continue
            registrar.startInjecting(AntlersLanguage.INSTANCE)
                .addPlace("{{ ", " }}", host, TextRange(span.contentStart, span.contentEnd))
                .doneInjecting()
        }
    }
}
