import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("org.jetbrains.grammarkit")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdeaCommunity("2025.2.3") {
            useInstaller = false
        }
        testFramework(TestFrameworkType.Platform)
    }
}

sourceSets["main"].java.srcDirs("src/main/gen")

tasks.named<org.jetbrains.grammarkit.tasks.GenerateLexerTask>("generateLexer") {
    sourceFile.set(file("src/main/grammar/AntlersLexer.flex"))
    targetOutputDir.set(file("src/main/gen/com/github/balotias/intellijantlers/lexer"))
    purgeOldFiles.set(true)
}

tasks.named<org.jetbrains.grammarkit.tasks.GenerateParserTask>("generateParser") {
    sourceFile.set(file("src/main/grammar/Antlers.bnf"))
    targetRootOutputDir.set(file("src/main/gen"))
    pathToParser.set("/com/github/balotias/intellijantlers/parser/AntlersParser.java")
    pathToPsiRoot.set("/com/github/balotias/intellijantlers/psi")
    purgeOldFiles.set(true)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn("generateLexer", "generateParser")
}

