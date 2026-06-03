package com.github.balotias.intellijantlers.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StatamicVersionServiceTest : BasePlatformTestCase() {

    private fun version() = StatamicVersionService.getInstance(project).majorVersion()

    fun testDefaultsToLatestWhenNoComposer() {
        assertEquals(StatamicVersionService.LATEST_MAJOR, version())
    }

    fun testDetectsFive() {
        myFixture.addFileToProject("composer.json", """{ "require": { "statamic/cms": "^5.0" } }""")
        assertEquals(5, version())
    }

    fun testDetectsSix() {
        myFixture.addFileToProject("composer.json", """{ "require": { "statamic/cms": "~6.2" } }""")
        assertEquals(6, version())
    }

    fun testFallsBackToLock() {
        myFixture.addFileToProject("composer.json", """{ "require": { "laravel/framework": "^12.0" } }""")
        myFixture.addFileToProject(
            "composer.lock",
            """{ "packages": [ { "name": "statamic/cms", "version": "v5.3.1" } ] }""",
        )
        assertEquals(5, version())
    }
}
