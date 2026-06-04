# Antlers Tier-1 Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix `-=` mis-lexing (#138) and add a project setting that makes Reformat Code leave `.antlers.html` untouched so Prettier can own formatting (#132).

**Architecture:** Two independent fixes. (1) A surgical `IDENT` regex change in the JFlex lexer (regenerated). (2) A project-level `PersistentStateComponent` + a Settings page; all three formatter entry points (two post-format processors + the formatting-model builder) early-out to a no-op when the setting is off.

**Tech Stack:** Kotlin, IntelliJ Platform 2025.2 (JFlex lexer, `PersistentStateComponent`, `Configurable`, `AbstractBlock`/`FormattingModelProvider`), JUnit / `BasePlatformTestCase`.

**Spec:** `docs/superpowers/specs/2026-06-04-antlers-tier1-fixes-design.md`
**Branch:** `antlers-tier1-fixes` (already created from `main`; spec already committed).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty). `--rerun-tasks` REQUIRED.

**Lexer-regen recipe (verified byte-identical round-trip):**
```bash
JFLEX=/Users/balotias/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps.jflex/jflex/1.9.2/e866267b1b1e983b3c316c350e9fa9a9656606d7/jflex-1.9.2.jar
# java -cp "$JFLEX" jflex.Main -d <out-dir> --nobak src/main/grammar/AntlersLexer.flex
```

## File Structure

- **Modify** `src/main/grammar/AntlersLexer.flex` + regen `_AntlersLexer.java` — `IDENT` regex (Component 1).
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/settings/AntlersFormatterSettings.kt` — persisted setting.
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/settings/AntlersSettingsConfigurable.kt` — Settings page.
- **Modify** `src/main/resources/META-INF/plugin.xml` — register the configurable.
- **Modify** the two post-format processors + `AntlersHtmlFormattingModelBuilder.kt` — gate on the setting.
- Tests: `LexerTest` (add), `AntlersFormatterSettingsTest`, `AntlersSettingsConfigurableTest`, `AntlersFormatterOptOutTest`.

---

### Task 1: Fix `-=` mis-lexing (#138)

**Files:**
- Modify: `src/main/grammar/AntlersLexer.flex`
- Regen: `src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt` (add)

- [ ] **Step 1: Round-trip the lexer toolchain (gate)**

```bash
JFLEX=/Users/balotias/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps.jflex/jflex/1.9.2/e866267b1b1e983b3c316c350e9fa9a9656606d7/jflex-1.9.2.jar
rm -rf /tmp/jf0 && mkdir -p /tmp/jf0
java -cp "$JFLEX" jflex.Main -d /tmp/jf0 --nobak src/main/grammar/AntlersLexer.flex 2>&1 | tail -2
diff src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java /tmp/jf0/_AntlersLexer.java | grep -cE '^[<>]'
```
Expected: `0` (byte-identical). If non-zero → STOP and report BLOCKED.

- [ ] **Step 2: Write the failing tests**

Append to `src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt` (inside the class; reuse the existing `types(...)` helper):
```kotlin
    @Test fun compoundMinusAssignIsNotEatenByIdent() {
        // #138: `foo-=3` must lex as foo / -= / 3, not `foo-` / = / 3.
        assertEquals(
            listOf(
                AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS,
                AntlersTypes.T_IDENT, AntlersTypes.T_OP, AntlersTypes.T_NUMBER,
                AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE
            ),
            types("{{ foo-=3 }}")
        )
    }

    @Test fun trailingHyphenIsAnOperator() {
        assertEquals(
            listOf(AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS, AntlersTypes.T_IDENT, AntlersTypes.T_OP,
                AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE),
            types("{{ foo- }}")
        )
    }

    @Test fun kebabIdentifiersArePreserved() {
        // hyphens BETWEEN identifier chars stay part of the identifier
        for (name in listOf("a-b", "meta-title", "my-field-name", "count-1")) {
            val ts = types("{{ $name }}")
            assertEquals("$name should be one T_IDENT, got $ts",
                listOf(AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS, AntlersTypes.T_IDENT,
                    AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE),
                ts)
        }
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*LexerTest"`
Expected: FAIL — `compoundMinusAssignIsNotEatenByIdent` and `trailingHyphenIsAnOperator` fail (today `foo-`/`foo-=` are one `T_IDENT`). `kebabIdentifiersArePreserved` already passes.

