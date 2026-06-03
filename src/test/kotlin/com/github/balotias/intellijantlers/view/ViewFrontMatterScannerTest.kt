package com.github.balotias.intellijantlers.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewFrontMatterScannerTest {

    private fun fm(text: String) = ViewFrontMatterScanner.scan(text)

    @Test fun basicTopLevelKeys() {
        val r = fm("---\nfoo: bar\ntitle: \"My Page\"\n---\n{{ view:foo }}")!!
        assertEquals(listOf("foo", "title"), r.entries.filter { it.indent == 0 }.map { it.name })
        assertEquals("bar", r.entries[0].valuePreview)
        assertEquals("My Page", r.entries[1].valuePreview)        // surrounding quotes stripped
    }

    @Test fun keyAndValueOffsetsAreCorrect() {
        val text = "---\nfoo: bar\n---\n"
        val e = fm(text)!!.entries.single()
        assertEquals("foo", text.substring(e.key.start, e.key.end))
        assertEquals("bar", text.substring(e.value!!.start, e.value!!.end))
        assertEquals(4, e.key.start)                              // after "---\n"
    }

    @Test fun fenceSpans() {
        val text = "---\nfoo: bar\n---\nbody"
        val r = fm(text)!!
        assertEquals("---", text.substring(r.openFence.start, r.openFence.end))
        assertEquals("---", text.substring(r.closeFence.start, r.closeFence.end))
        assertEquals(0, r.openFence.start)
    }

    @Test fun nestedKeysKeptButNotTopLevel() {
        val r = fm("---\nfoo: bar\nnested:\n  child: x\n---\n")!!
        assertEquals(listOf("foo", "nested", "child"), r.entries.map { it.name })
        assertEquals(listOf("foo", "nested"), r.entries.filter { it.indent == 0 }.map { it.name })
    }

    @Test fun keyWithoutValueHasNullValueSpan() {
        val e = fm("---\nempty:\n---\n")!!.entries.single()
        assertNull(e.value)
        assertEquals("", e.valuePreview)
    }

    @Test fun noLeadingFenceReturnsNull() {
        assertNull(fm("{{ title }}\nfoo: bar"))
        assertNull(fm("<div>---</div>\nfoo: bar\n---"))            // --- not on line 0
    }

    @Test fun unclosedFrontMatterReturnsNull() {
        assertNull(fm("---\nfoo: bar\n"))
    }
}
