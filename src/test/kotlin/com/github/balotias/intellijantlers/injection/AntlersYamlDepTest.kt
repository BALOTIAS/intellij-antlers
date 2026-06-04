package com.github.balotias.intellijantlers.injection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.YAMLLanguage

class AntlersYamlDepTest : BasePlatformTestCase() {
    fun testYamlLanguageAvailable() {
        assertNotNull("the bundled YAML plugin is on the test classpath", YAMLLanguage.INSTANCE)
    }
}
