# Antlers Modifier-Pipe Color Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the modifier pipe `|` its own themeable color, defaulting to plain text (white, like a comma) instead of the generic Operator green.

**Architecture:** A new `PIPE` `TextAttributesKey` (fallback `HighlighterColors.TEXT`); the base syntax highlighter and the string-interpolation annotator color `T_PIPE` with it instead of `OPERATOR`; the color-settings page exposes it.

**Tech Stack:** Kotlin, IntelliJ Platform (`TextAttributesKey`, `SyntaxHighlighterBase`, `ColorSettingsPage`), JUnit / `BasePlatformTestCase`.

**Spec:** `docs/superpowers/specs/2026-06-04-antlers-pipe-color-design.md`
**Branch:** `antlers-pipe-color` (already created from `main`; spec already committed).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty). `--rerun-tasks` REQUIRED.

## File Structure

- **Modify** `highlighting/AntlersSyntaxHighlighter.kt` — add the `PIPE` key; color `T_PIPE` with it.
- **Modify** `editor/AntlersStringInterpolationAnnotator.kt` — color interpolated `T_PIPE` with `PIPE`.
- **Modify** `highlighting/AntlersColorSettingsPage.kt` — register the key (descriptor + tag + demo).
- Tests: new `highlighting/AntlersSyntaxHighlighterTest.kt`; update `editor/AntlersStringInterpolationHighlightTest.kt` + `highlighting/AntlersColorSettingsPageTest.kt`.

---

### Task 1: New PIPE key + recolor the pipe (base highlighter + interpolation)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationAnnotator.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighterTest.kt` (create)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt` (modify)

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighterTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.highlighting

import com.github.balotias.intellijantlers.psi.AntlersTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntlersSyntaxHighlighterTest {
    private val hl = AntlersSyntaxHighlighter()

    @Test fun pipeUsesItsOwnPipeColorNotOperator() {
        val keys = hl.getTokenHighlights(AntlersTypes.T_PIPE).toList()
        assertTrue("pipe is PIPE-colored", keys.contains(AntlersSyntaxHighlighter.PIPE))
        assertFalse("pipe is no longer Operator", keys.contains(AntlersSyntaxHighlighter.OPERATOR))
    }

    @Test fun otherOperatorsStayOperator() {
        assertTrue(hl.getTokenHighlights(AntlersTypes.T_COLON).toList().contains(AntlersSyntaxHighlighter.OPERATOR))
        assertTrue(hl.getTokenHighlights(AntlersTypes.T_DOT).toList().contains(AntlersSyntaxHighlighter.OPERATOR))
    }
}
```

In `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt`,
replace the existing test:
```kotlin
    fun testInterpolatedPipeIsOperator() =
        assertEquals(AntlersSyntaxHighlighter.OPERATOR,
            keyOver("{{ \"{title | upper}\" }}", "|"))
```
with:
```kotlin
    fun testInterpolatedPipeUsesPipeColor() =
        assertEquals(AntlersSyntaxHighlighter.PIPE,
            keyOver("{{ \"{title | upper}\" }}", "|"))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersSyntaxHighlighterTest" --tests "*AntlersStringInterpolationHighlightTest"`
Expected: FAIL — `PIPE` is unresolved (compile error) / the pipe is still `OPERATOR`.

- [ ] **Step 3: Add the PIPE key + recolor in the base highlighter**

In `AntlersSyntaxHighlighter.kt`, add the import (next to the existing imports):
```kotlin
import com.intellij.openapi.editor.HighlighterColors
```
In the companion object, after the `val PARAMETER = …` line, add:
```kotlin
        val PIPE = TextAttributesKey.createTextAttributesKey("ANTLERS_PIPE", HighlighterColors.TEXT)
```
Next to the other `*_KEYS` arrays, add:
```kotlin
        private val PIPE_KEYS = arrayOf(PIPE)
```
In `getTokenHighlights`, change the operator branch from:
```kotlin
            AntlersTypes.T_OP, AntlersTypes.T_PIPE, AntlersTypes.T_EQUALS, AntlersTypes.T_ARROW,
            AntlersTypes.T_COLON, AntlersTypes.T_SLASH, AntlersTypes.T_DOT -> OPERATOR_KEYS
```
to (drop `T_PIPE`, add a dedicated branch):
```kotlin
            AntlersTypes.T_PIPE -> PIPE_KEYS

            AntlersTypes.T_OP, AntlersTypes.T_EQUALS, AntlersTypes.T_ARROW,
            AntlersTypes.T_COLON, AntlersTypes.T_SLASH, AntlersTypes.T_DOT -> OPERATOR_KEYS
```

- [ ] **Step 4: Recolor the pipe in the interpolation annotator**

