package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Reformat indentation produced by [AntlersIndentProcessor].
 *
 * Each line is indented to one combined absolute depth = enclosing HTML elements + enclosing Antlers
 * pairs/conditions + template-named tags + multi-line params. Void HTML elements (`<br>`, `<hr>`,
 * `<img>`) have no multi-line interior so add no depth. Asserted values are the rule-derived columns,
 * verified stable and idempotent. INDENT_SIZE = 4.
 */
class AntlersHtmlFormatTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("t.antlers.html", text)
        CodeStyle.getSettings(file).indentOptions?.INDENT_SIZE = 4
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    fun testPureHtml() {
        assertEquals(
            "<div>\n    <p>hello</p>\n</div>",
            reformat("<div>\n<p>hello</p>\n</div>")
        )
    }

    /**
     * Plain-text content inside an Antlers pair tag: `<li>` depth = enclosing `<ul>` (1) + `collection`
     * pair (1) = 2 → 8 sp. (The old additive model double-counted to 12 sp.)
     */
    fun testPlainTextContent() {
        assertEquals(
            "<ul>\n    {{ collection:blog }}\n        <li>x</li>\n    {{ /collection }}\n</ul>",
            reformat("<ul>\n{{ collection:blog }}\n<li>x</li>\n{{ /collection }}\n</ul>")
        )
    }

    fun testAntlersContent() {
        assertEquals(
            "<ul>\n    {{ collection:blog }}\n        <li>{{ title }}</li>\n    {{ /collection }}\n</ul>",
            reformat("<ul>\n{{ collection:blog }}\n<li>{{ title }}</li>\n{{ /collection }}\n</ul>")
        )
    }

    fun testMultiChildText() {
        // depth: collection[0] <article>[1] <h2>/<p>[2] </article>[1] /collection[0]
        assertEquals(
            "{{ collection:blog }}\n    <article>\n        <h2>Title</h2>\n        <p>body text</p>\n    </article>\n{{ /collection }}",
            reformat("{{ collection:blog }}\n<article>\n<h2>Title</h2>\n<p>body text</p>\n</article>\n{{ /collection }}")
        )
    }

    /** Two plain siblings inside an Antlers pair tag: each `<p>` = `<div>` (1) + `if` (1) = 2 → 8 sp. */
    fun testTwoPlainSiblings() {
        assertEquals(
            "<div>\n    {{ if x }}\n        <p>a</p>\n        <p>b</p>\n    {{ /if }}\n</div>",
            reformat("<div>\n{{ if x }}\n<p>a</p>\n<p>b</p>\n{{ /if }}\n</div>")
        )
    }

    fun testTwoHtmlLevels() {
        // Input `<div><ul>` on one physical line, so both open on line 0 and close on the last line:
        // collection[2=div+ul] <li>[3=div+ul+collection] /collection[2].
        assertEquals(
            "<div><ul>\n        {{ collection:blog }}\n            <li>x</li>\n        {{ /collection }}\n</ul></div>",
            reformat("<div><ul>\n{{ collection:blog }}\n<li>x</li>\n{{ /collection }}\n</ul></div>")
        )
    }

    /** Reformatting twice (and from over-indented garbage) converges to the same canonical result. */
    fun testIdempotentAndConverges() {
        // `<li>` = enclosing `<ul>` (1) + `collection` pair (1) = 2 → 8 sp.
        val canonical =
            "<ul>\n    {{ collection:blog }}\n        <li>x</li>\n    {{ /collection }}\n</ul>"

        val once = reformat("<ul>\n{{ collection:blog }}\n<li>x</li>\n{{ /collection }}\n</ul>")
        assertEquals(canonical, once)
        assertEquals(canonical, reformat(once))            // idempotent

        // Over-indented garbage must collapse back, not accumulate further.
        val garbage =
            "<ul>\n                {{ collection:blog }}\n                        <li>x</li>\n        {{ /collection }}\n</ul>"
        assertEquals(canonical, reformat(garbage))
    }

    /**
     * Bare, attribute-less void HTML elements (`<br>`, `<hr>`) inside a `{{ }}` pair indent to the same
     * sibling level as an attributed `<img …>`: depth = enclosing `<div>` (1) + `collection` pair (1)
     * = 2 → 8 sp. Void elements have no multi-line interior so add no depth of their own.
     */
    fun testBareVoidElementIndent() {
        assertEquals(
            "<div>\n    {{ collection:blog }}\n        <img src=\"x\" alt=\"y\">\n        <br>\n    {{ /collection }}\n</div>",
            reformat("<div>\n{{ collection:blog }}\n<img src=\"x\" alt=\"y\">\n<br>\n{{ /collection }}\n</div>")
        )
        // bare void as the only child, and two bare voids as siblings — all at the sibling level
        assertEquals(
            "<div>\n    {{ collection:blog }}\n        <br>\n    {{ /collection }}\n</div>",
            reformat("<div>\n{{ collection:blog }}\n<br>\n{{ /collection }}\n</div>")
        )
        assertEquals(
            "<div>\n    {{ collection:blog }}\n        <br>\n        <hr>\n    {{ /collection }}\n</div>",
            reformat("<div>\n{{ collection:blog }}\n<br>\n<hr>\n{{ /collection }}\n</div>")
        )
    }

    /**
     * Nested Antlers blocks: `{{ if b }}` = `if a` (1) → 4 sp; `<p>x</p>` = `if a` (1) + `if b` (1)
     * = 2 → 8 sp; inner `{{ /if }}` back to 4 sp.
     */
    fun testNestedAntlersNoAccumulation() {
        assertEquals(
            "{{ if a }}\n    {{ if b }}\n        <p>x</p>\n    {{ /if }}\n{{ /if }}",
            reformat("{{ if a }}\n{{ if b }}\n<p>x</p>\n{{ /if }}\n{{ /if }}")
        )
    }

    /**
     * Previously a known limitation: a void element on its own line after an element whose attribute
     * holds an Antlers fragment (`<img src="{{ image }}">` then `<br>`) under-indented to a lower column
     * than its sibling. Now fixed — depth is computed from the data PSI trees, so both `<img>` and `<br>`
     * sit at the sibling level: `<div>` (1) + `collection` pair (1) = 2 → 8 sp.
     */
    fun testVoidAfterAntlersAttribute() {
        assertEquals(
            "<div>\n    {{ collection:blog }}\n        <img src=\"{{ image }}\" alt=\"x\">\n        <br>\n    {{ /collection }}\n</div>",
            reformat("<div>\n{{ collection:blog }}\n<img src=\"{{ image }}\" alt=\"x\">\n<br>\n{{ /collection }}\n</div>")
        )
    }
}
