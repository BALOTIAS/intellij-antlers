# Go-to-Declaration for Inline-Tag-Call Custom Tags Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Cmd/Ctrl+B (go-to-declaration) on a custom tag written as an inline tag call — `{{ href = {obfuscate_link …} }}` — navigate to its PHP `Tags/` class, just like the standalone `{{ obfuscate_link … }}` form already does.

**Architecture:** In `{{ x = {tag …} }}` the parser leaves the tag name as a bare `T_IDENT` (not an `AntlersNamePathMixin` head), so `AntlersDefinitionReferenceHelper.refsForIdent` attaches no reference. Extract the inline-tag-head heuristic the semantic highlighter already uses for *coloring* into a shared `AntlersInlineTags.isInlineTagHead`, then attach the existing soft `AntlersPhpClassReference` in `refsForIdent` when the ident is an inline tag head. Tags only — modifiers already get the reference everywhere they appear.

**Tech Stack:** Kotlin, IntelliJ PSI references (`PsiReferenceBase`), `BasePlatformTestCase`. Build/test: `./gradlew --rerun-tasks test` (`--rerun-tasks` REQUIRED — Gradle caches). Gate: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

---

## File Structure

- `psi/AntlersInlineTags.kt` (create) — shared `isInlineTagHead` helper (the single source of truth for "is this bare ident an inline tag-call head").
- `editor/AntlersSemanticHighlightAnnotator.kt` (modify) — drop its private `isInlineTagHead`, call the shared one (behavior-preserving).
- `references/AntlersDefinitionReferenceHelper.kt` (modify) — attach `AntlersPhpClassReference` for inline tag heads.
- `psi/AntlersInlineTagsTest.kt` (create, test) — unit tests for the shared helper.
- `references/AntlersInlineTagGotoDeclTest.kt` (create, test) — reference resolution end-to-end (text-scan scanner works without the PHP plugin).

---

### Task 1: Extract the shared `AntlersInlineTags.isInlineTagHead` helper

