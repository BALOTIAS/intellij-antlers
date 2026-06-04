package com.github.balotias.intellijantlers.highlighting

import com.github.balotias.intellijantlers.psi.AntlersTypes
import org.junit.Assert.assertTrue
import org.junit.Test

class AntlersFrontMatterFenceHighlightTest {
    @Test fun fenceTokenUsesFenceColor() {
        val keys = AntlersSyntaxHighlighter().getTokenHighlights(AntlersTypes.T_FRONTMATTER_FENCE)
        assertTrue("--- fence is FRONTMATTER_FENCE-colored", keys.contains(AntlersSyntaxHighlighter.FRONTMATTER_FENCE))
    }
}
