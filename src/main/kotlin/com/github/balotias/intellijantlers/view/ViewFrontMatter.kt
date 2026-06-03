package com.github.balotias.intellijantlers.view

/** Half-open [start, end) — maps directly to com.intellij.openapi.util.TextRange(start, end). */
data class TextSpan(val start: Int, val end: Int)

data class FmEntry(
    val name: String,
    val indent: Int,
    val key: TextSpan,
    val value: TextSpan?,
    val valuePreview: String
)

data class FrontMatter(
    val block: TextSpan,
    val openFence: TextSpan,
    val closeFence: TextSpan,
    val entries: List<FmEntry>
)

/**
 * Hand-rolled extractor for Statamic Antlers view front matter — the leading `---…---` YAML block.
 * Pure / IntelliJ-free so it can be unit-tested directly. Tolerant; never throws. Returns null unless
 * the text starts with a `---` fence (column 0) and has a closing `---` fence.
 */
object ViewFrontMatterScanner {

    private val KEY_RE = Regex("""^(\s*)([A-Za-z_][A-Za-z0-9_-]*):(.*)$""")

    fun scan(text: String): FrontMatter? {
        val body = if (text.isNotEmpty() && text[0] == '﻿') text.substring(1) else text
        val shift = text.length - body.length        // 0 or 1 (leading UTF-8 BOM)
        val lines = body.split("\n")
        if (lines.isEmpty() || lines[0].trimEnd() != "---") return null

        val lineStart = IntArray(lines.size)
        var pos = 0
        for (i in lines.indices) { lineStart[i] = pos; pos += lines[i].length + 1 }

        var closeLine = -1
        for (i in 1 until lines.size) if (lines[i].trimEnd() == "---") { closeLine = i; break }
        if (closeLine == -1) return null

        val openFence = TextSpan(shift, shift + 3)
        val closeFence = TextSpan(shift + lineStart[closeLine], shift + lineStart[closeLine] + 3)

        val entries = ArrayList<FmEntry>()
        for (i in 1 until closeLine) {
            val line = lines[i]
            val m = KEY_RE.find(line) ?: continue
            val indent = m.groupValues[1].length
            val name = m.groupValues[2]
            val keyStart = lineStart[i] + indent
            val key = TextSpan(shift + keyStart, shift + keyStart + name.length)
            val rawValue = m.groupValues[3]                                   // text after the colon
            val lead = rawValue.length - rawValue.trimStart().length
            val valueText = rawValue.trim()
            val value = if (valueText.isEmpty()) null else {
                val vStart = lineStart[i] + indent + name.length + 1 + lead   // +1 for the ':'
                TextSpan(shift + vStart, shift + vStart + valueText.length)
            }
            entries.add(FmEntry(name, indent, key, value, stripQuotes(valueText)))
        }

        return FrontMatter(TextSpan(0, closeFence.end), openFence, closeFence, entries)
    }

    private fun stripQuotes(s: String): String =
        if (s.length >= 2 && ((s.first() == '"' && s.last() == '"') || (s.first() == '\'' && s.last() == '\'')))
            s.substring(1, s.length - 1) else s
}
