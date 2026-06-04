package com.github.balotias.intellijantlers.scope

import org.junit.Assert.assertEquals
import org.junit.Test

class AntlersHintParserTest {
    private fun names(body: String) = AntlersHintParser.parse(body).map { it.name }

    @Test fun recognizesAllSevenDirectives() {
        val body = "\n@name N\n@desc D\n@param p A param\n@entry e\n@collection blog\n@blueprint bp\n@set a.b.c\n"
        assertEquals(
            listOf("@name", "@desc", "@param", "@entry", "@collection", "@blueprint", "@set"),
            names(body)
        )
    }

    @Test fun ignoresNonDirectiveAndUnknown() {
        assertEquals(emptyList<String>(), names("just prose\n@unknown x\n  @ also not\n"))
    }

    @Test fun capturesValueAndNameSpan() {
        val body = "  @collection blog\n"
        val d = AntlersHintParser.parse(body).single()
        assertEquals("blog", d.value)
        assertEquals("@collection", body.substring(d.nameStart, d.nameEnd))
    }

    @Test fun directiveSetIsTheSevenNames() {
        assertEquals(
            setOf("name", "desc", "param", "entry", "collection", "blueprint", "set"),
            AntlersHintParser.DIRECTIVE_NAMES
        )
    }
}
