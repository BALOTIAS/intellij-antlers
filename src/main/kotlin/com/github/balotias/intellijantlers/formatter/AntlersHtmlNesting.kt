package com.github.balotias.intellijantlers.formatter

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

/**
 * Per-line HTML nesting depth from the template-data (HTML) PSI tree: every [XmlTag] adds 1 to the lines
 * strictly inside it (its open and close lines stay at the enclosing level). The HTML parser handles
 * void / self-closing / optional-close / inline tags and multi-line attribute lists for free.
 * `<pre>` / `<textarea>` interiors are reported as preserve lines (significant whitespace). Never throws.
 */
object AntlersHtmlNesting {

    class Result(val depth: IntArray, val preserve: Set<Int>)

    private val PRESERVE_TAGS = setOf("pre", "textarea")

    fun compute(document: Document, htmlFile: PsiFile?): Result {
        val lineCount = document.lineCount
        val depth = IntArray(lineCount)
        val preserve = HashSet<Int>()
        if (htmlFile == null) return Result(depth, preserve)

        for (tag in PsiTreeUtil.findChildrenOfType(htmlFile, XmlTag::class.java)) {
            val range = tag.textRange
            if (range.isEmpty) continue
            val sLine = document.getLineNumber(range.startOffset)
            // endOffset is exclusive; -1 gives the last char's line (and avoids getLineNumber throwing
            // when the tag ends exactly at document end).
            val eLine = document.getLineNumber(range.endOffset - 1)
            if (eLine <= sLine) continue                       // single-line tag → no interior
            val isPreserve = tag.name.lowercase() in PRESERVE_TAGS
            for (l in (sLine + 1) until eLine) {
                depth[l] += 1
                if (isPreserve) preserve.add(l)
            }
        }
        return Result(depth, preserve)
    }
}