In `editor/AntlersStringInterpolationAnnotator.kt`, the `colorFor` function maps `T_PIPE` with the other
operators:
```kotlin
        AntlersTypes.T_PIPE, AntlersTypes.T_COLON, AntlersTypes.T_DOT, AntlersTypes.T_OP,
        AntlersTypes.T_EQUALS, AntlersTypes.T_ARROW, AntlersTypes.T_SLASH,
        AntlersTypes.T_COMMA, AntlersTypes.T_SEMICOLON -> AntlersSyntaxHighlighter.OPERATOR
```
Split `T_PIPE` into its own branch above that one:
```kotlin
        AntlersTypes.T_PIPE -> AntlersSyntaxHighlighter.PIPE
        AntlersTypes.T_COLON, AntlersTypes.T_DOT, AntlersTypes.T_OP,
        AntlersTypes.T_EQUALS, AntlersTypes.T_ARROW, AntlersTypes.T_SLASH,
        AntlersTypes.T_COMMA, AntlersTypes.T_SEMICOLON -> AntlersSyntaxHighlighter.OPERATOR
```
(The `T_IDENT`-after-`T_PIPE` → `MODIFIER` rule earlier in `colorFor` is unchanged — `prev` still tracks
`T_PIPE`.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersSyntaxHighlighterTest" --tests "*AntlersStringInterpolationHighlightTest"`
Expected: PASS (the pipe is now `PIPE`; the `upper`-after-pipe → `MODIFIER` assertion in the interpolation
test still passes because the modifier rule reads `prev == T_PIPE`).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationAnnotator.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighterTest.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt
git commit -m "$(cat <<'EOF'
feat(highlighting): give the modifier pipe its own color (default plain text)

The `|` separator was colored as a generic Operator (green) while commas are
uncolored; it now uses a dedicated ANTLERS_PIPE key defaulting to plain text
(white), in both the base highlighter and string interpolation.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Expose the PIPE color in the settings page

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPageTest.kt`

- [ ] **Step 1: Update the failing test**

In `AntlersColorSettingsPageTest.kt`, add `AntlersSyntaxHighlighter.PIPE` to the `expected` descriptor set
in `testDescriptorsCoverAllKeys` (after the `OPERATOR` entry):
```kotlin
            AntlersSyntaxHighlighter.OPERATOR, AntlersSyntaxHighlighter.PIPE,
```
and in `testBasics`, add `"pipe"` to the expected tag-map key set:
```kotlin
        assertEquals("preview maps the semantic tags", setOf("tag", "kw", "mod", "param", "fmfence", "pipe"),
            page.additionalHighlightingTagToDescriptorMap!!.keys)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersColorSettingsPageTest"`
Expected: FAIL — the page doesn't expose `PIPE` / the `"pipe"` tag yet.

- [ ] **Step 3: Register the key in the settings page**

In `AntlersColorSettingsPage.kt`:
- Add to the map returned by `getAdditionalHighlightingTagToDescriptorMap()` (after the `"fmfence"` entry):
```kotlin
            "pipe" to AntlersSyntaxHighlighter.PIPE,
```
- Add to `DESCRIPTORS` (after the `"Operator"` entry):
```kotlin
            AttributesDescriptor("Modifier pipe", AntlersSyntaxHighlighter.PIPE),
```
- In the `DEMO` string, tag the pipe — change the line:
```kotlin
              {{ title | <mod>upper</mod> }}
```
to:
```kotlin
              {{ title <pipe>|</pipe> <mod>upper</mod> }}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersColorSettingsPageTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPageTest.kt
git commit -m "$(cat <<'EOF'
feat(highlighting): expose "Modifier pipe" in the Antlers color settings page

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Full-suite regression gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml && echo FAIL || echo "GATE CLEAN"`
Expected: `GATE CLEAN`. (Any other highlight test asserting the pipe was `OPERATOR` would surface here —
there are none besides the one updated in Task 1.)

## Self-Review

- **Spec coverage:** `PIPE` key + base-highlighter recolor (§Components 1) → Task 1 Step 3; interpolation
  recolor (§Components 2) → Task 1 Step 4; settings-page descriptor/tag/demo (§Components 3) → Task 2;
  tests (base highlighter, interpolation rename, settings page) → Tasks 1-2; full gate → Task 3. No gaps.
- **Placeholder scan:** none — complete code/edits in every step.
- **Type consistency:** `AntlersSyntaxHighlighter.PIPE` (Task 1 Step 3) is used identically in the
  annotator (Step 4), the settings page (Task 2), and all three tests; `PIPE_KEYS` is the array form used
  only in `getTokenHighlights`; the `OPERATOR` branch keeps exactly the remaining operator tokens.
