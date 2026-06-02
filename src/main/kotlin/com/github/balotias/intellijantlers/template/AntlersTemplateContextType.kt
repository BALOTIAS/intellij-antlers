package com.github.balotias.intellijantlers.template

import com.github.balotias.intellijantlers.AntlersFileType
import com.github.balotias.intellijantlers.psi.AntlersComment
import com.github.balotias.intellijantlers.psi.AntlersNoparseBlock
import com.github.balotias.intellijantlers.psi.AntlersPhpBlock
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.util.PsiTreeUtil

/** Scopes the bundled live templates to Antlers files, but ONLY outside an existing `{{ }}` — the
 *  templates embed their own delimiters, so firing them inside braces would double them. */
class AntlersTemplateContextType : TemplateContextType("Antlers") {
    override fun isInContext(context: TemplateActionContext): Boolean {
        val file = context.file
        if (file.fileType !is AntlersFileType) return false
        val el = file.findElementAt(context.startOffset) ?: return true
        return PsiTreeUtil.getParentOfType(
            el,
            AntlersStatement::class.java,
            AntlersComment::class.java,
            AntlersNoparseBlock::class.java,
            AntlersPhpBlock::class.java
        ) == null
    }
}