- [ ] **Step 4: Edit the lexer**

In `src/main/grammar/AntlersLexer.flex`, change the line:
```
IDENT=[a-zA-Z_][a-zA-Z_0-9\-]*
```
to:
```
IDENT=[a-zA-Z_]([a-zA-Z_0-9]|-[a-zA-Z_0-9])*
```

- [ ] **Step 5: Regenerate the lexer**

```bash
JFLEX=/Users/balotias/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps.jflex/jflex/1.9.2/e866267b1b1e983b3c316c350e9fa9a9656606d7/jflex-1.9.2.jar
rm -rf /tmp/jf1 && mkdir -p /tmp/jf1
java -cp "$JFLEX" jflex.Main -d /tmp/jf1 --nobak src/main/grammar/AntlersLexer.flex 2>&1 | tail -3
cp /tmp/jf1/_AntlersLexer.java src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java
./gradlew compileKotlin
```
Expected: JFlex prints state/DFA counts with NO errors; BUILD SUCCESSFUL.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*LexerTest"`
Expected: PASS — all (the 3 new + every existing case). If any existing case regressed, the regex change broke a valid identifier shape; reconcile against the spec's table (kebab names must stay one `T_IDENT`).

- [ ] **Step 7: Commit**

```bash
git add src/main/grammar/AntlersLexer.flex \
        src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java \
        src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt
git commit -m "$(cat <<'EOF'
fix(lexer): don't let an identifier eat a trailing hyphen (`foo-=3`)

IDENT now only keeps a hyphen when it is followed by an identifier char, so
`foo-=3` lexes as foo / -= / 3 while kebab names (meta-title, count-1) stay
one token. Fixes Konafets antlers-idea #138.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Formatter setting + Settings page (#132 infra)

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/settings/AntlersFormatterSettings.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/settings/AntlersSettingsConfigurable.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/settings/AntlersFormatterSettingsTest.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/settings/AntlersSettingsConfigurableTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/settings/AntlersFormatterSettingsTest.kt`:
```kotlin
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
```

Create `src/test/kotlin/com/github/balotias/intellijantlers/settings/AntlersSettingsConfigurableTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersFormatterSettingsTest" --tests "*AntlersSettingsConfigurableTest"`
Expected: FAIL — compile errors (`AntlersFormatterSettings` / `AntlersSettingsConfigurable` unresolved).

- [ ] **Step 3: Write the settings service**

Create `src/main/kotlin/com/github/balotias/intellijantlers/settings/AntlersFormatterSettings.kt`:
```kotlin
package com.github.balotias.intellijantlers.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Per-project Antlers formatter preferences. Default = today's behavior (reformatting on). */
@Service(Service.Level.PROJECT)
@State(name = "AntlersFormatterSettings", storages = [Storage("antlers.xml")])
class AntlersFormatterSettings : PersistentStateComponent<AntlersFormatterSettings.State> {

    data class State(var reformatEnabled: Boolean = true)

    private var state = State()

    /** When false, Reformat Code leaves `.antlers.html` untouched (defer to Prettier / external). */
    var reformatEnabled: Boolean
        get() = state.reformatEnabled
        set(value) { state.reformatEnabled = value }

    override fun getState(): State = state
    override fun loadState(newState: State) { state = newState }

    companion object {
        fun getInstance(project: Project): AntlersFormatterSettings = project.service()
    }
}
```

- [ ] **Step 4: Write the Settings page**

