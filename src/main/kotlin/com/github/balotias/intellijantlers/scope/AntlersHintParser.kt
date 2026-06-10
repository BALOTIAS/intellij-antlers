package com.github.balotias.intellijantlers.scope

/**
 * Parses Antlers Toolbox "Template IDE Hint" directives out of a `{{# … #}}` comment body. Pure /
 * IntelliJ-free; tolerant. A directive is a line whose first non-whitespace token is one of the
 * `@<directive>` keywords, followed by its (trimmed) value.
 */
object AntlersHintParser {

    /** The recognized directive names, without the leading `@`. */
    val DIRECTIVE_NAMES = setOf("name", "desc", "param", "deprecated", "entry", "collection", "blueprint", "set")

    /** `nameStart`/`nameEnd` = half-open `@directive` offsets in the parsed body; `value` is trimmed. */
    data class Directive(val name: String, val nameStart: Int, val nameEnd: Int, val value: String)

    private val LINE = Regex("""^(\s*)(@[a-zA-Z]+)\b(.*)$""")

    fun parse(body: String): List<Directive> {
        val out = ArrayList<Directive>()
        var pos = 0
        for (line in body.split("\n")) {
            val m = LINE.find(line)
            if (m != null && m.groupValues[2].removePrefix("@") in DIRECTIVE_NAMES) {
                val name = m.groupValues[2]
                val nameStart = pos + m.groupValues[1].length
                out.add(Directive(name, nameStart, nameStart + name.length, m.groupValues[3].trim()))
            }
            pos += line.length + 1
        }
        return out
    }
}
