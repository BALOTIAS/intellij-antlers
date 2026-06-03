package com.github.balotias.intellijantlers.editor

/**
 * Finds Antlers `{ … }` interpolation spans inside a string literal's raw text (quotes included).
 * Pure / IntelliJ-free so it can be unit-tested directly. Highlighting only — no parsing.
 */
object AntlersInterpolationScanner {

    /** Offsets relative to the scanned text. `contentStart == openBrace + 1`, `contentEnd == closeBrace`. */
    data class Span(val openBrace: Int, val contentStart: Int, val contentEnd: Int, val closeBrace: Int)

    fun scan(text: String): List<Span> {
        val spans = ArrayList<Span>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '\\') { i += 2; continue }          // escape: skip the next char (e.g. \{ )
            if (c == '{') {
                val span = matchSpan(text, i)
                if (span != null) { spans.add(span); i = span.closeBrace + 1; continue }
            }
            i++
        }
        return spans
    }

    /** From an opening `{` at [open], find the balanced closing `}`; null if unmatched. */
    private fun matchSpan(text: String, open: Int): Span? {
        var depth = 0
        var i = open
        val n = text.length
        var inSingle = false                              // inside a '…' inner string
        while (i < n) {
            val c = text[i]
            when {
                c == '\\' -> { i += 2; continue }         // escape in any state
                inSingle -> { if (c == '\'') inSingle = false; i++ }
                c == '\'' -> { inSingle = true; i++ }
                c == '{' -> { depth++; i++ }
                c == '}' -> {
                    depth--
                    if (depth == 0) return Span(open, open + 1, i, i)
                    i++
                }
                else -> i++
            }
        }
        return null
    }
}
