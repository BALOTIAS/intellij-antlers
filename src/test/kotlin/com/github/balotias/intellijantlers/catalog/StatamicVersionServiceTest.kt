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

    fun testLockReadsCmsVersionNotASiblingPackage() {
        // A cms-prefixed sibling (with a misleading version) precedes the real package; we must read
        // statamic/cms's own version, not bleed into a neighbour.
        myFixture.addFileToProject("composer.json", """{ "require": { "laravel/framework": "^12.0" } }""")
        myFixture.addFileToProject(
            "composer.lock",
            """
            { "packages": [
                { "name": "statamic/cms-eloquent-driver", "version": "v9.9.9" },
                { "name": "statamic/cms", "version": "v6.0.4" },
                { "name": "spatie/something", "version": "v3.0.0" }
            ] }
            """.trimIndent(),
        )
        assertEquals(6, version())
    }
}
