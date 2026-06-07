package com.github.balotias.intellijantlers.highlighting

import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersTemplateTagAttrHighlightTest : BasePlatformTestCase() {

    /** Keys our annotator forced over a piece of text whose content contains [needle]. */
    private fun forcedKeysOver(text: String, needle: String): List<TextAttributesKey> {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .filter { it.forcedTextAttributesKey != null && it.text?.contains(needle) == true }
            .map { it.forcedTextAttributesKey!! }
    }

    fun testTemplateTagAttributeValueColored() {
        val keys = forcedKeysOver("<{{ as }} class=\"prose\">x</{{ as }}>", "prose")
        assertTrue("value carries HTML_ATTRIBUTE_VALUE: $keys",
            keys.contains(XmlHighlighterColors.HTML_ATTRIBUTE_VALUE))
    }

    fun testTemplateTagAttributeNameColored() {
        val keys = forcedKeysOver("<{{ as }} class=\"prose\">x</{{ as }}>", "class")
        assertTrue("name carries HTML_ATTRIBUTE_NAME: $keys",
            keys.contains(XmlHighlighterColors.HTML_ATTRIBUTE_NAME))
    }

    fun testNormalDivNotAnnotatedByUs() {
        // A normal <div> is colored by the lexer, not by our annotator: no FORCED key over its value.
        myFixture.configureByText("p.antlers.html", "<div class=\"prose\">x</div>")
        val forced = myFixture.doHighlighting()
            .any { it.forcedTextAttributesKey == XmlHighlighterColors.HTML_ATTRIBUTE_VALUE && it.text?.contains("prose") == true }
        assertFalse("our annotator must not touch normal <div> tags", forced)
    }

    fun testInterpolationInsideValueNotAttributeColored() {
        // Distinct token `slot` (≠ the attribute name) so a match can only come from the interpolation.
        myFixture.configureByText("p.antlers.html", "<{{ as }} class=\"{{ slot }}\">x</{{ as }}>")
        val valueOverSlot = myFixture.doHighlighting().any {
            it.forcedTextAttributesKey == XmlHighlighterColors.HTML_ATTRIBUTE_VALUE && it.text?.contains("slot") == true
        }
        assertFalse("the {{ slot }} interpolation must not be HTML_ATTRIBUTE_VALUE-colored", valueOverSlot)
    }
}
