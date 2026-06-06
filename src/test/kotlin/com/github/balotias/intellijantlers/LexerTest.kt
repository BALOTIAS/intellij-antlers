package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lexer.FlexAdapter
import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test

class LexerTest {

    /** Drive the lexer the same way the platform does, via FlexAdapter. */
    private fun types(input: String): List<IElementType> {
        val lexer = FlexAdapter(_AntlersLexer(null))
        lexer.start(input)
        val out = mutableListOf<IElementType>()
        while (lexer.tokenType != null) {
            out.add(lexer.tokenType!!)
            lexer.advance()
        }
        return out
    }

    @Test
    fun outerHtmlAndTag() {
        assertEquals(
            listOf(
                AntlersTypes.T_OUTER_HTML,   // "Hi "
                AntlersTypes.T_LDOUBLE,      // "{{"
                AntlersTypes.T_WS,
                AntlersTypes.T_IDENT,        // "title"
                AntlersTypes.T_WS,
                AntlersTypes.T_RDOUBLE       // "}}"
            ),
            types("Hi {{ title }}")
        )
    }

    @Test
    fun escapedDelimiterIsOuterHtml() {
        val ts = types("@{{ title }}")
        assert(ts.none { it == AntlersTypes.T_LDOUBLE }) { "escaped @{{ opened an expression: $ts" }
        assertEquals(AntlersTypes.T_OUTER_HTML, ts.first())
    }

    @Test
    fun comment() {
        assertEquals(
            listOf(AntlersTypes.T_COMMENT_OPEN, AntlersTypes.T_COMMENT_TEXT, AntlersTypes.T_COMMENT_CLOSE),
            types("{{# hidden #}}")
        )
    }

    @Test
    fun phpRawAndEcho() {
        assertEquals(
            listOf(AntlersTypes.T_PHP_RAW_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_RAW_CLOSE),
            types("{{? \$x = 1; ?}}")
        )
        assertEquals(
            listOf(AntlersTypes.T_PHP_ECHO_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_ECHO_CLOSE),
            types("{{\$ value \$}}")
        )
    }

    @Test
    fun noparseIsRaw() {
        val ts = types("{{ noparse }}{{ title }}{{ /noparse }}")
        assertEquals(AntlersTypes.T_NOPARSE_OPEN, ts.first())
        assertEquals(AntlersTypes.T_NOPARSE_CLOSE, ts.last())
        assert(ts.none { it == AntlersTypes.T_IDENT }) { "noparse body was tokenised: $ts" }
    }

    @Test
    fun modifierPipeAndOperators() {
        val ts = types("{{ a | upper }}{{ if x == 1 }}")
        assert(ts.contains(AntlersTypes.T_PIPE))
        assert(ts.contains(AntlersTypes.T_OP))
    }

    @Test
    fun accessAndParams() {
        val ts = types("{{ collection:blog limit=\"5\" :sort=\"order\" }}")
        assert(ts.contains(AntlersTypes.T_COLON))
        assert(ts.contains(AntlersTypes.T_EQUALS))
        assert(ts.contains(AntlersTypes.T_STRING))
    }

    @Test fun frontMatterAtStartIsCarvedOut() {
        assertEquals(
            listOf(
                AntlersTypes.T_FRONTMATTER_FENCE,   // "---\n"
                AntlersTypes.T_FRONTMATTER_TEXT,    // "name: ''\n"
                AntlersTypes.T_FRONTMATTER_TEXT,    // "filled: false\n"
                AntlersTypes.T_FRONTMATTER_FENCE,   // "---\n"
                AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS, AntlersTypes.T_IDENT,
                AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE
            ),
            types("---\nname: ''\nfilled: false\n---\n{{ title }}")
        )
    }

    @Test fun noLeadingFenceLexesAsBefore() {
        assertEquals(
            listOf(AntlersTypes.T_OUTER_HTML, AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS,
                AntlersTypes.T_IDENT, AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE),
            types("<div>{{ title }}")
        )
    }

    @Test fun dashesNotAtFileStartAreOuterHtml() {
        // `---` mid-file is plain content, not front matter.
        assertEquals(listOf(AntlersTypes.T_OUTER_HTML), types("x\n---\ny"))
    }