Pure refactor — move the detection out of the annotator into a shared object and have the annotator use it. The existing coloring test stays green (no behavior change).

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersInlineTags.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersSemanticHighlightAnnotator.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/psi/AntlersInlineTagsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/psi/AntlersInlineTagsTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.psi

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersInlineTagsTest : BasePlatformTestCase() {

    // `collection` is a bundled catalog tag, so isTag("collection") is true without any project setup.
    private fun identNamed(text: String, name: String): PsiElement {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.collectElements(antlers) {
            it.node?.elementType == AntlersTypes.T_IDENT && it.text == name
        }.first()
    }

    fun testInlineTagHeadIsRecognized() {
        // `{collection …}` as an inline tag call: bare T_IDENT preceded by `{`.
        assertTrue(AntlersInlineTags.isInlineTagHead(identNamed("{{ x = {collection from=\"blog\"} }}", "collection")))
    }

    fun testArrayKeyIsNotInlineTagHead() {
        // `{collection: 'y'}` — next sibling is `:`, so it is an array key, not a tag.
        assertFalse(AntlersInlineTags.isInlineTagHead(identNamed("{{ x = {collection: 'y'} }}", "collection")))
    }

    fun testStandaloneTagHeadIsNotInlineTagHead() {
        // `{{ collection }}` head has no preceding `{` sibling — handled by the NamePath branch, not here.
        assertFalse(AntlersInlineTags.isInlineTagHead(identNamed("{{ collection from=\"blog\" }}", "collection")))
    }

    fun testUnknownNameAfterBraceIsNotInlineTagHead() {
        assertFalse(AntlersInlineTags.isInlineTagHead(identNamed("{{ x = {not_a_tag y=\"z\"} }}", "not_a_tag")))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInlineTagsTest"`
Expected: FAIL — `AntlersInlineTags` unresolved (does not exist yet).

- [ ] **Step 3: Create the shared helper**

Create `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersInlineTags.kt`:
```kotlin
package com.github.balotias.intellijantlers.psi

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * An inline tag-call head: a bare `T_IDENT` opening `{tag …}` — preceded by `{`, not followed by `:` (so it
 * is not an array key like `{collection: 'x'}`), whose text is a known catalog tag.
 *
 * In `{{ x = {obfuscate_link …} }}` the parser leaves the tag name as a bare T_IDENT directly under the
 * statement (not wrapped in a NAME_PATH). Shared by the semantic highlighter (tag coloring) and the
 * go-to-declaration reference so coloring and navigation agree by construction.
 */
object AntlersInlineTags {
    fun isInlineTagHead(element: PsiElement): Boolean {
        if (element.node?.elementType != AntlersTypes.T_IDENT) return false
        if (PsiTreeUtil.skipWhitespacesBackward(element)?.node?.elementType != AntlersTypes.T_LBRACE) return false
        if (PsiTreeUtil.skipWhitespacesForward(element)?.node?.elementType == AntlersTypes.T_COLON) return false
        return AntlersCatalogService.getInstance(element.project).isTag(element.text)
    }
}
```

- [ ] **Step 4: Switch the annotator to the shared helper**

In `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersSemanticHighlightAnnotator.kt`:

Add the import (with the other `psi` imports):
```kotlin
import com.github.balotias.intellijantlers.psi.AntlersInlineTags
```

Change the `else` branch in `annotate`'s `when` from:
```kotlin
            else -> if (isInlineTagHead(element)) paint(holder, element, AntlersSyntaxHighlighter.TAG)
```
to:
```kotlin
            else -> if (AntlersInlineTags.isInlineTagHead(element)) paint(holder, element, AntlersSyntaxHighlighter.TAG)
```

Delete the now-unused private function and its KDoc (the whole block):
```kotlin
    /**
     * True when [element] is a bare `T_IDENT` opening an inline tag call: preceded by `{` and not followed
     * by `:` (which would make it an array key like `{collection: 'x'}`), whose text is a known tag.
     */
    private fun isInlineTagHead(element: PsiElement): Boolean {
        if (element.node.elementType != AntlersTypes.T_IDENT) return false
        if (PsiTreeUtil.skipWhitespacesBackward(element)?.node?.elementType != AntlersTypes.T_LBRACE) return false
        if (PsiTreeUtil.skipWhitespacesForward(element)?.node?.elementType == AntlersTypes.T_COLON) return false
        return AntlersCatalogService.getInstance(element.project).isTag(element.text)
    }
```
NOTE: leave the other imports (`AntlersCatalogService`, `AntlersTypes`, `PsiTreeUtil`) — they are still used elsewhere in the annotator (the `AntlersNamePathMixin` branch, `firstIdent`, etc.). Do not remove them. If the Kotlin compiler reports any of them as now-unused, remove only the genuinely unused ones and report which.

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInlineTagsTest" --tests "*SemanticHighlight*"`
Expected: PASS — the 4 new helper tests pass and the existing semantic-highlight / inline-tag-coloring tests stay green (behavior unchanged).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersInlineTags.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersSemanticHighlightAnnotator.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/psi/AntlersInlineTagsTest.kt
git commit -m "refactor(psi): extract shared AntlersInlineTags.isInlineTagHead"
```

---

### Task 2: Attach the PHP-class reference for inline tag heads

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersInlineTagGotoDeclTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersInlineTagGotoDeclTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersInlineTagGotoDeclTest : BasePlatformTestCase() {

    // The scanner is text-based (no PHP plugin): a `.php` file whose path contains `/Tags/` with
    // `class ObfuscateLink extends Tags` maps handle `obfuscate_link` -> this class.
    private fun addTagClass() {
        myFixture.addFileToProject(
            "app/Tags/ObfuscateLink.php",
            "<?php\nnamespace App\\Tags;\nuse Statamic\\Tags\\Tags;\nclass ObfuscateLink extends Tags {}\n"
        )
    }

    private fun identNamed(text: String, name: String): PsiElement {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.collectElements(antlers) {
            it.text == name && it.references.isNotEmpty()
        }.firstOrNull()
            ?: PsiTreeUtil.collectElements(antlers) { it.text == name }.first()
    }

    private fun resolvedFileName(ident: PsiElement): String? =
        ident.references.firstNotNullOfOrNull { it.resolve() }?.containingFile?.name

    fun testInlineTagCallResolvesToPhpClass() {
        addTagClass()
        // The failing case from the bug report: inline tag call inside an assignment.
        val ident = identNamed("{{ href = {obfuscate_link href=\"x\"} }}", "obfuscate_link")
        assertEquals("ObfuscateLink.php", resolvedFileName(ident))
    }

    fun testStandaloneTagStillResolves() {
        addTagClass()
        // Regression: the already-working standalone form.
        val ident = identNamed("{{ obfuscate_link href=\"x\" }}", "obfuscate_link")
        assertEquals("ObfuscateLink.php", resolvedFileName(ident))
    }

    fun testArrayKeyGetsNoPhpClassReference() {
        addTagClass()
        // `{collection: 'y'}` — `collection` is an array key, not a tag: no PHP-class reference attached.
        val ident = identNamed("{{ x = {collection: 'y'} }}", "collection")
        assertFalse(
            "array key must not get a PHP-class reference",
            ident.references.any { it is AntlersPhpClassReference }
        )
    }
}
```
NOTE: `identNamed` prefers an ident that already has references (the inline/standalone head) but falls back to the first match (the array-key case, which should have none) so the negative test can still locate its ident.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInlineTagGotoDeclTest"`
Expected: FAIL — `testInlineTagCallResolvesToPhpClass` fails (inline head has no reference, so `resolvedFileName` is null). `testStandaloneTagStillResolves` and the array-key test may already pass.

- [ ] **Step 3: Attach the reference**

In `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt`:

Add the import (with the other `psi` imports near the top):
```kotlin
import com.github.balotias.intellijantlers.psi.AntlersInlineTags
```

In `refsForIdent`, insert the inline-tag-head branch **after** the modifier-name `let { … }` block and **before** the `// Tag head:` NamePath lookup (`val path = PsiTreeUtil.getParentOfType(element, AntlersNamePathMixin::class.java) ?: return emptyArray()`):
```kotlin
        // Inline tag call `{tag …}` (e.g. `href = {obfuscate_link …}`): the tag name is a bare T_IDENT,
        // not a NAME_PATH head, so attach the PHP-class reference here too (mirrors the inline-tag coloring,
        // sharing AntlersInlineTags.isInlineTagHead). Tags only — modifiers are never inline-call syntax.
        if (AntlersInlineTags.isInlineTagHead(element)) {
            return arrayOf(AntlersPhpClassReference(element, name, isModifier = false))
        }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInlineTagGotoDeclTest"`
Expected: PASS (3 tests) — the inline tag head now resolves to `ObfuscateLink.php`; standalone still resolves; the array key gets no PHP-class reference.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersInlineTagGotoDeclTest.kt
git commit -m "feat(references): go-to-declaration for inline tag-call custom tags"
```

---

### Task 3: Full-suite gate

**Files:** none (verification only).

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL. Existing reference, coloring, and rename tests stay green.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml; echo "exit:$?"`
Expected: prints nothing, `exit:1`.

- [ ] **Step 3: Count check**

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END{print s}'`
Expected: previous total + 7 (4 helper tests + 3 reference tests), zero failures/errors.

If green, proceed to whole-feature review and `superpowers:finishing-a-development-branch`.

---

## Notes for the implementer

- This is a small bug fix on an existing feature (go-to-declaration already works for standalone tags and for modifiers). The only gap is the inline tag-call form `{{ x = {tag …} }}`.
- `AntlersInlineTags.isInlineTagHead` is the single source of truth shared by coloring and navigation — do not re-inline it.
- The reference is **soft** (`AntlersPhpClassReference`), so an inline tag with no resolvable PHP class is never flagged as an error.
- Modifiers need no change: they only ever appear as `| modifier` (an `AntlersModifierMixin`), which already gets the reference, including inside string-interpolation injection. There is no inline-modifier-call syntax.
- Run the **full suite with `--rerun-tasks`** and gate on the result XML — not just `compileTestKotlin`.
