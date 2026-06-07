package com.github.balotias.intellijantlers.formatter

import com.intellij.formatting.Block
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock

/**
 * Indentation for `*.antlers.html` is handled entirely by the post-format processors
 * ([AntlersIndentProcessor] for indentation, [AntlersSpacingPostFormatProcessor] for `{{ }}` spacing).
 * The formatting model itself is a single whole-file leaf that changes nothing, so the platform HTML
 * formatter never competes for indentation.
 */
class AntlersHtmlFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val file = formattingContext.containingFile
        return FormattingModelProvider.createFormattingModelForPsiFile(
            file, NoopBlock(file.node), formattingContext.codeStyleSettings
        )
    }

    private class NoopBlock(node: ASTNode) : AbstractBlock(node, null, null) {
        override fun buildChildren(): List<Block> = emptyList()
        override fun getSpacing(child1: Block?, child2: Block): Spacing? = null
        override fun isLeaf(): Boolean = true
        override fun getIndent(): Indent = Indent.getNoneIndent()
    }
}
