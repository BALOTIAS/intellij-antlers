package com.github.balotias.intellijantlers.formatter

import com.intellij.lang.html.HTMLLanguage
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersHtmlNestingTest : BasePlatformTestCase() {

    private fun compute(text: String): AntlersHtmlNesting.Result {
        val file = myFixture.configureByText("p.antlers.html", text)
        val html = file.viewProvider.getPsi(HTMLLanguage.INSTANCE)
        val doc = file.viewProvider.document!!
        return AntlersHtmlNesting.compute(doc, html)
    }

    fun testNestedDivSpanDepth() {
        val r = compute("<div>\n<span>\nx\n</span>\n</div>")
        assertEquals(0, r.depth[0])   // <div>
        assertEquals(1, r.depth[1])   // <span> inside div
        assertEquals(2, r.depth[2])   // x inside div+span
        assertEquals(1, r.depth[3])   // </span> inside div
        assertEquals(0, r.depth[4])   // </div>
    }

    fun testVoidElementAddsNoDepth() {
        val r = compute("<div>\n<br>\nx\n</div>")
        assertEquals(1, r.depth[1])   // <br>
        assertEquals(1, r.depth[2])   // x — only div, not br
    }

    fun testPreInteriorPreserved() {
        val r = compute("<pre>\n  keep me\n</pre>")
        assertTrue("pre interior preserved", r.preserve.contains(1))
        assertFalse("pre open line not preserved", r.preserve.contains(0))
    }

    fun testContentOnOpenLineThenCloseLine() {
        // <div>x  /  </div> — sLine=0, eLine=1, so the interior range (1 until 1) is empty: there is no
        // interior line, and the close line sits at the enclosing level (0).
        val r = compute("<div>x\n</div>")
        assertEquals(0, r.depth[0])
        assertEquals(0, r.depth[1])
    }

    fun testNullHtmlYieldsZeros() {
        val r = AntlersHtmlNesting.compute(
            myFixture.configureByText("p.antlers.html", "<div>\nx\n</div>").viewProvider.document!!,
            null
        )
        assertTrue(r.depth.all { it == 0 })
        assertTrue(r.preserve.isEmpty())
    }
}
