package com.github.balotias.intellijantlers.editor

import com.intellij.application.options.CodeStyle
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersEnterHandlerTest : BasePlatformTestCase() {

    /** Type one Enter at the caret and return the resulting document text. */
    private fun enter(textWithCaret: String): String {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.type("\n")
        return myFixture.editor.document.text
    }

    /** One indent unit per the file's code style (tab or N spaces). Call after [enter]/configure. */
    private fun unit(): String {
        val o = CodeStyle.getIndentOptions(myFixture.file)
        return if (o.USE_TAB_CHARACTER) "\t" else " ".repeat(o.INDENT_SIZE)
    }

    fun testExpandsColZeroBlock() {
        myFixture.configureByText("p.antlers.html", "{{ collection }}<caret>{{ /collection }}")
        myFixture.type("\n")
        assertEquals("{{ collection }}\n${unit()}\n{{ /collection }}", myFixture.editor.document.text)
        assertEquals("{{ collection }}\n${unit()}".length, myFixture.caretOffset)
    }

    fun testExpandsIndentedOpener() {
        // Evaluate enter() first so unit() reads the configured file (matching the handler's options).
        val actual = enter("  {{ if x }}<caret>{{ /if }}")
        assertEquals("  {{ if x }}\n  ${unit()}\n  {{ /if }}", actual)
    }

    fun testWhitespaceBetweenNormalizes() {
        val actual = enter("{{ collection }} <caret> {{ /collection }}")
        assertEquals("{{ collection }}\n${unit()}\n{{ /collection }}", actual)
    }

    fun testExpandsLogicBlock() {
        val actual = enter("{{ unless x }}<caret>{{ /unless }}")
        assertEquals("{{ unless x }}\n${unit()}\n{{ /unless }}", actual)
    }

    fun testNonEmptyBlockDoesNotFire() {
        val r = enter("{{ collection }}foo<caret>{{ /collection }}")
        assertEquals("only the default newline was inserted", 1, r.count { it == '\n' })
    }

    fun testAlreadyMultilineDoesNotDoubleExpand() {
        val r = enter("{{ collection }}\n<caret>\n{{ /collection }}")
        assertEquals("started with 2 newlines, default Enter adds one", 3, r.count { it == '\n' })
    }
}
