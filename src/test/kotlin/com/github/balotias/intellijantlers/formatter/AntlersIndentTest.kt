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
}
