package com.github.balotias.intellijantlers.template

import com.github.balotias.intellijantlers.AntlersFileType
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

/** Scopes the bundled live templates to Antlers files. Context id comes from the
 *  `<liveTemplateContext contextId="ANTLERS">` registration; the templates XML references it. */
class AntlersTemplateContextType : TemplateContextType("Antlers") {
    override fun isInContext(context: TemplateActionContext): Boolean =
        context.file.fileType is AntlersFileType
}
