package com.github.balotias.intellijantlers.references

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialParamsFileTest : BasePlatformTestCase() {

    private val button = """
        {{#
            @name Button attributes
            @desc A single button component.
            @param* label The caption label.
            @param as The wrapping element. Defaults to `a`.
            @param button_type `Inline` if the button needs to be rendered as an inline button.
            @param faux Boolean. For faux button wrapped in an actual button/anchor.
        #}}

        <!-- /components/_button.antlers.html -->
        {{ if label }}<span>{{ label }}</span>{{ /if }}
    """.trimIndent()

    fun testReadsParamsInOrder() {
        val file = myFixture.addFileToProject("resources/views/components/_button.antlers.html", button)
        val params = AntlersPartialParams.of(file)
        assertEquals(listOf("label", "as", "button_type", "faux"), params.map { it.name })
        assertTrue("label is required", params.first { it.name == "label" }.required)
        assertFalse("as is optional", params.first { it.name == "as" }.required)
        assertEquals("The wrapping element. Defaults to `a`.", params.first { it.name == "as" }.description)
    }

    fun testNoDirectiveCommentYieldsEmpty() {
        val file = myFixture.addFileToProject("resources/views/components/_plain.antlers.html", "<div>no hints</div>")
        assertTrue(AntlersPartialParams.of(file).isEmpty())
    }
}
