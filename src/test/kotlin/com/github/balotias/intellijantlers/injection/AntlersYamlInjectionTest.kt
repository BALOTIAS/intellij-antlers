package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.YAMLLanguage

class AntlersYamlInjectionTest : BasePlatformTestCase() {

    private fun antlersFileFor(text: String) = run {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
    }

    fun testYamlInjectedIntoFrontMatter() {
        val text = "---\nname: ''\nfilled: false\n---\n{{ title }}"
        val antlers = antlersFileFor(text)
        val ilm = InjectedLanguageManager.getInstance(project)
        val injected = ilm.findInjectedElementAt(antlers, text.indexOf("name"))
        assertNotNull("YAML is injected at the front-matter key", injected)
        assertEquals(YAMLLanguage.INSTANCE, injected!!.containingFile.language)
    }

    fun testNoInjectionOutsideFrontMatter() {
        val text = "---\nname: ''\n---\n{{ title }}"
        val antlers = antlersFileFor(text)
        val ilm = InjectedLanguageManager.getInstance(project)
        assertNull("the body is not YAML", ilm.findInjectedElementAt(antlers, text.indexOf("title")))
    }

    fun testNoInjectionWhenNoFrontMatter() {
        val text = "{{ title }}\nname: ''"
        val antlers = antlersFileFor(text)
        val ilm = InjectedLanguageManager.getInstance(project)
        assertNull("no front matter → no YAML", ilm.findInjectedElementAt(antlers, text.indexOf("name")))
    }
}
