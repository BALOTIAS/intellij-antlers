package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.psi.AntlersPhpBlockBody
import com.github.balotias.intellijantlers.psi.AntlersPhpEchoBlock
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Injects the real PHP language into `{{$ … $}}` / `{{? … ?}}` block bodies, when the PHP plugin is
 * present (PhpStorm / IDEA Ultimate). PHP is resolved by id so there is no compile-time PHP dependency;
 * absent -> graceful no-op.
 */
class AntlersPhpInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(AntlersPhpBlockBody::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is AntlersPhpBlockBody) return
        val php = Language.findLanguageByID("PHP") ?: return
        val host = context as PsiLanguageInjectionHost
        registrar.startInjecting(php)
            .addPlace(prefixFor(context), "", host, TextRange(0, context.textLength))
            .doneInjecting()
    }

    companion object {
        /** `<?= ` for an echo block (a PHP expression) else `<?php ` (raw block statements). */
        fun prefixFor(body: AntlersPhpBlockBody): String =
            if (body.parent is AntlersPhpEchoBlock) "<?= " else "<?php "
    }
}
