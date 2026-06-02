package com.github.balotias.intellijantlers.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFileTemplateTest : BasePlatformTestCase() {

    fun testInternalTemplateContent() {
        val text = javaClass.classLoader
            .getResourceAsStream("fileTemplates/internal/Antlers Template.antlers.html.ft")
            ?.bufferedReader()?.readText()
            ?: error("internal file template not on classpath")
        assertTrue("starter contains a body placeholder: $text", text.contains("template_content"))
        assertTrue("starter is an html skeleton: $text", text.contains("<html"))
    }

    fun testActionPresentation() {
        val action = CreateAntlersFileAction()
        assertEquals("Antlers Template", action.templatePresentation.text)
    }
}
