package com.github.balotias.intellijantlers.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * When an HTML tag *name* is an Antlers interpolation (`<{{ as or 'h2' }}> … </{{ as or 'h2' }}>`),
 * the HTML data tree only sees placeholder tag names and wrongly reports the closer as unmatched.
 * The interpolation-aware HighlightInfoFilter must suppress that false structural error.
 */
class AntlersInterpolatedHtmlTagTest : BasePlatformTestCase() {

    private fun descriptions(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting().mapNotNull { it.description }
    }

    fun testInterpolatedClosingTagNotFlagged() {
        val d = descriptions("<{{ as or 'h2' }} class=\"x\">{{ content }}</{{ as or 'h2' }}>")
        assertFalse("false 'Closing tag matches nothing' on interpolated tag: $d",
            d.any { it.contains("Closing tag matches nothing") })
    }

    fun testRealUnmatchedClosingTagStillFlagged() {
        val d = descriptions("<div>hello</span>")
        assertTrue("a genuinely unmatched </span> must still be reported: $d",
            d.any { it.contains("Closing tag matches nothing") })
    }
}
