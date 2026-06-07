package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.editor.AntlersTemplateTagAttributes.AttrPart
import com.github.balotias.intellijantlers.editor.AntlersTemplateTagAttributes.Kind.NAME
import com.github.balotias.intellijantlers.editor.AntlersTemplateTagAttributes.Kind.VALUE
import org.junit.Assert.assertEquals
import org.junit.Test

class AntlersTemplateTagAttributesTest {

    private fun parts(t: String) = AntlersTemplateTagAttributes.parts(t)

    @Test fun singleAttribute() =
        assertEquals(
            listOf(AttrPart(10, 15, NAME), AttrPart(16, 19, VALUE)),
            parts("<{{ as }} class=\"x\">")
        )

    @Test fun valueSplitAroundInterpolation() {
        // <{{ as }} class="a {{ x }} b">  → VALUE `"a `, (skip {{ x }}), VALUE ` b"`
        val t = "<{{ as }} class=\"a {{ x }} b\">"
        assertEquals(
            listOf(AttrPart(10, 15, NAME), AttrPart(16, 19, VALUE), AttrPart(26, 29, VALUE)),
            parts(t)
        )
    }

    @Test fun multiLineValue() {
        val t = "<{{ as }} class=\"\nprose\n\">"
        // names/values present; the VALUE range spans the newlines from the opening to closing quote.
        val ps = parts(t)
        assertEquals(NAME, ps[0].kind)
        assertEquals("class", t.substring(ps[0].start, ps[0].end))
        assertEquals(VALUE, ps[1].kind)
        assertEquals("\"\nprose\n\"", t.substring(ps[1].start, ps[1].end))
    }

    @Test fun booleanAttributeHasNameOnly() {
        val ps = parts("<{{ as }} hidden>")
        assertEquals(1, ps.size)
        assertEquals(NAME, ps[0].kind)
        assertEquals("hidden", "<{{ as }} hidden>".substring(ps[0].start, ps[0].end))
    }

    @Test fun prefixedAndDashedNames() {
        val t = "<{{ as }} x-ref=\"f\" :href=\"u\" @click=\"go\">"
        val names = parts(t).filter { it.kind == NAME }.map { t.substring(it.start, it.end) }
        assertEquals(listOf("x-ref", ":href", "@click"), names)
    }

    @Test fun singleQuotes() {
        val ps = parts("<{{ as }} class='x'>")
        assertEquals(VALUE, ps[1].kind)
        assertEquals("'x'", "<{{ as }} class='x'>".substring(ps[1].start, ps[1].end))
    }

    @Test fun gtInsideValueDoesNotEndTag() {
        // the `>` inside the quoted value must not terminate the tag; `data-x` still parses
        val t = "<{{ as }} title=\"a > b\" id=\"y\">"
        val names = parts(t).filter { it.kind == NAME }.map { t.substring(it.start, it.end) }
        assertEquals(listOf("title", "id"), names)
    }

    @Test fun gtInsideExpressionDoesNotEndTag() {
        val t = "<{{ if x > 0 }} class=\"y\">"
        val names = parts(t).filter { it.kind == NAME }.map { t.substring(it.start, it.end) }
        assertEquals(listOf("class"), names)
    }

    @Test fun normalTagYieldsNothing() {
        assertEquals(emptyList<AttrPart>(), parts("<div class=\"x\">text</div>"))
    }

    @Test fun closingTemplateTagYieldsNothing() {
        assertEquals(emptyList<AttrPart>(), parts("</{{ as }}>"))
    }

    @Test fun unquotedValue() =
        assertEquals(
            listOf(AttrPart(10, 15, NAME), AttrPart(16, 19, VALUE)),
            parts("<{{ as }} class=foo>")
        )

    @Test fun multipleTemplateTags() =
        assertEquals(
            listOf(
                AttrPart(9, 10, NAME), AttrPart(11, 14, VALUE),
                AttrPart(24, 25, NAME), AttrPart(26, 29, VALUE),
            ),
            parts("<{{ a }} x=\"1\"><{{ b }} y=\"2\">")
        )
}