Create `src/main/kotlin/com/github/balotias/intellijantlers/settings/AntlersSettingsConfigurable.kt`:
```kotlin
package com.github.balotias.intellijantlers.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/** Settings → Languages & Frameworks → Antlers. */
class AntlersSettingsConfigurable(private val project: Project) : Configurable {

    private var reformatCheckbox: JBCheckBox? = null

    override fun getDisplayName(): String = "Antlers"

    override fun createComponent(): JComponent {
        val cb = JBCheckBox(
            "Reformat Antlers code on Reformat Code (uncheck to defer to Prettier / an external formatter)"
        )
        reformatCheckbox = cb
        return FormBuilder.createFormBuilder()
            .addComponent(cb)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean =
        reformatCheckbox?.isSelected != AntlersFormatterSettings.getInstance(project).reformatEnabled

    override fun apply() {
        AntlersFormatterSettings.getInstance(project).reformatEnabled = reformatCheckbox?.isSelected ?: true
    }

    override fun reset() {
        reformatCheckbox?.isSelected = AntlersFormatterSettings.getInstance(project).reformatEnabled
    }
}
```

- [ ] **Step 5: Register the configurable**

In `src/main/resources/META-INF/plugin.xml` (inside `<extensions defaultExtensionNs="com.intellij">`):
```xml
        <projectConfigurable parentId="language"
            id="com.github.balotias.intellijantlers.settings.AntlersSettingsConfigurable"
            displayName="Antlers"
            instance="com.github.balotias.intellijantlers.settings.AntlersSettingsConfigurable"/>
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersFormatterSettingsTest" --tests "*AntlersSettingsConfigurableTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/settings \
        src/test/kotlin/com/github/balotias/intellijantlers/settings \
        src/main/resources/META-INF/plugin.xml
git commit -m "$(cat <<'EOF'
feat(settings): per-project AntlersFormatterSettings + Settings page

Adds a "Reformat Antlers code" toggle (default on) under Languages &
Frameworks → Antlers. Gating wired in the next task.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Gate the formatter on the setting (#132)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingPostFormatProcessor.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersHtmlFormattingModelBuilder.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersFormatterOptOutTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersFormatterOptOutTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.settings.AntlersFormatterSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFormatterOptOutTest : BasePlatformTestCase() {
    override fun tearDown() {
        try { AntlersFormatterSettings.getInstance(project).reformatEnabled = true } finally { super.tearDown() }
    }

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    fun testDisabledLeavesDelimiterSpacingUntouched() {
        AntlersFormatterSettings.getInstance(project).reformatEnabled = false
        assertEquals("{{x}}", reformat("{{x}}"))   // would become "{{ x }}" when enabled
    }

    fun testDisabledLeavesMultilineTagUntouched() {
        AntlersFormatterSettings.getInstance(project).reformatEnabled = false
        val src = "{{ collection:blog\nlimit=\"3\"\n}}"   // would be indented + spacing-normalized when enabled
        assertEquals(src, reformat(src))
    }

    fun testEnabledStillFormats() {
        // default (enabled) — proves the gate doesn't break normal behavior
        assertEquals("{{ x }}", reformat("{{x}}"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersFormatterOptOutTest"`
Expected: FAIL — `testDisabledLeavesDelimiterSpacingUntouched` / `testDisabledLeavesMultilineTagUntouched` fail (formatting still runs; not gated yet). `testEnabledStillFormats` passes.

- [ ] **Step 3: Gate the two post-format processors**

In `AntlersSpacingPostFormatProcessor.kt`, inside `processText`, immediately after the existing first line
`val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat`,
add:
```kotlin
        if (!com.github.balotias.intellijantlers.settings.AntlersFormatterSettings.getInstance(source.project).reformatEnabled)
            return rangeToReformat
```

In `AntlersMultilineTagIndentProcessor.kt`, inside `processText`, immediately after its first line
`val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat`,
add the same guard:
```kotlin
        if (!com.github.balotias.intellijantlers.settings.AntlersFormatterSettings.getInstance(source.project).reformatEnabled)
            return rangeToReformat
```

