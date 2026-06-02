package com.github.balotias.intellijantlers.template

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersLiveTemplatesTest : BasePlatformTestCase() {

    fun testInContextForAntlersFile() {
        myFixture.configureByText("page.antlers.html", "{{ x }}")
        val ctx = TemplateActionContext.expanding(myFixture.file, myFixture.editor)
        assertTrue(AntlersTemplateContextType().isInContext(ctx))
    }

    fun testNotInContextForPlainText() {
        myFixture.configureByText("note.txt", "hello")
        val ctx = TemplateActionContext.expanding(myFixture.file, myFixture.editor)
        assertFalse(AntlersTemplateContextType().isInContext(ctx))
    }

    fun testBundledTemplatesParseAndAreAntlersScoped() {
        val text = javaClass.classLoader.getResourceAsStream("liveTemplates/Antlers.xml")
            ?.bufferedReader()?.readText() ?: error("liveTemplates/Antlers.xml not on classpath")
        listOf("if", "ife", "unless", "coll", "partial", "pair").forEach { abbrev ->
            assertTrue("missing abbreviation '$abbrev': $text", text.contains("name=\"$abbrev\""))
        }
        assertTrue("templates must carry the ANTLERS context: $text",
            text.contains("name=\"ANTLERS\" value=\"true\""))
    }
}
