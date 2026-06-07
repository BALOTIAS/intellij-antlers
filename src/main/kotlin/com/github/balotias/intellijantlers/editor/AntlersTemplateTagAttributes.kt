package com.github.balotias.intellijantlers.editor

/**
 * Pure scanner for the attribute name/value ranges of template-named `<{{ … }}>` open tags. The editor's
 * layered HTML highlighter can't color these (it re-lexes each outer-HTML chunk independently, so the part
 * after `}}` is plain text), so [AntlersTemplateTagAttributeAnnotator] overlays HTML colors onto the ranges
 * this returns. Only `<{{ }}>` tags are scanned — normal `<div>` tags already color via the lexer.
 *
 * IntelliJ-free and tolerant: skips `{{ … }}` regions and quoted strings while finding the tag's closing
 * `>` (so a `>` inside an expression or value doesn't end the tag), and splits quoted values around any
 * `{{ … }}` so interpolations stay Antlers-colored. Never throws.
 */
object AntlersTemplateTagAttributes {

    enum class Kind { NAME, VALUE }

    /** Half-open `[start, end)` offsets into the scanned text. */
    data class AttrPart(val start: Int, val end: Int, val kind: Kind)

    fun parts(text: String): List<AttrPart> {
        val out = ArrayList<AttrPart>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (text[i] == '<' && text.getOrNull(i + 1) == '{' && text.getOrNull(i + 2) == '{') {
                i = scanOpenTag(text, i, out)
            } else {
                i++
            }
        }
        return out
    }

    /** Scan one `<{{ … }}` open tag from [start] (`<`); returns the index after its closing `>` (or EOF). */
    private fun scanOpenTag(text: String, start: Int, out: MutableList<AttrPart>): Int {
        val n = text.length
        var i = skipInterpolation(text, start + 1)   // skip the `{{ … }}` tag name
        while (i < n) {
            val c = text[i]
            when {
                c == '>' -> return i + 1
                c == '/' && text.getOrNull(i + 1) == '>' -> return i + 2
                c == '{' && text.getOrNull(i + 1) == '{' -> i = skipInterpolation(text, i)
                c == ' ' || c == '\t' || c == '\n' || c == '\r' -> i++
                isNameChar(c) -> {
                    val ns = i
                    while (i < n && isNameChar(text[i])) i++
                    out.add(AttrPart(ns, i, Kind.NAME))
                    var j = i
                    while (j < n && isSpace(text[j])) j++
                    if (j < n && text[j] == '=') {
                        j++
                        while (j < n && isSpace(text[j])) j++
                        i = scanValue(text, j, out)
                    } else {
                        i = j
                    }
                }
                else -> i++
            }
        }
        return n
    }

    /** From a `{{` at [at], return the index after the matching `}}` (string-aware); EOF if unterminated. */
    private fun skipInterpolation(text: String, at: Int): Int {
        val n = text.length
        var i = at + 2
        var quote: Char? = null
        while (i < n) {
            val c = text[i]
            when {
                quote != null -> { if (c == quote) quote = null; i++ }
                c == '"' || c == '\'' -> { quote = c; i++ }
                c == '}' && text.getOrNull(i + 1) == '}' -> return i + 2
                else -> i++
            }
        }
        return n
    }

    /** Emit VALUE part(s) for the value at [at]; returns the index after the value. */
    private fun scanValue(text: String, at: Int, out: MutableList<AttrPart>): Int {
        val n = text.length
        val q = text.getOrNull(at) ?: return at
        if (q == '"' || q == '\'') {
            var segStart = at                         // opening quote starts the first segment
            var i = at + 1
            while (i < n) {
                val c = text[i]
                when {
                    c == '{' && text.getOrNull(i + 1) == '{' -> {
                        if (i > segStart) out.add(AttrPart(segStart, i, Kind.VALUE))
                        i = skipInterpolation(text, i)
                        segStart = i
                    }
                    c == q -> {
                        out.add(AttrPart(segStart, i + 1, Kind.VALUE))   // final segment, incl. closing quote
                        return i + 1
                    }
                    else -> i++
                }
            }
            if (n > segStart) out.add(AttrPart(segStart, n, Kind.VALUE))   // unterminated quote
            return n
        }
        // Unquoted value: run until whitespace / `>` / `{{`.
        var i = at
        while (i < n) {
            val c = text[i]
            if (isSpace(c) || c == '>') break
            if (c == '{' && text.getOrNull(i + 1) == '{') break
            i++
        }
        if (i > at) out.add(AttrPart(at, i, Kind.VALUE))
        return i
    }

    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    /** HTML attribute-name chars, incl. framework prefixes/separators (`x-ref`, `attr:class`, `@click`, `:href`). */
    private fun isNameChar(c: Char) =
        c.isLetterOrDigit() || c == '-' || c == '_' || c == ':' || c == '.' || c == '@'
}
