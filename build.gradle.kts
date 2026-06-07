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
        bundledPlugin("org.jetbrains.plugins.yaml")
        testFramework(TestFrameworkType.Platform)
    }
}

sourceSets["main"].java.srcDirs("src/main/gen")

// The whole suite runs in one JVM (`forkEvery = 0`), ~28s, instead of the old `forkEvery = 1` (a fresh
// ~5s IntelliJ-platform JVM per class, ~120 classes ≈ 10min on CI). The one class that used to require
// per-class isolation, `AntlersParsingTest` (ParsingTestCase), is made registration-independent via
// `checkAllPsiRoots() = false` (see that class) so it no longer conflicts with the plugin-loading tests.
//
// NB: do NOT add `maxParallelForks`. All forks share the single IntelliJ test sandbox (idea.system/config/
// plugins/log paths set by the IntelliJ Platform Gradle Plugin), so concurrent forks contend on IntelliJ's
// single-instance lock and race on sandbox files — observed as `:test` hanging and throwing IOExceptions.
tasks.withType<Test>().configureEach {
    forkEvery = 0
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

