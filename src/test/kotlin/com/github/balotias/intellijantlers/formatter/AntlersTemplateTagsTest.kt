package com.github.balotias.intellijantlers.formatter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntlersTemplateTagsTest {
    @Test fun singleLineOpenClose() =
        assertEquals(listOf(TemplateElement(0, 0, 0)), AntlersTemplateTags.elements("<{{ tag }}>x</{{ tag }}>"))

    @Test fun multiLineOpenAndBody() =
        assertEquals(
            listOf(TemplateElement(0, 2, 4)),
            AntlersTemplateTags.elements("<{{ tag }}\nclass=\"x\"\n>\nbody\n</{{ tag }}>"))

    @Test fun selfClosingIsNotAContainer() =
        assertTrue(AntlersTemplateTags.elements("<{{ tag }} class=\"x\" />").isEmpty())

    @Test fun gtInsideAntlersIsNotTagEnd() =
        assertEquals(
            listOf(TemplateElement(0, 2, 3)),
            AntlersTemplateTags.elements("<{{ tag }}\n{{ x > 0 ? 'a' : 'b' }}\n>\n</{{ tag }}>"))

    @Test fun gtInsideQuoteIsNotTagEnd() =
        assertEquals(
            listOf(TemplateElement(0, 0, 0)),
            AntlersTemplateTags.elements("<{{ tag }} class=\"a>b\">x</{{ tag }}>"))

    @Test fun nestedTemplateTags() =
        assertEquals(2, AntlersTemplateTags.elements("<{{ a }}>\n<{{ b }}>\nx\n</{{ b }}>\n</{{ a }}>").size)

    @Test fun unbalancedOpenIsUnclosed() =
        assertEquals(
            listOf(TemplateElement(0, 0, null)),
            AntlersTemplateTags.elements("<{{ tag }}>\nbody"))

    @Test fun plainHtmlAndPlainAntlersAreIgnored() =
        assertTrue(AntlersTemplateTags.elements("<div>{{ x }}</div>\n{{ if y }}{{ /if }}").isEmpty())
}
