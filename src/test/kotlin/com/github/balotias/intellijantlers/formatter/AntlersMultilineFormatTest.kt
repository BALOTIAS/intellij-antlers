package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMultilineFormatTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()  // sync PSI with the doc edits
        }
        return file.text
    }

    /** The active indent unit — call AFTER reformat() has configured the fixture (NPE otherwise). */
    private fun unit(): String {
        val opts = CodeStyle.getIndentOptions(myFixture.file)
        return if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
    }

    fun testMultilineCloserNotPulledUp() {
        val out = reformat("{{ collection:blog\nlimit=\"3\"\n}}")
        assertTrue("closer stays on its own line, got:\n$out", out.trimEnd().endsWith("\n}}"))
        assertFalse("closer not joined to the last param, got:\n$out", out.contains("\"3\" }}"))
    }

    fun testCanonicalMultiline() {
        val out = reformat("{{ collection:blog\nlimit=\"3\"\nas=\"posts\"\n}}")
        val u = unit()
        assertEquals("{{ collection:blog\n${u}limit=\"3\"\n${u}as=\"posts\"\n}}", out)
    }

    // The spacing pass collapses the opener-line gap BEFORE this processor runs; params must still be
    // indented in a SINGLE reformat (regression: stale PSI offsets used to skip the whole statement).
    fun testCollapsibleOpenerSpacingStillIndentsInOnePass() {
        val out = reformat("{{   collection:blog\nlimit=\"3\"\n}}")
        val u = unit()
        assertEquals("{{ collection:blog\n${u}limit=\"3\"\n}}", out)
    }

    fun testCollapsiblePipeSpacingStillIndentsInOnePass() {
        val out = reformat("{{ a  |  b\nlimit=\"3\"\n}}")
        val u = unit()
        assertEquals("{{ a | b\n${u}limit=\"3\"\n}}", out)
    }

    fun testIdempotentFromUnnormalizedInput() {
        val once = reformat("{{   collection:blog\nlimit=\"3\"\n}}")
        val twice = reformat(once)
        assertEquals(once, twice)
    }

    fun testOverwritesExistingIndent() {
        val out = reformat("{{ collection:blog\n        limit=\"3\"\n}}")   // 8 leading spaces
        val u = unit()
        assertEquals("{{ collection:blog\n${u}limit=\"3\"\n}}", out)
    }

    fun testIdempotent() {
        val once = reformat("{{ collection:blog\nlimit=\"3\"\n}}")
        val twice = reformat(once)
        assertEquals(once, twice)
    }

    fun testMultilineInsideElement() {
        val out = reformat("<div>\n{{ collection:blog\nlimit=\"3\"\n}}\n</div>")
        val u = unit()
        assertEquals("<div>\n$u{{ collection:blog\n$u${u}limit=\"3\"\n$u}}\n</div>", out)
    }

    fun testSingleLineUntouched() {
        assertEquals("{{ collection:blog limit=\"3\" }}", reformat("{{ collection:blog limit=\"3\" }}"))
    }

    fun testCloserInlineWithLastParam() {
        val out = reformat("{{ collection:blog\nlimit=\"3\" }}")
        val u = unit()
        assertEquals("{{ collection:blog\n${u}limit=\"3\" }}", out)
    }

    fun testMultilineStringValueNotReindented() {
        // A param string value that spans lines must keep its inner content verbatim.
        val out = reformat("{{ partial:src=\"a\nb\" }}")
        assertTrue("multi-line string content preserved, got:\n$out", out.contains("\"a\nb\""))
    }

    fun testMultilineBracketNesting() {
        val out = reformat("{{\n[\n'a' => 1,\n'b' => 2,\n] | classes\n}}")
        val u = unit()
        assertEquals(
            "{{\n${u}[\n${u}${u}'a' => 1,\n${u}${u}'b' => 2,\n${u}] | classes\n}}",
            out
        )
    }

    fun testBracketCharInsideStringDoesNotShiftDepth() {
        // The `[` inside the string must NOT count toward bracket depth.
        val out = reformat("{{\nx = \"a[b\",\ny = 2\n}}")
        val u = unit()
        assertEquals("{{\n${u}x = \"a[b\",\n${u}y = 2\n}}", out)
    }
}
