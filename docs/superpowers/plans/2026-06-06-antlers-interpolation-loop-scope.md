# Loop-Scope-Aware Completion Inside Interpolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Field completion/go-to-def/hover inside string interpolation resolves against the enclosing `{{ collection }}` loop scope, not just global.

**Architecture:** Before computing blueprint scope, normalize an element to its injection host (the `AntlersStringLeaf` in the outer file) via `InjectedLanguageManager.getInjectionHost`. The existing scope machinery then computes the loop scope from the outer file. One helper + two small edits in `scope/`.

**Tech Stack:** Kotlin, IntelliJ `InjectedLanguageManager`, `BasePlatformTestCase`. Build/test: `./gradlew --rerun-tasks test` (`--rerun-tasks` REQUIRED — Gradle caches). Gate: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

---

## File Structure

- `scope/AntlersScopeResolver.kt` (modify) — add `hostOrSelf`; use it in `scopesAt`.
- `scope/AntlersFieldContext.kt` (modify) — normalize to host in `namespacesFor`.
- `injection/AntlersInterpolationScopeTest.kt` (create, test) — resolver-level loop-scope tests.

---

### Task 1: Normalize to the injection host before scope resolution

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersFieldContext.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersInterpolationScopeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `AntlersInterpolationScopeTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersInterpolationScopeTest : BasePlatformTestCase() {

    private fun blueprint(handle: String, field: String) {
        myFixture.addFileToProject(
            "resources/blueprints/collections/$handle/$handle.yaml",
            "fields:\n  - handle: $field\n    field:\n      type: text\n      display: X\n"
        )
    }

    /** An element INSIDE the injected interpolation fragment whose host string contains [marker]. */
    private fun injectedElement(text: String, marker: String): PsiElement {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val host = PsiTreeUtil.collectElements(antlers) {
            it is AntlersStringLeaf && it.text.contains(marker)
        }.first()
        val files = mutableListOf<PsiFile>()
        InjectedLanguageManager.getInstance(project).enumerate(host) { f, _ -> files.add(f) }
        val root = files.first().viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.collectElements(root) { it.text == marker }.first()
    }

    fun testLoopScopeCrossesIntoInterpolation() {
        blueprint("blog", "hero_title")
        val el = injectedElement(
            "{{ collection:blog }}\n<a href=\"{zz}\">x</a>\n{{ /collection }}", "zz")
        val handles = AntlersFieldContext.fieldsInScope(el, project)?.map { it.handle }
        assertEquals(listOf("hero_title"), handles)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInterpolationScopeTest"`
Expected: FAIL — without host normalization, `fieldsInScope` on the injected element sees the fragment file (no loop) and returns the global fallback (`null`), not `["hero_title"]`.

- [ ] **Step 3: Add `hostOrSelf` and use it in `scopesAt`**

In `scope/AntlersScopeResolver.kt`, add the import:
```kotlin
import com.intellij.lang.injection.InjectedLanguageManager
```
Add this public function to the `AntlersScopeResolver` object (next to `scopesAt`):
```kotlin
    /** The injection host in the outer file for an injected element, else the element itself. */
    fun hostOrSelf(element: PsiElement): PsiElement =
        InjectedLanguageManager.getInstance(element.project).getInjectionHost(element) ?: element
```
Change the top of `scopesAt` from:
```kotlin
    fun scopesAt(element: PsiElement): List<BlueprintScope> {
        val file = element.containingFile ?: return emptyList()
        val caret = element.textRange.startOffset
```
to:
```kotlin
    fun scopesAt(element: PsiElement): List<BlueprintScope> {
        val target = hostOrSelf(element)
        val file = target.containingFile ?: return emptyList()
        val caret = target.textRange.startOffset
```
and change the `computeIfAbsent` line at the end of `scopesAt` from:
```kotlin
        return memo.computeIfAbsent(caret) { computeScopesAt(file, caret, element.project) }
```
to:
```kotlin
        return memo.computeIfAbsent(caret) { computeScopesAt(file, caret, target.project) }
```

- [ ] **Step 4: Normalize to host in `namespacesFor`**

