# Antlers Completion Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop completion from re-traversing the whole PSI tree ~4× per keystroke by caching the file's sorted `AntlersStatement` list.

**Architecture:** One memoized helper (`AntlersStatements.sortedIn(file)`, cached per-file on `PsiModificationTracker.MODIFICATION_COUNT`) that the three statement-replay functions consume instead of each running `PsiTreeUtil.findChildrenOfType(...).sortedBy { … }`. Behavior-invisible: the cached list is pre-sorted, so each consumer keeps only its cheap per-position filter.

**Tech Stack:** Kotlin, IntelliJ Platform (`CachedValuesManager`, `PsiModificationTracker`), JUnit / `BasePlatformTestCase`.

**Spec:** `docs/superpowers/specs/2026-06-04-antlers-completion-perf-design.md`
**Branch:** `antlers-completion-perf` (already created from `main`; spec already committed).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty). `--rerun-tasks` REQUIRED.

## File Structure

- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersStatements.kt` — the cache.
- **Modify** `scope/AntlersNestingTreeBuilder.kt` (`build` + `nearestUnclosedAt`, via a private `cachedStatements`) and `scope/AntlersScopeResolver.kt` (`scopesAt`) — consume the cache.
- Test: `scope/AntlersStatementsTest.kt`.

---

### Task 1: The cached statement helper

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersStatements.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersStatementsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersStatementsTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.scope

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersStatementsTest : BasePlatformTestCase() {

    fun testSortedAndCachedWithinOneModification() {
        val file = myFixture.configureByText("p.antlers.html", "{{ a }}{{ if x }}{{ b }}{{ /if }}")
        val first = AntlersStatements.sortedIn(file)
        assertTrue("has statements", first.isNotEmpty())
        assertEquals("sorted by start offset", first.map { it.textRange.startOffset },
            first.map { it.textRange.startOffset }.sorted())
        // Cached: same List instance until the PSI changes.
        assertSame("second call returns the cached instance", first, AntlersStatements.sortedIn(file))
    }

    fun testRefreshesAfterEdit() {
        myFixture.configureByText("p.antlers.html", "{{ a }}<caret>")
        val file = myFixture.file
        val before = AntlersStatements.sortedIn(file).size
        myFixture.type("{{ b }}")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("the new statement is picked up after the edit", before + 1, AntlersStatements.sortedIn(file).size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersStatementsTest"`
Expected: FAIL — compile error (`AntlersStatements` unresolved).

- [ ] **Step 3: Write the helper**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersStatements.kt`:
```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * The file's [AntlersStatement]s sorted by start offset, cached per-file on the PSI modification count.
 * The whole-tree traversal (`findChildrenOfType`) is the dominant per-completion cost; memoizing it lets
 * the ~4 statement-replay calls in one completion share a single traversal.
 */
