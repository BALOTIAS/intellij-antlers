package com.github.balotias.intellijantlers.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFormatterSettingsTest : BasePlatformTestCase() {
    override fun tearDown() {
        try { AntlersFormatterSettings.getInstance(project).reformatEnabled = true } finally { super.tearDown() }
    }

    fun testDefaultIsEnabled() {
        assertTrue(AntlersFormatterSettings.getInstance(project).reformatEnabled)
    }

    fun testStateRoundTrips() {
        val s = AntlersFormatterSettings.getInstance(project)
        s.reformatEnabled = false
        val loaded = AntlersFormatterSettings().also { it.loadState(s.state) }
        assertFalse(loaded.reformatEnabled)
    }
}
