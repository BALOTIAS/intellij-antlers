package com.github.balotias.intellijantlers.highlighting

import com.github.balotias.intellijantlers.AntlersIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class AntlersColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = AntlersIcons.FILE
    override fun getHighlighter(): SyntaxHighlighter = AntlersSyntaxHighlighter()
    override fun getDemoText(): String = DEMO
    override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey> =
        mutableMapOf(
            "tag" to AntlersSyntaxHighlighter.TAG,
            "kw" to AntlersSyntaxHighlighter.KEYWORD,
            "mod" to AntlersSyntaxHighlighter.MODIFIER,
        )
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = "Antlers"

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Braces & delimiters", AntlersSyntaxHighlighter.BRACES),
            AttributesDescriptor("Tag name", AntlersSyntaxHighlighter.TAG),
            AttributesDescriptor("Condition keyword", AntlersSyntaxHighlighter.KEYWORD),
            AttributesDescriptor("Modifier", AntlersSyntaxHighlighter.MODIFIER),
            AttributesDescriptor("Identifier", AntlersSyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("String", AntlersSyntaxHighlighter.STRING),
            AttributesDescriptor("Number", AntlersSyntaxHighlighter.NUMBER),
            AttributesDescriptor("Comment", AntlersSyntaxHighlighter.COMMENT),
            AttributesDescriptor("Operator", AntlersSyntaxHighlighter.OPERATOR),
        )

        private val DEMO = """
            {{# Featured posts #}}
            {{ <tag>collection</tag>:blog limit="3" as="posts" }}
              {{ title | <mod>upper</mod> }}
              {{ <kw>if</kw> count > 0 }}{{ price }}{{ /collection }}
        """.trimIndent()
    }
}