    @Test fun compoundMinusAssignIsNotEatenByIdent() {
        // #138: `foo-=3` must lex as foo / -= / 3, not `foo-` / = / 3.
        assertEquals(
            listOf(
                AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS,
                AntlersTypes.T_IDENT, AntlersTypes.T_OP, AntlersTypes.T_NUMBER,
                AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE
            ),
            types("{{ foo-=3 }}")
        )
    }

    @Test fun trailingHyphenIsAnOperator() {
        assertEquals(
            listOf(AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS, AntlersTypes.T_IDENT, AntlersTypes.T_OP,
                AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE),
            types("{{ foo- }}")
        )
    }

    @Test fun kebabIdentifiersArePreserved() {
        // hyphens BETWEEN identifier chars stay part of the identifier
        for (name in listOf("a-b", "meta-title", "my-field-name", "count-1")) {
            val ts = types("{{ $name }}")
            assertEquals("$name should be one T_IDENT, got $ts",
                listOf(AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS, AntlersTypes.T_IDENT,
                    AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE),
                ts)
        }
    }

    @Test fun phpTagBlockTokens() {
        assertEquals(
            listOf(AntlersTypes.T_PHP_TAG_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_TAG_CLOSE),
            types("<?php echo 1; ?>")
        )
    }

    @Test fun phpEchoTagTokens() {
        assertEquals(
            listOf(AntlersTypes.T_PHP_ECHO_TAG_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_TAG_CLOSE),
            types("<?= \$x ?>")
        )
    }

    @Test fun unclosedPhpTagHasNoClose() {
        val ts = types("<?php \$x = 1;")
        assertEquals(AntlersTypes.T_PHP_TAG_OPEN, ts.first())
        assert(ts.none { it == AntlersTypes.T_PHP_TAG_CLOSE }) { "unclosed tag should have no close: $ts" }
    }

    @Test fun bareProcessingInstructionStaysHtml() {
        val ts = types("<?xml version=\"1.0\"?>")
        assert(ts.all { it == AntlersTypes.T_OUTER_HTML }) { "<?xml must stay outer HTML: $ts" }
    }

    @Test fun normalHtmlAroundTagStillOpens() {
        val ts = types("<div>{{ x }}</div>")
        assert(ts.contains(AntlersTypes.T_LDOUBLE)) { "the {{ tag must still open inside HTML: $ts" }
        assert(ts.none { it == AntlersTypes.T_PHP_TAG_OPEN }) { "no PHP tag in plain HTML: $ts" }
    }

    @Test fun tightEchoTagTokens() {
        assertEquals(
            listOf(AntlersTypes.T_PHP_ECHO_TAG_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_TAG_CLOSE),
            types("<?=\$x?>")
        )
    }

    @Test fun phpTagMidHtmlKeepsSurroundingOuterHtml() {
        val ts = types("a<?php b ?>c")
        assertEquals(AntlersTypes.T_OUTER_HTML, ts.first())
        assertEquals(AntlersTypes.T_OUTER_HTML, ts.last())
        assert(ts.contains(AntlersTypes.T_PHP_TAG_OPEN) && ts.contains(AntlersTypes.T_PHP_TAG_CLOSE)) {
            "the <?php tag must be claimed mid-HTML: $ts"
        }
    }

    // The bare `%` is the Antlers tag-disambiguation prefix (e.g. `{{ %form:fields }}`), so it must be
    // its own token rather than the generic operator — otherwise the grammar can't allow it before a
    // tag name.
    @Test fun bareModuloLexesAsPercentToken() {
        val ts = types("{{ a % b }}")
        assert(ts.contains(AntlersTypes.T_PERCENT)) { "bare % should be T_PERCENT: $ts" }
        assert(!ts.contains(AntlersTypes.T_OP)) { "bare % must not also be T_OP: $ts" }
    }

    @Test fun moduloAssignStaysOperator() {
        val ts = types("{{ a %= b }}")
        assert(ts.contains(AntlersTypes.T_OP)) { "%= should stay T_OP: $ts" }
        assert(!ts.contains(AntlersTypes.T_PERCENT)) { "%= must not be T_PERCENT: $ts" }
    }
}
