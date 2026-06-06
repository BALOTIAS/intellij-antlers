# Structural Indentation — Antlers-Side (conditionals + multi-line expressions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reformat Code dedents `{{ else }}`/`{{ elseif }}` to the `{{ if }}` opener level, and indents multi-line `{{ … }}` expression lines by bracket depth (`[ ( {`).

**Architecture:** Two surgical extensions to the existing post-format processors: `AntlersBlockIndentProcessor` (dedent `else`/`elseif` lines) and `AntlersMultilineTagIndentProcessor` (bracket-depth-aware interior indent). No HTML changes; indentation-only; reformat-off opt-out preserved.

**Tech Stack:** Kotlin, IntelliJ `PostFormatProcessor`, `BasePlatformTestCase`. Build/test: `./gradlew --rerun-tasks test` (`--rerun-tasks` REQUIRED — Gradle caches). Gate: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

---

## File Structure

- `formatter/AntlersBlockIndentProcessor.kt` (modify) — dedent `else`/`elseif`.
- `formatter/AntlersMultilineTagIndentProcessor.kt` (modify) — bracket-depth interior indent.
- `formatter/AntlersBlockIndentTest.kt` (modify, test) — else/elseif cases.
- `formatter/AntlersMultilineFormatTest.kt` (modify, test) — bracket-nesting + composition cases.

---

### Task 1: Dedent `{{ else }}` / `{{ elseif }}` to the opener level

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentProcessor.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `AntlersBlockIndentTest.kt`:
```kotlin
    fun testElseDedentsToOpener() {
        val out = reformat("{{ if x }}\n{{ a }}\n{{ else }}\n{{ b }}\n{{ /if }}")
        val u = unit()
        assertEquals("{{ if x }}\n${u}{{ a }}\n{{ else }}\n${u}{{ b }}\n{{ /if }}", out)
    }

    fun testElseifDedentsToOpener() {
        val out = reformat("{{ if x }}\n{{ a }}\n{{ elseif y }}\n{{ b }}\n{{ /if }}")
        val u = unit()
        assertEquals("{{ if x }}\n${u}{{ a }}\n{{ elseif y }}\n${u}{{ b }}\n{{ /if }}", out)
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersBlockIndentTest"`
Expected: FAIL — currently `{{ else }}`/`{{ elseif }}` land at body level (`${u}…`), not column 0.

- [ ] **Step 3: Implement the dedent**

In `AntlersBlockIndentProcessor.kt`, add the import (next to the other `psi` imports):
```kotlin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
```
Immediately BEFORE the `fun walk(node: NestingNode, d: Int, base: String) {` declaration, add the
`elseLines` computation:
```kotlin
        // Lines whose statement is an `else`/`elseif` branch marker — they render at their {{ if }}'s
        // depth (one less than the body), so each branch's content stays indented +1 under them.
        val elseLines: Set<Int> = PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)
            .filter {
                val kw = (it.condition as? AntlersConditionMixin)?.keyword
                kw == "else" || kw == "elseif"
            }
            .map { document.getLineNumber(it.textRange.startOffset) }
            .toSet()
```
Then change the body-loop line inside `walk` from:
```kotlin
            for (l in (oLine + 1)..bodyEnd) if (l in 0 until lineCount) {
                depth[l] = d + 1; touched[l] = true; baseOf[l] = base
            }
```
to:
```kotlin
            for (l in (oLine + 1)..bodyEnd) if (l in 0 until lineCount) {
                depth[l] = if (l in elseLines) d else d + 1; touched[l] = true; baseOf[l] = base
            }
```

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersBlockIndentTest"`
Expected: PASS. (The whole class passes — existing block-indent tests are unaffected, since only `else`/`elseif` lines change depth.)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentProcessor.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt
git commit -m "feat(formatter): dedent {{ else }}/{{ elseif }} to the {{ if }} opener level"
```

---

### Task 2: Bracket-depth indentation for multi-line `{{ … }}` expressions

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineFormatTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `AntlersMultilineFormatTest.kt`:
```kotlin
    fun testMultilineBracketNesting() {
        val out = reformat("{{\n[\n'a' => 1,\n'b' => 2,\n] | classes\n}}")
        val u = unit()
        assertEquals(
            "{{\n${u}[\n${u}${u}'a' => 1,\n${u}${u}'b' => 2,\n${u}] | classes\n}}",
            out
        )
    }

    fun testBracketCharInsideStringDoesNotShiftDepth() {
        // The `[` inside the string must NOT count toward bracket depth.
        val out = reformat("{{\nx = \"a[b\",\ny = 2\n}}")
        val u = unit()
        assertEquals("{{\n${u}x = \"a[b\",\n${u}y = 2\n}}", out)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersMultilineFormatTest"`
Expected: FAIL — `testMultilineBracketNesting` fails (current processor puts every interior line at one flat level, so the array elements are at `${u}` not `${u}${u}`).

- [ ] **Step 3: Implement bracket-depth indentation**

In `AntlersMultilineTagIndentProcessor.kt`, add imports (next to the existing ones):
```kotlin
import com.intellij.psi.tree.IElementType
```
Add a `companion object` to the class with the bracket deltas (place it just inside the class, before
`processElement`):
```kotlin
    companion object {
        private val BRACKET_DELTAS: Map<IElementType, Int> = mapOf(
            AntlersTypes.T_LBRACE to 1, AntlersTypes.T_LBRACKET to 1, AntlersTypes.T_LPAREN to 1,
            AntlersTypes.T_RBRACE to -1, AntlersTypes.T_RBRACKET to -1, AntlersTypes.T_RPAREN to -1,
        )
    }
```
In `processText`, the per-statement loop currently computes `val contIndent = openerIndent + unit`.
REMOVE that `contIndent` line, and after the `stringSpans` are collected add the bracket-token list:
```kotlin
            // Bracket tokens within the statement ( [ { = +1, ) ] } = -1 ). Antlers strings are single
            // T_STRING tokens, so brackets *inside* strings are naturally excluded from the count.
            val brackets = PsiTreeUtil.collectElements(stmt) { el ->
                val t = el.node?.elementType
                t != null && BRACKET_DELTAS.containsKey(t)
            }.map { it.textRange.startOffset to BRACKET_DELTAS.getValue(it.node!!.elementType) }
```
Then change the `target` computation's `else` branch from:
```kotlin
                val target = when {
                    firstNonWs < 0 -> ""                                  // blank line → strip
                    line == closerLine && contentOffset == rdoubleStart -> openerIndent  // `}}` on its own line
                    else -> contIndent                                    // param / content line
                }
```
to:
```kotlin
                val target = when {
                    firstNonWs < 0 -> ""                                  // blank line → strip
                    line == closerLine && contentOffset == rdoubleStart -> openerIndent  // `}}` on its own line
                    else -> {                                             // interior: one level + bracket depth
                        val openBefore = brackets.filter { it.first < contentOffset }.sumOf { it.second }
                        val closesFirst = if (lineText[firstNonWs] in "])}") 1 else 0
                        openerIndent + unit.repeat(maxOf(1, 1 + openBefore - closesFirst))
                    }
                }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersMultilineFormatTest"`
