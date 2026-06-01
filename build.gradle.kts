import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

// NOTE: do NOT declare a project-level `repositories { }` block. Repositories are configured in
// settings.gradle.kts via dependencyResolutionManagement (mavenCentral + intellijPlatform
// defaultRepositories). A project-level block runs in PREFER_PROJECT mode and would make Gradle
// ignore the settings repositories — including IPGP's IDE-installer repo (download.jetbrains.com),
// which breaks installer/test-runtime resolution.

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}

sourceSets["main"].java.srcDirs("src/main/gen")

// Run each test in its own JVM. ParsingTestCase (lightweight, registers only the parser) and
// BasePlatformTestCase (loads the full plugin.xml incl. the multi-root file view provider) otherwise
// pollute each other's application-level registrations, making the view provider engage flakily.
tasks.withType<Test>().configureEach {
    forkEvery = 1
}

// The generated lexer/parser/PSI is committed under src/main/gen and compiled directly. The
// grammarkit plugin is intentionally NOT applied: it forced an early resolution of the
// `intellijPlatformDependency` configuration, which made IPGP skip registering its IDE-resolution
// metadata rule ("...has some resolution errors. ...will not be registered"), permanently breaking
// the `:test` task's platform resolution. It's also incompatible with the 2025.3+ platform
// (fastutil NoClassDefFoundError). To regenerate after editing the grammar, temporarily re-apply
// `org.jetbrains.grammarkit`, run generateLexer/generateParser on a compatible setup, then remove it
// again and commit the updated src/main/gen output.
// (on a setup where grammarkit works), then commit the updated src/main/gen output.

