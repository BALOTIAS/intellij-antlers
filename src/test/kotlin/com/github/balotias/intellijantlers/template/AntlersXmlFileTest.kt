package com.github.balotias.intellijantlers.template

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * `.antlers.xml` views (sitemaps, RSS/Atom feeds) are Antlers layered over XML — the markup gets XML
 * tooling while the `{{ }}` stay Antlers. `.antlers.html` keeps HTML as before.
 */
class AntlersXmlFileTest : BasePlatformTestCase() {

    private fun errors(name: String, text: String): List<String> {
        myFixture.configureByText(name, text)
        return myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WARNING }.map { it.description ?: "" }
    }

    fun testXmlIsTheDataLanguage() {
        myFixture.configureByText("sitemap.antlers.xml", "<urlset>{{ permalink }}</urlset>")
        val vp = myFixture.file.viewProvider
        assertTrue("an Antlers view provider", vp is AntlersFileViewProvider)
        assertNotNull("XML data PSI present for .antlers.xml", vp.getPsi(XMLLanguage.INSTANCE))
        assertNull("HTML must not be the data language for .antlers.xml", vp.getPsi(HTMLLanguage.INSTANCE))
    }

    fun testHtmlStaysTheDataLanguageForHtml() {
        myFixture.configureByText("page.antlers.html", "<div>{{ x }}</div>")
        val vp = myFixture.file.viewProvider
        assertNotNull("HTML data PSI present for .antlers.html", vp.getPsi(HTMLLanguage.INSTANCE))
    }

    fun testAntlersIsParsedInXml() {
        myFixture.configureByText("sitemap.antlers.xml", "<urlset>{{ permalink }}{{ if x }}a{{ /if }}</urlset>")
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        assertNotNull(antlers)
        assertTrue("Antlers statements parsed inside the XML view",
            PsiTreeUtil.findChildrenOfType(antlers!!, AntlersStatement::class.java).isNotEmpty())
    }

    // Conditionally-emitted XML elements (the sitemap shape) must not look malformed to the XML parser.
    fun testConditionalXmlHasNoFalseErrors() {
        val e = errors("sitemap.antlers.xml",
            "<urlset>\n{{ collection from=\"pages\" as=\"r\" }}{{ r }}{{ if permalink }}" +
                "<url><loc>{{ permalink }}</loc></url>{{ /if }}{{ /r }}{{ /collection }}\n</urlset>")
        assertTrue("conditional XML must not produce false errors: $e", e.isEmpty())
    }

    // Antlers standing in tag-attribute position (`<urlset {{ yield:namespace }}>`) must not error.
    fun testAntlersInTagAttributesHasNoFalseErrors() {
        val e = errors("sitemap.antlers.xml", "<urlset {{ yield:namespace }}>\n<url/>\n</urlset>")
        assertTrue("Antlers in tag-attribute position must not produce false errors: $e", e.isEmpty())
    }
}
