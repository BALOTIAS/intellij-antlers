# Antlers Code Style Settings Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Settings → Editor → Code Style → Antlers with the standard Indent panel, so users can configure the Antlers indent size/tabs the formatter already reads.

**Architecture:** Register a `LanguageCodeStyleSettingsProvider` for Antlers exposing the Indent settings + a code-sample preview. The formatter already calls `CodeStyle.getIndentOptions(file)`, so it honors the configured values automatically.

**Tech Stack:** Kotlin, IntelliJ `LanguageCodeStyleSettingsProvider`, `BasePlatformTestCase`. Build/test: `./gradlew --rerun-tasks test` (`--rerun-tasks` REQUIRED — Gradle caches). Gate: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

---

## File Structure

- `settings/AntlersLanguageCodeStyleSettingsProvider.kt` (create) — the Code Style provider.
- `META-INF/plugin.xml` (modify) — register `<langCodeStyleSettingsProvider>`.
- `settings/AntlersCodeStyleProviderTest.kt` (create, test) — provider basics + formatter integration.

---

### Task 1: The Code Style provider + registration

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/settings/AntlersLanguageCodeStyleSettingsProvider.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/settings/AntlersCodeStyleProviderTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `AntlersCodeStyleProviderTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.settings

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCodeStyleProviderTest : BasePlatformTestCase() {

    fun testProviderLanguageAndSample() {
        val p = AntlersLanguageCodeStyleSettingsProvider()
        assertEquals(AntlersLanguage.INSTANCE, p.language)
        val sample = p.getCodeSample(LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS)
        assertTrue("code sample is a non-blank Antlers snippet, got: $sample",
            sample.isNotBlank() && sample.contains("{{"))
    }

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    fun testConfiguredIndentSizeDrivesTheFormatter() {
        val temp = CodeStyle.getSettings(project).clone()
        temp.getCommonSettings(AntlersLanguage.INSTANCE).indentOptions!!.INDENT_SIZE = 2
        CodeStyle.doWithTemporarySettings(project, temp, Runnable {
            // {{ if }} body indents by the configured 2 spaces, not the default 4.
            assertEquals("{{ if x }}\n  {{ a }}\n{{ /if }}", reformat("{{ if x }}\n{{ a }}\n{{ /if }}"))
        })
    }

    fun testConfiguredTabsDriveTheFormatter() {
        val temp = CodeStyle.getSettings(project).clone()
        temp.getCommonSettings(AntlersLanguage.INSTANCE).indentOptions!!.let {
            it.USE_TAB_CHARACTER = true; it.TAB_SIZE = 4
        }
        CodeStyle.doWithTemporarySettings(project, temp, Runnable {
            assertEquals("{{ if x }}\n\t{{ a }}\n{{ /if }}", reformat("{{ if x }}\n{{ a }}\n{{ /if }}"))
        })
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersCodeStyleProviderTest"`
Expected: FAIL — `AntlersLanguageCodeStyleSettingsProvider` unresolved; and `getCommonSettings(AntlersLanguage.INSTANCE).indentOptions` is null without the registered provider, so the integration tests would NPE / use 4 spaces.

- [ ] **Step 3: Create the provider**

Create `AntlersLanguageCodeStyleSettingsProvider.kt`:
```kotlin
package com.github.balotias.intellijantlers.settings

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider

/**
 * Adds Settings → Editor → Code Style → Antlers with the standard Indent panel. The formatter already
 * reads `CodeStyle.getIndentOptions(file)`, so the configured indent size / tab settings drive it.
 * Indent settings only — the formatter consumes no spacing/wrapping/blank-line options.
 */
class AntlersLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {

    override fun getLanguage(): Language = AntlersLanguage.INSTANCE

    override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        if (settingsType == SettingsType.INDENT_SETTINGS) {
            consumer.showStandardOptions("INDENT_SIZE", "TAB_SIZE", "USE_TAB_CHARACTER")
        }
    }

    override fun getCodeSample(settingsType: SettingsType): String =
        "{{ collection:blog }}\n" +
        "    {{ if featured }}\n" +
        "        {{ title }}\n" +
        "    {{ /if }}\n" +
        "{{ /collection }}\n"
}
```
NOTE: `getLanguage()` and `getCodeSample()` are the required abstract overrides; `getIndentOptionsEditor()`
and `customizeSettings()` give the Indent panel. If this platform build declares any further abstract
method, implement it minimally (e.g. return defaults) and report it.

- [ ] **Step 4: Register the provider**

In `src/main/resources/META-INF/plugin.xml`, in the `<extensions defaultExtensionNs="com.intellij">`
block (near the other `lang.*` registrations), add:
```xml
        <langCodeStyleSettingsProvider implementation="com.github.balotias.intellijantlers.settings.AntlersLanguageCodeStyleSettingsProvider"/>
```

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersCodeStyleProviderTest"`
Expected: PASS (3 tests). With the provider registered, `getCommonSettings(AntlersLanguage.INSTANCE).indentOptions`
is non-null and the formatter honors the configured indent size / tabs.

If `indentOptions` is still null in the integration tests, fall back to the file-type accessor:
`temp.getIndentOptions(com.github.balotias.intellijantlers.AntlersFileType.INSTANCE).INDENT_SIZE = 2`
(keep the same reformat assertions). If `CodeStyle.doWithTemporarySettings(project, settings, Runnable)`
has a different arity in this build, use the `(PsiFile, Runnable)` or `(Project, CodeStyleSettings, ThrowableRunnable)`
overload — the assertion (2-space / tab indentation after reformat) is what matters.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/settings/AntlersLanguageCodeStyleSettingsProvider.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/settings/AntlersCodeStyleProviderTest.kt
git commit -m "feat(settings): Antlers Code Style page (indent options drive the formatter)"
```

---

### Task 2: Full-suite gate

**Files:** none (verification only).

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL. Existing formatter tests still pass (the default indent stays 4 spaces until a
user customizes it).

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml; echo "exit:$?"`
Expected: prints nothing, `exit:1`.

- [ ] **Step 3: Count check**

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END{print s}'`
Expected: previous total + 3, zero failures/errors.

If green, proceed to whole-feature review and `superpowers:finishing-a-development-branch`.

---

## Notes for the implementer

- This is configuration plumbing — no formatter logic changes (the passes already read `CodeStyle.getIndentOptions`).
- The reformat-on/off toggle stays in the existing `AntlersSettingsConfigurable` (Languages & Frameworks → Antlers); do NOT move it.
- Use `CodeStyle.doWithTemporarySettings` in the integration tests so the global project code-style settings aren't polluted across tests.
- Run the **full suite with `--rerun-tasks`** and gate on the result XML.