object AntlersStatements {
    fun sortedIn(file: PsiFile): List<AntlersStatement> =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(
                PsiTreeUtil.findChildrenOfType(file, AntlersStatement::class.java)
                    .sortedBy { it.textRange.startOffset },
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersStatementsTest"`
Expected: PASS (2 tests). If `testSortedAndCachedWithinOneModification`'s `assertSame` fails, the
`getCachedValue` lambda is being re-created per call — keep it exactly as written (a single object method).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersStatements.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersStatementsTest.kt
git commit -m "$(cat <<'EOF'
perf: cache the file's sorted AntlersStatement list (per PSI mod count)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Route the three replay sites through the cache

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilder.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`

> This is a behavior-invisible refactor — its verification is that the EXISTING suite stays green (those
> tests exercise `build` / `nearestUnclosedAt` / `scopesAt`). No new behavioral test.

- [ ] **Step 1: Add the `PsiFile`-or-fallback helper + route `build`/`nearestUnclosedAt`**

In `AntlersNestingTreeBuilder.kt`, add `import com.intellij.psi.PsiFile` (keep the existing imports).

Add this private function to the `AntlersNestingTreeBuilder` object (e.g. just above `build`):
```kotlin
    /** Cached sorted statements when [root] is a file (the usual case); a direct traversal otherwise. */
    private fun cachedStatements(root: PsiElement): List<AntlersStatement> =
        (root as? PsiFile)?.let { AntlersStatements.sortedIn(it) }
            ?: PsiTreeUtil.findChildrenOfType(root, AntlersStatement::class.java).sortedBy { it.textRange.startOffset }
```

In `build`, replace:
```kotlin
        val statements = PsiTreeUtil.findChildrenOfType(root, AntlersStatement::class.java)
            .sortedBy { it.textRange.startOffset }
```
with:
```kotlin
        val statements = cachedStatements(root)
```

In `nearestUnclosedAt`, replace:
```kotlin
        val statements = PsiTreeUtil.findChildrenOfType(root, AntlersStatement::class.java)
            .filter { it.textRange.startOffset < beforeOffset }
            .sortedBy { it.textRange.startOffset }
```
with:
```kotlin
        val statements = cachedStatements(root).filter { it.textRange.startOffset < beforeOffset }
```

- [ ] **Step 2: Route `scopesAt`**

In `AntlersScopeResolver.kt`, `scopesAt` already has `val file = element.containingFile ?: return emptyList()`
(a `PsiFile`). Replace:
```kotlin
        val statements = PsiTreeUtil.findChildrenOfType(file, AntlersStatement::class.java)
            .filter { it.textRange.endOffset <= caret }
            .sortedBy { it.textRange.startOffset }
```
with:
```kotlin
        val statements = AntlersStatements.sortedIn(file).filter { it.textRange.endOffset <= caret }
```
(`AntlersStatements` is in the same `scope` package — no import needed. If `PsiTreeUtil` becomes unused
in this file after the change, remove its now-dead import; if it's still used elsewhere in the file, leave
it.)

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the suites that exercise these functions (behavior must be unchanged)**

Run: `./gradlew --rerun-tasks test --tests "*AntlersCompletionTest" --tests "*AntlersBalanceAnnotatorTest" --tests "*AntlersFoldingTest" --tests "*Scope*" --tests "*AntlersStatementsTest"`
Expected: PASS (all). These cover `nearestUnclosedAt` (completion closers/followers), `build` (balance +
folding), and `scopesAt` (scope/field tests). Any failure means the cached list isn't output-equivalent —
investigate; do NOT change those tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilder.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt
git commit -m "$(cat <<'EOF'
perf: route nesting + scope replays through the cached statement list

build / nearestUnclosedAt / scopesAt now share one cached traversal per file
instead of each re-walking the whole PSI tree. Behavior unchanged. #118/#173.

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
Expected: `GATE CLEAN`.

This is a behavior-invisible perf change: the entire suite must stay green with no test edits. If anything
regressed, the cache changed observable behavior — investigate before proceeding.

## Self-Review

- **Spec coverage:** `AntlersStatements.sortedIn` cache → Task 1; the three consumers
  (`build`/`nearestUnclosedAt` via `cachedStatements`, `scopesAt`) → Task 2; behavior-unchanged safety net
  (completion/scope/balance/folding + full gate) → Task 2 Step 4 + Task 3; cache unit test (sorted +
  same-instance + refresh-after-edit) → Task 1. The manual benchmark re-check is the orchestrator's
  post-merge step (timing isn't a CI assertion), per the spec. No gaps.
- **Placeholder scan:** none — full code for the helper + exact find/replace at each consumer.
- **Type consistency:** `AntlersStatements.sortedIn(file: PsiFile): List<AntlersStatement>` is defined in
  Task 1 and consumed identically in Task 2; `cachedStatements(root: PsiElement)` wraps it with the
  `PsiFile`-or-direct fallback (output-equivalent to the prior `findChildrenOfType(...).sortedBy{…}`); the
  per-site filters (`startOffset < beforeOffset`, `endOffset <= caret`) are unchanged and operate on the
  pre-sorted cached list.
