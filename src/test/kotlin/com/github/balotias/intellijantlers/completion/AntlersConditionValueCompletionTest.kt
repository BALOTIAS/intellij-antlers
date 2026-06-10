package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersConditionValueCompletionTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    // The `status` field's condition value completes to the entry statuses.
    fun testStatusValuesCompleted() {
        val l = lookups("{{ collection:blog status:is=\"<caret>\" }}")
        assertTrue("published offered: $l", l.contains("published"))
        assertTrue("draft offered: $l", l.contains("draft"))
    }

    // A truthy/boolean operator (exists/doesnt_exist/…) takes true/false.
    fun testBooleanValuesForExists() {
        val l = lookups("{{ collection:blog hero:exists=\"<caret>\" }}")
        assertTrue("true offered: $l", l.contains("true"))
        assertTrue("false offered: $l", l.contains("false"))
    }

    // A date operator takes date keywords.
    fun testDateValuesForIsAfter() {
        val l = lookups("{{ collection:blog date:is_after=\"<caret>\" }}")
        assertTrue("now offered: $l", l.contains("now"))
    }

    // A genuine tag parameter value (not a condition) is unaffected — no condition values leak in.
    fun testRealParamValueNotConditionValues() {
        val l = lookups("{{ collection from=\"<caret>\" }}")
        assertFalse("status values must not leak into a normal param: $l", l.contains("published"))
    }
}
