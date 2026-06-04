package com.github.balotias.intellijantlers.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersSettingsConfigurableTest : BasePlatformTestCase() {
    override fun tearDown() {
        try { AntlersFormatterSettings.getInstance(project).reformatEnabled = true } finally { super.tearDown() }
    }

    fun testNotModifiedRightAfterReset() {
        AntlersFormatterSettings.getInstance(project).reformatEnabled = false
        val c = AntlersSettingsConfigurable(project)
        assertNotNull(c.createComponent())
        c.reset()
        assertFalse("checkbox reflects settings after reset, so nothing is modified", c.isModified)
    }
}
