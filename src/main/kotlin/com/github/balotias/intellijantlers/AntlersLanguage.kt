package com.github.balotias.intellijantlers

import com.intellij.lang.Language

import com.intellij.psi.templateLanguages.TemplateLanguage

class AntlersLanguage : Language("Antlers"), TemplateLanguage {
    companion object {
        val INSTANCE = AntlersLanguage()
    }
}
