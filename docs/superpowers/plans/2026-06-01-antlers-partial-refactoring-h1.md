# Antlers Partial Refactoring H1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Renaming/moving a Statamic partial file updates every `{{ partial:… }}` include, and Find Usages on a partial lists its includes.

**Architecture:** Add `handleElementRename` + `bindToElement` to `AntlersPartialReference` (it already resolves an include to the partial file). Rename swaps the last path segment; move rewrites the full path (string form). Rewrites use `LeafPsiElement.replaceWithText`. Find-usages comes free from the file-resolving reference.

**Tech Stack:** Kotlin, IntelliJ Platform (`PsiReferenceBase` rename hooks), JUnit `BasePlatformTestCase` (`renameElement`, `findUsages`).

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-partial-refactoring-h1-design.md`

**TDD note:** `./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`. Full suite is currently **184/184 green**. Keep it green.

---

## Task 1: Rename support + find-usages

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReference.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialRefactoringTest.kt` (new)

- [ ] **Step 1: Write the failing refactoring tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialRefactoringTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialRefactoringTest : BasePlatformTestCase() {

    private fun addPartial(path: String) = myFixture.addFileToProject(path, "<div>card</div>")
    private fun addTemplate(text: String): PsiFile =
        myFixture.addFileToProject("resources/views/page.antlers.html", text)

    fun testRenameStringForm() {
        val partial = addPartial("resources/views/blog/card.antlers.html")
        val tmpl = addTemplate("{{ partial:src=\"blog/card\" }}")
        myFixture.renameElement(partial, "tile.antlers.html")
        assertEquals("{{ partial:src=\"blog/tile\" }}", tmpl.text)
    }

    fun testRenameColonForm() {
        val partial = addPartial("resources/views/blog/card.antlers.html")
        val tmpl = addTemplate("{{ partial:blog/card }}")
        myFixture.renameElement(partial, "tile.antlers.html")
        assertEquals("{{ partial:blog/tile }}", tmpl.text)
    }

    fun testBindToElementMove() {
        addPartial("resources/views/blog/card.antlers.html")
        val text = "{{ partial:src=\"blog/card\" }}"
        val caret = text.indexOf("card")
        val tmpl = addTemplate(text)
        val moved = addPartial("resources/views/shared/card.antlers.html")
        val ref = tmpl.findReferenceAt(caret) as? AntlersPartialReference
            ?: error("no partial reference at caret")
        WriteCommandAction.runWriteCommandAction(project) { ref.bindToElement(moved) }
        assertTrue("path rewritten to shared/card: ${tmpl.text}", tmpl.text.contains("shared/card"))
    }

    fun testFindUsages() {
        val partial = addPartial("resources/views/blog/card.antlers.html")
        addTemplate("{{ partial:src=\"blog/card\" }}")
        val usages = myFixture.findUsages(partial)
        assertTrue("expected an include usage: ${usages.map { it.element?.text }}", usages.isNotEmpty())
    }

    fun testUnrelatedRenameLeavesTemplateAlone() {
        addPartial("resources/views/blog/card.antlers.html")
        val tmpl = addTemplate("{{ partial:src=\"blog/card\" }}")
        val other = myFixture.addFileToProject("resources/views/other.antlers.html", "x")
        myFixture.renameElement(other, "renamed.antlers.html")
        assertEquals("{{ partial:src=\"blog/card\" }}", tmpl.text)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.references.AntlersPartialRefactoringTest" --no-configuration-cache`
Expected: FAIL — the rename tests fail (no rename hooks; the template is unchanged after `renameElement`).
`testFindUsages` may already pass (the reference resolves to the file); the rename tests are the red.

- [ ] **Step 3: Add the rename hooks**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReference.kt`.

Add imports (next to the existing ones):

```kotlin
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
```

Add these overrides + helpers inside the class (e.g. after `resolve()`):

```kotlin
    override fun handleElementRename(newElementName: String): PsiElement {
        val newSegment = stripPartialExtensions(newElementName)
        val rangeText = rangeInElement.substring(element.text)
        val newRangeText =
            if (rangeText.contains('/')) rangeText.substringBeforeLast('/') + "/" + newSegment
            else newSegment
        return rewrite(newRangeText)
    }

    override fun bindToElement(targetElement: PsiElement): PsiElement {
        val file = targetElement as? PsiFile ?: return element
        val root = StatamicProject.viewsRoot(element) ?: return element
        val newFullPath = relativePathMinusExt(root, file.virtualFile ?: return element) ?: return element
        val rangeText = rangeInElement.substring(element.text)
        // String form: range holds the whole path → write the full new path (handles dir moves).
        // Colon form: range holds only the last segment → best-effort last-segment swap.
        val newRangeText = if (rangeText == path) newFullPath else newFullPath.substringAfterLast('/')
        return rewrite(newRangeText)
    }

    private fun rewrite(newRangeText: String): PsiElement {
        val leaf = element as? LeafPsiElement ?: return element
        val old = leaf.text
        val newText = old.substring(0, rangeInElement.startOffset) + newRangeText +
            old.substring(rangeInElement.endOffset)
        return leaf.replaceWithText(newText)
    }

    private fun stripPartialExtensions(name: String): String =
        name.removeSuffix(".antlers.html").removeSuffix(".html")

    private fun relativePathMinusExt(
        root: com.intellij.openapi.vfs.VirtualFile,
        vf: com.intellij.openapi.vfs.VirtualFile
    ): String? {
        val rel = getRelativePath(root, vf) ?: return null
        return rel.removeSuffix(".antlers.html").removeSuffix(".html")
    }
```

(`getRelativePath` and `StatamicProject` already exist in this file/package. `element`, `rangeInElement`,
and `path` are the reference's own members.)

- [ ] **Step 4: Add a word-scanner `FindUsagesProvider`**

This makes Antlers `T_IDENT`/`T_STRING` words feed the IdIndex, so `ReferencesSearch` (used by both
rename and Find Usages) reliably scans templates containing the partial's name in a real IDE — not just
the small test fixture. Create
`src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersFindUsagesProvider.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lexer.FlexAdapter
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet

/** Feeds Antlers identifiers/strings to the word index so partial references are searchable. */
class AntlersFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        FlexAdapter(_AntlersLexer(null)),
        TokenSet.create(AntlersTypes.T_IDENT, AntlersTypes.T_STRING),
        TokenSet.create(AntlersTypes.T_COMMENT_TEXT),
        TokenSet.EMPTY
    )

    // We don't define custom find-usages target symbols (partials are file targets); only the scanner matters.
    override fun canFindUsagesFor(element: PsiElement): Boolean = false
    override fun getType(element: PsiElement): String = ""
    override fun getDescriptiveName(element: PsiElement): String = (element as? PsiNamedElement)?.name ?: ""
    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
    override fun getHelpId(element: PsiElement): String? = null
}
```

Register it in `src/main/resources/META-INF/plugin.xml` (after the `lang.psiStructureViewFactory` line):

```xml
        <lang.findUsagesProvider language="Antlers"
            implementationClass="com.github.balotias.intellijantlers.references.AntlersFindUsagesProvider"/>
