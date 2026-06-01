package com.github.balotias.intellijantlers

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class AntlersFileType : LanguageFileType(AntlersLanguage.INSTANCE) {
    companion object {
        val INSTANCE = AntlersFileType()
    }

    override fun getName(): String = "Antlers"

    override fun getDescription(): String = "Statamic Antlers Template"

    override fun getDefaultExtension(): String = "antlers.html"

    override fun getIcon(): Icon = AntlersIcons.FILE
}
