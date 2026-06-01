package com.github.balotias.intellijantlers.template

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersElementType
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings

/**
 * Element type used to mark the hidden Antlers `{{ ... }}` ranges inside the generated
 * template-data (HTML) tree. These become [com.intellij.psi.templateLanguages.OuterLanguageElement]s.
 */
val ANTLERS_FRAGMENT = AntlersElementType("ANTLERS_FRAGMENT")

/**
 * Splits an `.antlers.html` document into an HTML data tree and the Antlers template tree.
 *
 * IMPORTANT: the 3rd constructor argument (`templateElementType`) is the *lexer token whose text
 * is copied verbatim into the HTML data document* — i.e. the outer HTML, [AntlersTypes.T_OUTER_HTML].
 * Every other token (the `{{ }}` code) is replaced by an [ANTLERS_FRAGMENT] placeholder so the
 * HTML parser never sees it. Passing a composite node (e.g. TAG_STATEMENT) here silently disables
 * all HTML/CSS completion, because the lexer never emits composite nodes.
 */
val ANTLERS_TEMPLATE_DATA = TemplateDataElementType(
    "ANTLERS_TEMPLATE_DATA",
    AntlersLanguage.INSTANCE,
    AntlersTypes.T_OUTER_HTML,
    ANTLERS_FRAGMENT
)

class AntlersFileViewProvider(
    manager: PsiManager,
    virtualFile: VirtualFile,
    eventSystemEnabled: Boolean,
    private val templateDataLanguage: Language
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, virtualFile, eventSystemEnabled),
    ConfigurableTemplateLanguageFileViewProvider {

    constructor(manager: PsiManager, virtualFile: VirtualFile, eventSystemEnabled: Boolean) : this(
        manager,
        virtualFile,
        eventSystemEnabled,
        getTemplateDataLanguage(manager, virtualFile)
    )

    override fun getBaseLanguage(): Language = AntlersLanguage.INSTANCE

    override fun getTemplateDataLanguage(): Language = templateDataLanguage

    override fun getLanguages(): Set<Language> = setOf(AntlersLanguage.INSTANCE, templateDataLanguage)

    override fun cloneInner(virtualFile: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider {
        return AntlersFileViewProvider(manager, virtualFile, false, templateDataLanguage)
    }

    override fun createFile(lang: Language): PsiFile? {
        val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null
        
        if (lang === templateDataLanguage) {
            val file = parserDefinition.createFile(this) as PsiFileImpl
            file.contentElementType = ANTLERS_TEMPLATE_DATA
            return file
        } else if (lang === AntlersLanguage.INSTANCE) {
            return parserDefinition.createFile(this)
        }
        
        return null
    }

    companion object {
        private fun getTemplateDataLanguage(manager: PsiManager, virtualFile: VirtualFile): Language {
            val dataLang = TemplateDataLanguageMappings.getInstance(manager.project)?.getMapping(virtualFile)
            return dataLang ?: HTMLLanguage.INSTANCE
        }
    }
}