- [ ] **Step 4: No-op the formatting-model builder when disabled**

In `AntlersHtmlFormattingModelBuilder.kt`, add these imports:
```kotlin
import com.github.balotias.intellijantlers.settings.AntlersFormatterSettings
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelProvider
import com.intellij.psi.formatter.common.AbstractBlock
```
Add an override of `createModel` to the `AntlersHtmlFormattingModelBuilder` class (as a member, e.g. right
after `isMarkupLanguageElement`):
```kotlin
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val file = formattingContext.containingFile
        if (!AntlersFormatterSettings.getInstance(file.project).reformatEnabled) {
            return FormattingModelProvider.createFormattingModelForPsiFile(
                file, AntlersNoopBlock(file.node), formattingContext.codeStyleSettings
            )
        }
        return super.createModel(formattingContext)
    }
```
Add the no-op block as a private nested class inside `AntlersHtmlFormattingModelBuilder` (next to
`AntlersTemplateBlock`):
```kotlin
    /** Whole-file leaf block → Reformat Code makes no changes (used when Antlers reformatting is off). */
    private class AntlersNoopBlock(node: ASTNode) : AbstractBlock(node, null, null) {
        override fun buildChildren(): List<Block> = emptyList()
        override fun getSpacing(child1: Block?, child2: Block): Spacing? = null
        override fun isLeaf(): Boolean = true
        override fun getIndent(): Indent = Indent.getNoneIndent()
    }
```
(`Block`, `Spacing`, `Indent`, `ASTNode` are already imported in this file.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersFormatterOptOutTest"`
Expected: PASS (3 tests). If `testDisabledLeavesMultilineTagUntouched` still changes the text, one of the
three entry points isn't gated — check all three guards are present and read the same setting.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersFormatterOptOutTest.kt
git commit -m "$(cat <<'EOF'
feat(formatter): honor the reformat-disabled setting (defer to Prettier)

When AntlersFormatterSettings.reformatEnabled is off, the two post-format
processors early-out and the model builder returns a no-op model, so Reformat
Code leaves .antlers.html untouched. Addresses Konafets antlers-idea #132.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Full-suite regression gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml && echo FAIL || echo "GATE CLEAN"`
Expected: `GATE CLEAN`.

If a formatter test regressed (`AntlersSpacingFormatterTest` / `AntlersMultilineFormatTest` /
`AntlersHtmlFormatTest`): the default must remain `reformatEnabled = true`, so the gate must be invisible
when the setting is untouched. If a lexer/parsing test regressed: the `IDENT` change released a hyphen
that a valid identifier needed — reconcile against the spec table. Do not edit other tests to go green.

## Self-Review

- **Spec coverage:** Component 1 `IDENT` regex + consequences → Task 1; Component 2 settings service →
  Task 2 Step 3, Configurable + registration → Task 2 Steps 4-5, gating of all THREE entry points (scope
  (b)) → Task 3 Steps 3-4; all testing rows (lexer `-=`/kebab, settings round-trip, configurable,
  disabled-leaves-untouched, enabled-still-formats, full gate) → Tasks 1-4. The `tearDown` reset
  requirement is in every Component-2 test. No gaps.
- **Placeholder scan:** none — full code in every step; exact regen commands.
- **Type consistency:** `AntlersFormatterSettings.getInstance(project).reformatEnabled` is defined in Task
  2 Step 3 and used identically in the Configurable (Task 2 Step 4) and all three formatter guards (Task 3);
  `createModel(FormattingContext): FormattingModel` matches the `AbstractXmlTemplateFormattingModelBuilder`
  contract the builder already extends; `AntlersNoopBlock(ASTNode)` extends `AbstractBlock` with the four
  overrides the platform requires. The new `IDENT` macro is referenced only by the existing `{IDENT}` rule.
