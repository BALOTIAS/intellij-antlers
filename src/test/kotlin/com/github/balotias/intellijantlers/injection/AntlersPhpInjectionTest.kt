package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersPhpBlockBody
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiLanguageInjectionHost
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

    fun testTagBlockUsesPhpOpenPrefix() {
        assertEquals("<?php ", AntlersPhpInjector.prefixFor(phpBody("<?php \$x = 1; ?>")))
    }

    fun testEchoTagBlockUsesShortEchoPrefix() {
        assertEquals("<?= ", AntlersPhpInjector.prefixFor(phpBody("<?= \$name ?>")))
    }

    fun testNoInjectionWithoutPhpPlugin() {
        // CI (IDEA Community) has no PHP plugin -> the injector no-ops gracefully (no crash, no injection).
        myFixture.configureByText("p.antlers.html", "{{? \$x = 1; ?}}")
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val ilm = InjectedLanguageManager.getInstance(project)
        assertNull(ilm.findInjectedElementAt(antlers, antlers.text.indexOf("\$x")))
    }

    /** Injected-fragment write-back (PsiLanguageInjectionHost.updateText -> the manipulator). */
    private fun updateBody(text: String, newContent: String): String {
        val body = phpBody(text)
        return WriteCommandAction.runWriteCommandAction<PsiLanguageInjectionHost>(project) {
            body.updateText(newContent)
        }.text
    }

    fun testWriteBackReplacesBody() {
        // Would silently no-op if the synthetic template padded $body with spaces (the round-trip guard).
        assertEquals(" \$y = 2; ", updateBody("{{? \$x = 1; ?}}", " \$y = 2; "))
    }

    fun testWriteBackBailsWhenContentWouldCloseTheBlock() {
        // New content containing `?}}` would re-close the synthetic block early -> bail, leave body intact.
        assertEquals(" \$x = 1; ", updateBody("{{? \$x = 1; ?}}", "\$a ?}} \$b"))
    }
}
