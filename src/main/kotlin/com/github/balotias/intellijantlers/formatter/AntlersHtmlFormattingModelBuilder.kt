package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.parser.AntlersFile
import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.xml.XmlFormattingPolicy
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.psi.xml.XmlElement
import com.intellij.xml.template.formatter.AbstractXmlTemplateFormattingModelBuilder
import com.intellij.xml.template.formatter.TemplateLanguageBlock

/**
 * Reformat-Code indentation for `*.antlers.html`.
 *
 * Re-uses the bundled HTML formatter as the PRIMARY block tree (via
 * [AbstractXmlTemplateFormattingModelBuilder]) so the HTML structure is indented by HTML nesting.
 * The Antlers `{{ ... }}` regions are merged in as opaque template blocks that take whatever indent
 * the framework hands them and do NOT add their own indent level — no absolute-indent bump, so no
 * accumulation down the ancestor chain and no runaway indentation on plain-text / multi-child content.
 *
 * Known tradeoff (accepted): an Antlers pair-tag body that is not wrapped in an HTML element stays at
 * its surrounding HTML level (no extra Antlers indent level).
 *
 * In-`{{ }}` spacing is handled separately by [AntlersSpacingPostFormatProcessor]; this builder adds
 * no Antlers spacing (getSpacing returns null).
 */
class AntlersHtmlFormattingModelBuilder : AbstractXmlTemplateFormattingModelBuilder() {

    override fun isTemplateFile(file: PsiFile?): Boolean = file is AntlersFile

    override fun isOuterLanguageElement(element: PsiElement?): Boolean = element is OuterLanguageElement

    override fun isMarkupLanguageElement(element: PsiElement?): Boolean =
        element is XmlElement && element !is OuterLanguageElement

    override fun createTemplateLanguageBlock(
        node: ASTNode,
        settings: CodeStyleSettings,
        xmlFormattingPolicy: XmlFormattingPolicy,
        indent: Indent?,
        alignment: Alignment?,
        wrap: Wrap?
    ): Block = AntlersTemplateBlock(this, node, wrap, alignment, settings, xmlFormattingPolicy, indent)

    private class AntlersTemplateBlock(
        builder: AbstractXmlTemplateFormattingModelBuilder,
        node: ASTNode,
        wrap: Wrap?,
        alignment: Alignment?,
        settings: CodeStyleSettings,
        xmlFormattingPolicy: XmlFormattingPolicy,
        indent: Indent?
    ) : TemplateLanguageBlock(builder, node, wrap, alignment, settings, xmlFormattingPolicy, indent) {

        // Children of an Antlers block (i.e. content directly under a {{ }} region in the Antlers tree)
        // get no extra indent of their own — they keep the HTML-assigned position.
        override fun getChildIndent(node: ASTNode): Indent = Indent.getNoneIndent()

        // No Antlers-side spacing; AntlersSpacingPostFormatProcessor owns in-{{ }} spacing.
        override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

        override fun getSpacing(adjacentBlock: TemplateLanguageBlock?): Spacing? = null
    }
}