```

- [ ] **Step 5: Run the refactoring tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.references.AntlersPartialRefactoringTest" --no-configuration-cache`
Expected: PASS (5 tests).

- [ ] **Step 6: Run the partial-reference regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.references.*" --no-configuration-cache`
Expected: PASS — resolve/completion behavior unchanged; only rename hooks added.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/ src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialRefactoringTest.kt src/main/resources/META-INF/plugin.xml
git commit -m "Add partial rename support (file rename/move updates includes) + find-usages"
```

---

## Task 2: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew test --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm no failures**

Run: `grep -lo 'failures="[1-9]' build/test-results/test/*.xml | wc -l`
Expected: `0`.

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc`
Expected: **~189** (184 prior + 5 new). Exact count may differ; **0 failures** is the gate.

- [ ] **Step 3: Verify production compilation**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

---

## Notes for the implementer

- `handleElementRename` is called by the platform on each include reference when the partial file is
  renamed; `bindToElement` on move. Both rewrite the reference's leaf via `LeafPsiElement.replaceWithText`
  (no `ElementManipulator` EP needed — the custom `AntlersStringLeaf`/`AntlersIdentLeaf` extend
  `LeafPsiElement`).
- The uniform last-segment swap works for both syntaxes because the reference's range text is the full
  path (`src="…"`) or just the last segment (colon form); swapping the last `/`-segment of that text is
  correct in both cases. `bindToElement` distinguishes them via `rangeText == path`.
- `renameElement`/`findUsages` reach our references through `ReferencesSearch`, which relies on the
  IdIndex. The `AntlersFindUsagesProvider`'s word-scanner (Step 4) is what feeds Antlers identifiers/strings
  to that index, so rename/find-usages work in a real IDE, not just the test fixture. `canFindUsagesFor`
  returns false (partials are file targets; we don't define custom find-usages symbols) — only the scanner
  matters.
- `tmpl.text` reflects the PSI `replaceWithText` edit synchronously (PSI change in a write action — not a
  raw document edit like G2), so no commit is needed in these tests.
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
```
