package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersQueryOperatorCompletionTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    // After a plain expression, the query/builder operators are valid next tokens — offer them.
    // (Empty prefix so the full list is returned rather than auto-inserted on a single match.)
    fun testQueryOperatorsOfferedAfterExpression() {
        val l = lookups("{{ players <caret> }}")
        assertTrue("where offered: $l", l.contains("where"))
        assertTrue("take offered: $l", l.contains("take"))
        assertTrue("merge offered: $l", l.contains("merge"))
    }

    // A real tag head takes parameters, not query operators — don't pollute its completion.
    fun testQueryOperatorsNotOfferedAfterTagHead() {
        val l = lookups("{{ collection w<caret> }}")
        assertFalse("where must not be offered after a tag head: $l", l.contains("where"))
    }

    // At the head position there's no left operand, so operators don't apply.
    fun testQueryOperatorsNotOfferedAtHead() {
        val l = lookups("{{ w<caret> }}")
        assertFalse("where must not be offered at head: $l", l.contains("where"))
    }
}