Expected: PASS. The existing multiline tests (single-level param lists with no brackets) still pass — for a param line with no brackets, `openBefore == 0`, `closesFirst == 0`, so `unit.repeat(1)` == the old `contIndent`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineFormatTest.kt
git commit -m "feat(formatter): indent multi-line {{ }} expression lines by bracket depth"
```

---

### Task 3: Composition, idempotency, regression, gate

**Files:**
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineFormatTest.kt`

- [ ] **Step 1: Add composition + idempotency tests**

Append to `AntlersMultilineFormatTest.kt`:
```kotlin
    fun testBracketNestingInsideIfBlockComposes() {
        // block-indent sets the multi-line {{ opener at +1 (inside the if); the multiline pass then adds
        // bracket depth on top of that opener indent.
        val out = reformat("{{ if x }}\n{{\n[\n'a' => 1,\n] | classes\n}}\n{{ /if }}")
        val u = unit()
        assertEquals(
            "{{ if x }}\n${u}{{\n${u}${u}[\n${u}${u}${u}'a' => 1,\n${u}${u}] | classes\n${u}}}\n{{ /if }}",
            out
        )
    }

    fun testBracketNestingIdempotent() {
        val once = reformat("{{\n[\n'a' => 1,\n'b' => 2,\n] | classes\n}}")
        assertEquals("bracket-nesting reformat is a fixed point", once, reformat(once))
    }
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew --rerun-tasks test --tests "*AntlersMultilineFormatTest" --tests "*AntlersBlockIndentTest"`
Expected: PASS. If `testBracketNestingInsideIfBlockComposes` fails on offsets, verify processor order is spacing → block-indent → multiline (plugin.xml) so the multi-line `{{` opener is block-indented before the multiline pass reads its indent.

- [ ] **Step 3: Run the full formatter suite — reconcile any pinned expectations**

Run: `./gradlew --rerun-tasks test --tests "*AntlersHtmlFormatTest" --tests "*AntlersMultilineFormatTest" --tests "*AntlersBlockIndentTest" --tests "*AntlersSpacingFormatterTest" --tests "*AntlersFormatterOptOutTest"`
Expected: all PASS. The `else`/`elseif` and bracket changes only affect `else`/`elseif` lines and multi-line-`{{ }}` interiors with brackets; other fixtures are unaffected. If a pre-existing fixture contained an `{{ else }}` at body level or a bracketed multi-line `{{ }}` and now differs, that is the intended new (correct) output — update that expectation, recording the before→after in the commit message.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineFormatTest.kt
git commit -m "test(formatter): else/bracket composition + idempotency"
```

---

### Task 4: Full-suite gate

**Files:** none (verification only).

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml; echo "exit:$?"`
Expected: prints nothing, `exit:1`.

- [ ] **Step 3: Count check**

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END{print s}'`
Expected: previous total + ~6 new, zero failures/errors.

If green, proceed to whole-feature review and `superpowers:finishing-a-development-branch`.

---

## Notes for the implementer

- These are surgical extensions; do NOT touch the HTML handling (that is the deferred follow-up sub-project).
- `else`/`elseif` detection uses `(stmt.condition as? AntlersConditionMixin)?.keyword` (same access the nesting builder uses).
- Bracket depth is token-based (`BRACKET_DELTAS` keyed by `AntlersTypes.T_L*/T_R*`), so brackets inside strings (single `T_STRING` tokens) and the `{{`/`}}` delimiters (`T_LDOUBLE`/`T_RDOUBLE`) are correctly excluded.
- Both passes set indentation absolutely → idempotent; the `testBracketNestingIdempotent` test pins it.
- Run the **full suite with `--rerun-tasks`** and gate on the result XML.
