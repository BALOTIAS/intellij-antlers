package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersPhpBlockBody
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPhpInjectionTest : BasePlatformTestCase() {

    private fun phpBody(text: String): AntlersPhpBlockBody {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.findChildOfType(antlers, AntlersPhpBlockBody::class.java)!!
    }

    fun testEchoBlockUsesShortEchoPrefix() {
        assertEquals("<?= ", AntlersPhpInjector.prefixFor(phpBody("{{\$ \$x \$}}")))
    }

    fun testRawBlockUsesPhpOpenPrefix() {
        assertEquals("<?php ", AntlersPhpInjector.prefixFor(phpBody("{{? \$x = 1; ?}}")))
    }

    fun testNoInjectionWithoutPhpPlugin() {
        // CI (IDEA Community) has no PHP plugin -> the injector no-ops gracefully (no crash, no injection).
        myFixture.configureByText("p.antlers.html", "{{? \$x = 1; ?}}")
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val ilm = InjectedLanguageManager.getInstance(project)
        assertNull(ilm.findInjectedElementAt(antlers, antlers.text.indexOf("\$x")))
    }
}
