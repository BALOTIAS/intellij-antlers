package com.github.balotias.intellijantlers.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class AntlersInterpolationScannerTest {

    /** The inner content (between the braces) of each span found in [text]. */
    private fun contents(text: String): List<String> =
        AntlersInterpolationScanner.scan(text).map { text.substring(it.contentStart, it.contentEnd) }

    @Test fun findsSingleSpan() =
        assertEquals(listOf("logo:focus_css"), contents("\"object-position: {logo:focus_css}\""))

    @Test fun findsTwoSpans() =
        assertEquals(listOf("one", "two"), contents("\"a {one} b {two} c\""))

    @Test fun ignoresEscapedBrace() =
        assertEquals(emptyList<String>(), contents("\"a \\{not} b\""))

    @Test fun unmatchedOpenIsIgnored() =
        assertEquals(emptyList<String>(), contents("\"a {oops\""))

    @Test fun nestedBracesBalanced() =
        assertEquals(listOf("a {b} c"), contents("\"x {a {b} c} y\""))

    @Test fun braceInsideSingleQuoteDoesNotClose() =
        assertEquals(listOf("foo:'}'"), contents("\"{foo:'}'}\""))

    @Test fun emptyBracesYieldEmptyContent() {
        val spans = AntlersInterpolationScanner.scan("\"{}\"")
        assertEquals(1, spans.size)
        assertEquals(spans[0].contentStart, spans[0].contentEnd)
    }
}