In `scope/AntlersFieldContext.kt`, change `namespacesFor` from:
```kotlin
    fun namespacesFor(element: PsiElement): List<BlueprintNamespace>? {
        val scopes = AntlersScopeResolver.scopesAt(element)
        if (scopes.isNotEmpty()) return scopes.map { it.namespace }
        val hints = element.containingFile?.let { AntlersViewHints.declaredNamespaces(it) }
        if (!hints.isNullOrEmpty()) return hints
        val page = PageBlueprintResolver.namespacesFor(element)
        if (page.isNotEmpty()) return page
        return null
    }
```
to:
```kotlin
    fun namespacesFor(element: PsiElement): List<BlueprintNamespace>? {
        // Inside string interpolation the element lives in an injected fragment with no enclosing loop;
        // resolve scope against the injection host in the outer file so loop/hint/page all see the real
        // surrounding context. For a non-injected element this is a no-op (hostOrSelf returns it).
        val target = AntlersScopeResolver.hostOrSelf(element)
        val scopes = AntlersScopeResolver.scopesAt(target)
        if (scopes.isNotEmpty()) return scopes.map { it.namespace }
        val hints = target.containingFile?.let { AntlersViewHints.declaredNamespaces(it) }
        if (!hints.isNullOrEmpty()) return hints
        val page = PageBlueprintResolver.namespacesFor(target)
        if (page.isNotEmpty()) return page
        return null
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInterpolationScopeTest"`
Expected: PASS (1 test) — `fieldsInScope` on the injected element now returns `["hero_title"]`.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersFieldContext.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersInterpolationScopeTest.kt
git commit -m "feat(scope): resolve interpolation scope against the injection host (loop scope)"
```

---

### Task 2: Negative + no-loop + regression

**Files:**
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersInterpolationScopeTest.kt`

- [ ] **Step 1: Add the tests**

Append to `AntlersInterpolationScopeTest.kt`:
```kotlin
    fun testOtherCollectionFieldNotInScope() {
        blueprint("blog", "hero_title")
        blueprint("news", "headline")
        val el = injectedElement(
            "{{ collection:blog }}\n<a href=\"{zz}\">x</a>\n{{ /collection }}", "zz")
        val handles = AntlersFieldContext.fieldsInScope(el, project)?.map { it.handle }
        assertEquals(listOf("hero_title"), handles)
        assertFalse("news field must not leak into the blog loop", handles!!.contains("headline"))
    }

    fun testInterpolationOutsideLoopFallsBackToGlobal() {
        blueprint("blog", "hero_title")
        // No enclosing loop → no namespaces → global fallback (null), exactly as before this feature.
        val el = injectedElement("<a href=\"{zz}\">x</a>", "zz")
        assertNull("no enclosing loop → global fallback", AntlersFieldContext.fieldsInScope(el, project))
    }
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInterpolationScopeTest"`
Expected: PASS (3 tests total).

- [ ] **Step 3: Confirm no regression in scope/completion/docs**

Run: `./gradlew --rerun-tasks test --tests "*AntlersScopeResolverTest" --tests "*AntlersFieldContext*" --tests "*AntlersViewHintsTest" --tests "*ScopeTest" --tests "*CompletionTest" --tests "*PageNavTest"`
Expected: all PASS. `hostOrSelf` is a no-op for non-injected elements, so plain `{{ }}` scoping is unchanged.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersInterpolationScopeTest.kt
git commit -m "test(scope): interpolation loop-scope negatives + no-loop fallback"
```

---

### Task 3: Full-suite gate

**Files:** none (verification only).

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml; echo "exit:$?"`
Expected: prints nothing, `exit:1`.

- [ ] **Step 3: Count check**

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END{print s}'`
Expected: previous total + 3, zero failures/errors.

If green, proceed to whole-feature review and `superpowers:finishing-a-development-branch`.

---

## Notes for the implementer

- `hostOrSelf` is normalized in BOTH `scopesAt` (for its direct callers) and `namespacesFor` (for the hint/page paths). Double-normalization is harmless (the host is not itself injected, so the second call is a no-op).
- The tests resolve scope at the `fieldsInScope` level, NOT via `myFixture.completeBasic()`, because completion into a freshly-injected fragment is timing-flaky in the harness (documented in the prior interpolation feature). The injected element is reached via `InjectedLanguageManager.enumerate` → the fragment's Antlers root.
- Run the **full suite with `--rerun-tasks`** and gate on the result XML.
