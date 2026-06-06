package com.github.balotias.intellijantlers

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Parser-level regressions for real-world Antlers tag syntax that previously produced parse errors:
 *  - closing tags whose path uses '/' separators (`{{ /partial:components/notification }}`), and
 *  - the `%` tag-disambiguation prefix (`{{ %form:fields }}` / `{{ /%form:fields }}`).
 *
 * Each asserts the Antlers PSI tree has no [PsiErrorElement]; the modulo/division cases guard against
 * regressing arithmetic when `%` became its own token.
 */
class AntlersTagSyntaxTest : BasePlatformTestCase() {

    private fun parseErrors(text: String): List<String> {
        val file = myFixture.configureByText("p.antlers.html", text)
        return PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
            .map { it.errorDescription + " @ '" + it.text + "'" }
    }

    fun testSlashPathClosingPartialParsesClean() =
        assertEquals(emptyList<String>(), parseErrors("{{ /partial:components/notification }}"))

    fun testSlashPathClosingPartialMultiSegmentParsesClean() =
        assertEquals(emptyList<String>(), parseErrors("{{ /partial:page_builder/blocks/card }}"))

    fun testPercentTagPrefixParsesClean() =
        assertEquals(emptyList<String>(), parseErrors("{{ %form:fields scope=\"field\" }}{{ /%form:fields }}"))

    fun testModuloStillParses() =
        assertEquals(emptyList<String>(), parseErrors("{{ a % b }}"))

    fun testModuloAssignStillParses() =
        assertEquals(emptyList<String>(), parseErrors("{{ a %= b }}"))

    fun testDivisionStillParses() =
        assertEquals(emptyList<String>(), parseErrors("{{ total / count }}"))

    fun testOrdinaryClosingTagsUnaffected() =
        assertEquals(emptyList<String>(), parseErrors("{{ if x }}{{ /if }}{{ collection:blog }}{{ /collection }}"))
}
