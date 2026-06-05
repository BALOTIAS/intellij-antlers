package com.github.balotias.intellijantlers.completion

/**
 * Computes which positional argument the caret sits in, given a modifier's raw text and a caret offset
 * relative to that text. Handles both `replace('a', 'b')` (comma-separated in parens) and
 * `replace:'a':'b'` (colon-separated) forms. Returns -1 when the caret is not inside the argument region.
 * Pure / IntelliJ-free.
 */
object ModifierArgIndex {

    fun indexAt(text: String, caret: Int): Int {
        val lparen = text.indexOf('(')
        if (lparen >= 0) {
            if (caret <= lparen) return -1
            var depth = 0
            var index = 0
            var quote: Char? = null
            var i = lparen
            while (i < text.length && i < caret) {
                val c = text[i]
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '\'' || c == '"' -> quote = c
                    c == '(' || c == '[' -> depth++
                    c == ')' || c == ']' -> { depth--; if (depth == 0) return -1 }
                    c == ',' && depth == 1 -> index++
                }
                i++
            }
            return index
        }

        val colon = text.indexOf(':')
        if (colon >= 0) {
            if (caret <= colon) return -1
            var index = 0
            var quote: Char? = null
            var i = colon + 1
            while (i < text.length && i < caret) {
                val c = text[i]
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '\'' || c == '"' -> quote = c
                    c == ':' -> index++
                }
                i++
            }
            return index
        }
        return -1
    }
}
