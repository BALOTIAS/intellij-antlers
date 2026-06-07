package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersIndentTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    private fun u(): String {
        val f = myFixture.file ?: myFixture.configureByText("p.antlers.html", "")
        val o = CodeStyle.getIndentOptions(f)
        return if (o.USE_TAB_CHARACTER) "\t" else " ".repeat(o.INDENT_SIZE)
    }

    private fun assertStable(src: String) {
        val a = reformat(src); val b = reformat(a)
        assertEquals("not idempotent:\n$a", a, b)
    }

    fun testHtmlInsideTwoAntlersPairs() {
        val u = u()
        val out = reformat("{{ if a }}\n{{ if b }}\n<div>\n<span>x</span>\n</div>\n{{ /if }}\n{{ /if }}")
        assertEquals(
            "{{ if a }}\n${u}{{ if b }}\n${u}${u}<div>\n${u}${u}${u}<span>x</span>\n${u}${u}</div>\n${u}{{ /if }}\n{{ /if }}",
            out
        )
    }

    fun testAntlersInsideHtmlInsideAntlers() {
        val u = u()
        val out = reformat("{{ collection:x }}\n<div>\n{{ if a }}\n<span>y</span>\n{{ /if }}\n</div>\n{{ /collection }}")
        assertEquals(
            "{{ collection:x }}\n${u}<div>\n${u}${u}{{ if a }}\n${u}${u}${u}<span>y</span>\n${u}${u}{{ /if }}\n${u}</div>\n{{ /collection }}",
            out
        )
    }

    fun testPureHtmlIndentsByHtmlNesting() {
        val u = u()
        assertEquals("<div>\n${u}<span>x</span>\n</div>", reformat("<div>\n<span>x</span>\n</div>"))
    }

    fun testPreInteriorUntouched() {
        val out = reformat("<div>\n<pre>\n      keep\n</pre>\n</div>")
        val u = u()
        // <pre> indented as html child, but its interior line keeps its original whitespace
        assertEquals("<div>\n${u}<pre>\n      keep\n${u}</pre>\n</div>", out)
    }

    fun testIdempotentMatrix() {
        assertStable("{{ if a }}\n{{ if b }}\n<div>\n<span>x</span>\n</div>\n{{ /if }}\n{{ /if }}")
        assertStable("{{ collection:x }}\n<div>\n{{ if a }}\n<span>y</span>\n{{ /if }}\n</div>\n{{ /collection }}")
        assertStable("<{{ as or 'a' }}>\n<span>x</span>\n</{{ as or 'a' }}>")
    }

    fun testElseDedents() {
        val u = u()
        assertEquals(
            "{{ if a }}\n${u}x\n{{ else }}\n${u}y\n{{ /if }}",
            reformat("{{ if a }}\nx\n{{ else }}\ny\n{{ /if }}")
        )
    }

    fun testEmptyDocument() {
        assertEquals("", reformat(""))
    }

    fun testSingleLineTagUntouched() {
        assertEquals("{{ if a }}x{{ /if }}", reformat("{{ if a }}x{{ /if }}"))
    }

    fun testInlineTagAddsLevel() {
        val u = u()
        assertEquals("<a href=\"x\">\n${u}link\n</a>", reformat("<a href=\"x\">\nlink\n</a>"))
    }

    fun testMultilineParamsIndentUnderOpener() {
        val u = u()
        val out = reformat("{{ collection:blog\nlimit=\"3\"\n}}\n{{ /collection }}")
        assertEquals("{{ collection:blog\n${u}limit=\"3\"\n}}\n{{ /collection }}", out)
    }

    fun testNoparseInteriorPreserved() {
        val out = reformat("{{ if a }}\n{{ noparse }}\n      raw {{ x }}\n{{ /noparse }}\n{{ /if }}")
        val u = u()
        assertEquals("{{ if a }}\n${u}{{ noparse }}\n      raw {{ x }}\n${u}{{ /noparse }}\n{{ /if }}", out)
    }

    fun testMultilineStringNotReindented() {
        // Uses an Antlers T_STRING spanning multiple lines; the interior lines must not be reindented.
        val out = reformat("{{ if a }}\n{{ x param=\"\nfoo\nbar\n\" }}\n{{ /if }}")
        assertTrue("string interior preserved", out.contains("\nfoo\nbar\n"))
    }

    fun testTemplateNamedTagBodyIndents() {
        val u = u()
        assertEquals("<{{ as or 'a' }}>\n${u}<span>x</span>\n</{{ as or 'a' }}>",
            reformat("<{{ as or 'a' }}>\n<span>x</span>\n</{{ as or 'a' }}>"))
    }

    fun testStrayCloserDoesNotCrashOrRunaway() {
        val out = reformat("{{ /collection }}\n<div>\nx\n</div>")
        assertStable(out)
    }

    fun testCommentInteriorPreserved() {
        val u = u()
        val out = reformat("{{ if a }}\n{{#\n   keep comment\n#}}\n{{ /if }}")
        assertEquals("{{ if a }}\n${u}{{#\n   keep comment\n${u}#}}\n{{ /if }}", out)
    }

    fun testPhpBlockInteriorPreserved() {
        val u = u()
        val out = reformat("{{ if a }}\n{{\$\n   \$x = 1;\n\$}}\n{{ /if }}")
        assertEquals("{{ if a }}\n${u}{{\$\n   \$x = 1;\n${u}\$}}\n{{ /if }}", out)
    }

    // #regression: a nested if's {{ else }} must stay at its own if's level (still inside the outer if),
    // not dedent all the way out.
    fun testNestedElseStaysAtItsIfLevel() {
        val u = u()
        val out = reformat("{{ if a }}\n{{ if b }}\nx\n{{ else }}\ny\n{{ /if }}\n{{ /if }}")
        assertEquals(
            "{{ if a }}\n${u}{{ if b }}\n${u}${u}x\n${u}{{ else }}\n${u}${u}y\n${u}{{ /if }}\n{{ /if }}",
            out
        )
    }

    // A loop over an arbitrary variable (`{{ buttons }}…{{ /buttons }}`) indents its body, even though
    // `buttons` is not a catalog pair tag.
    fun testLoopVariableBodyIndents() {
        val u = u()
        val out = reformat("{{ buttons }}\n{{ partial:components/button }}\n{{ /buttons }}")
        assertEquals("{{ buttons }}\n${u}{{ partial:components/button }}\n{{ /buttons }}", out)
    }

    fun testNestedLoopInsideHtmlInsideIf() {
        val u = u()
        val out = reformat("{{ if a }}\n<div>\n{{ buttons }}\n{{ partial:x }}\n{{ /buttons }}\n</div>\n{{ /if }}")
        assertEquals(
            "{{ if a }}\n${u}<div>\n${u}${u}{{ buttons }}\n${u}${u}${u}{{ partial:x }}\n${u}${u}{{ /buttons }}\n${u}</div>\n{{ /if }}",
            out
        )
    }

    // A non-pair tag with no closer must NOT indent everything after it (permissive pairing must discard
    // unclosed unknown openers).
    fun testUnclosedUnknownDoesNotIndentFollowing() {
        val u = u()
        val out = reformat("{{ title }}\n<div>\nx\n</div>")
        assertEquals("{{ title }}\n<div>\n${u}x\n</div>", out)
    }
}
