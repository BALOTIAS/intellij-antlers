package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersBlockIndentTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    private fun unit(): String {
        val opts = CodeStyle.getIndentOptions(myFixture.file)
        return if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
    }

    fun testIfBodyIndented() {
        val out = reformat("{{ if x }}\n{{ title }}\n{{ /if }}")
        val u = unit()
        assertEquals("{{ if x }}\n${u}{{ title }}\n{{ /if }}", out)
    }

    fun testNestedBlocksCompound() {
        val out = reformat("{{ if a }}\n{{ if b }}\n{{ title }}\n{{ /if }}\n{{ /if }}")
        val u = unit()
        assertEquals(
            "{{ if a }}\n${u}{{ if b }}\n${u}${u}{{ title }}\n${u}{{ /if }}\n{{ /if }}",
            out
        )
    }

    fun testCollectionPairTagBodyIndented() {
        // `collection` opens because the catalog marks it isPair (the project service is available in tests).
        val out = reformat("{{ collection:blog }}\n{{ title }}\n{{ /collection }}")
        val u = unit()
        assertEquals("{{ collection:blog }}\n${u}{{ title }}\n{{ /collection }}", out)
    }

    fun testHtmlBodyLineShiftsAtLeastOneLevel() {
        // The HTML formatter owns the HTML line's base; we only assert it gains the Antlers level and that
        // the opener/closer stay put — exact column is HTML-formatter dependent (documented).
        val out = reformat("{{ if x }}\n<span>hi</span>\n{{ /if }}")
        val u = unit()
        val lines = out.split("\n")
        assertEquals("{{ if x }}", lines[0])
        assertTrue("html body indented at least one level, got: <${lines[1]}>", lines[1].startsWith(u))
        assertTrue("html body content present", lines[1].trim() == "<span>hi</span>")
        assertEquals("{{ /if }}", lines.last())
    }

    fun testMultilineParamInsideBlockComposes() {
        // A multi-line opener inside a pair: block sets the opener line indent, multiline indents its params
        // one level under that — they must compound.
        val out = reformat("{{ if x }}\n{{ collection:blog\nlimit=\"3\"\n}}\n{{ /if }}")
        val u = unit()
        assertEquals(
            "{{ if x }}\n${u}{{ collection:blog\n${u}${u}limit=\"3\"\n${u}}}\n{{ /if }}",
            out
        )
        assertEquals("multiline-in-pair reformat is a fixed point", out, reformat(out))
    }

    fun testIdempotentAntlers() {
        val once = reformat("{{ if a }}\n{{ if b }}\n{{ title }}\n{{ /if }}\n{{ /if }}")
        val twice = reformat(once)
        assertEquals(once, twice)
    }

    fun testIdempotentMixedHtml() {
        val once = reformat("{{ collection:blog }}\n<article>\n<h2>{{ title }}</h2>\n</article>\n{{ /collection }}")
        val twice = reformat(once)
        assertEquals("reformat must be a fixed point (no runaway), got:\n$twice", once, twice)
    }

    fun testOptOutLeavesBodyUntouched() {
        val settings = com.github.balotias.intellijantlers.settings.AntlersFormatterSettings.getInstance(project)
        val prev = settings.reformatEnabled
        settings.reformatEnabled = false
        try {
            val src = "{{ if x }}\n{{ title }}\n{{ /if }}"
            assertEquals(src, reformat(src))
        } finally {
            settings.reformatEnabled = prev
        }
    }

    fun testTopLevelContentUntouched() {
        // No paired block → processor is a no-op; a plain {{ }} + text keep column 0.
        val out = reformat("{{ title }}\nplain text")
        assertEquals("{{ title }}\nplain text", out)
    }

    fun testUnclosedIfDoesNotCrashAndIndentsBody() {
        val out = reformat("{{ if x }}\n{{ title }}")
        val u = unit()
        assertEquals("{{ if x }}\n${u}{{ title }}", out)
    }

    fun testStrayCloserDoesNotCrash() {
        // An unmatched closer contributes no depth; surrounding content is untouched, no exception.
        val out = reformat("{{ /collection }}\n{{ title }}")
        assertEquals("{{ /collection }}\n{{ title }}", out)
    }
}
