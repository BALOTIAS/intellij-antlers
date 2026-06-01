package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.openapi.project.DumbAware

class AntlersCompletionContributor : CompletionContributor(), DumbAware {
    init {
        // Trigger completion anywhere within Antlers tags
        extend(
            CompletionType.BASIC,
            psiElement(),
            AntlersCompletionProvider()
        )
    }
}
