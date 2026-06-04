package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.psi.AntlersFrontMatterBody
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.yaml.YAMLLanguage

/** Injects the real YAML language into a view's `---…---` front-matter body. */
class AntlersYamlInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(AntlersFrontMatterBody::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is AntlersFrontMatterBody) return
        val host = context as PsiLanguageInjectionHost
        if (!host.isValidHost) return
        registrar.startInjecting(YAMLLanguage.INSTANCE)
            .addPlace(null, null, host, TextRange(0, context.textLength))
            .doneInjecting()
    }
}
