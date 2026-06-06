package com.github.balotias.intellijantlers.formatter

/** A template-named HTML tag `<{{ … }} … >` … `</{{ … }}>`. 0-based lines; [closeLine] null = unclosed. */
data class TemplateElement(val openStartLine: Int, val openEndLine: Int, val closeLine: Int?)

/**
 * Scans raw text for template-named HTML tags. `{{ … }}` regions and HTML attribute quotes are skipped so
 * that a `>` inside an Antlers expression (`{{ x > 0 }}`) or a quoted value (`class="a>b"`) is not mistaken
 * for the tag terminator. Pure / IntelliJ-free; tolerant (unbalanced → unclosed / ignored), never throws.
 */
object AntlersTemplateTags {
    fun elements(text: String): List<TemplateElement> {
        val out = ArrayList<TemplateElement>()
        val open = ArrayDeque<Pair<Int, Int>>()      // (openStartLine, openEndLine) awaiting a close tag
        var i = 0; var line = 0; val n = text.length
        var antlers = 0                              // depth of open `{{`
        var quote: Char? = null
        var openStart = -1                           // line of a `<{{` whose terminator we are seeking, or -1
        while (i < n) {
            val c = text[i]
            if (c == '\n') { line++; i++; continue }
            if (antlers > 0) {
                when {
                    c == '{' && text.getOrNull(i + 1) == '{' -> { antlers++; i += 2 }
                    c == '}' && text.getOrNull(i + 1) == '}' -> { antlers--; i += 2 }
                    else -> i++
                }
                continue
            }
            if (quote != null) { if (c == quote) quote = null; i++; continue }
            when {
                c == '"' || c == '\'' -> { quote = c; i++ }
                c == '{' && text.getOrNull(i + 1) == '{' -> { antlers++; i += 2 }
                openStart >= 0 && c == '/' && text.getOrNull(i + 1) == '>' -> { openStart = -1; i += 2 } // self-closing
                openStart >= 0 && c == '>' -> { open.addLast(openStart to line); openStart = -1; i++ }
                openStart >= 0 -> i++                                                          // scanning open tag
                c == '<' && text.getOrNull(i + 1) == '/' &&
                    text.getOrNull(i + 2) == '{' && text.getOrNull(i + 3) == '{' -> {          // </{{ close tag
                    if (open.isNotEmpty()) { val (os, oe) = open.removeLast(); out.add(TemplateElement(os, oe, line)) }
                    i += 4; antlers++                                                          // enter its {{ … }}
                }
                c == '<' && text.getOrNull(i + 1) == '{' && text.getOrNull(i + 2) == '{' -> {  // <{{ open tag
                    openStart = line; i += 3; antlers++                                        // enter its {{ … }}
                }
                else -> i++
            }
        }
        while (open.isNotEmpty()) { val (os, oe) = open.removeLast(); out.add(TemplateElement(os, oe, null)) }
        return out
    }
}
