# Antlers Completion Insert UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the parameter/modifier insert caret, add idiomatic colon-form tag prefills, ensure auto-popup inside `{{ }}`, and pre-select the nearest unclosed tag when typing `{{ /`.

**Architecture:** Insert-handler edits (C1, C2), a typed-handler `checkAutoPopup` (C3), and an `isClosing` completion flag + nearest-unclosed helper + provider prioritization (C4). All additive; no parser/grammar change.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, `BasePlatformTestCase`. Tests run via `./gradlew test`; build cache returns cached results → use `--rerun-tasks`, gate on `build/test-results/test/*.xml`.

**Verified facts:**
- `ParameterInsertHandler` (object) inserts `="\"\""`; `ModifierInsertHandler(takesArguments)` inserts `()` — both read `context.tailOffset` twice (the bug).
- `AntlersTagInsertHandler(isPair)` runs after the platform inserted the bare name; the normal flow has `}}` already present (`alreadyClosed`).
- `AntlersCompletionContext.classify` `T_SLASH` branch returns `AntlersCompletionInfo(TAG_NAME)` for `{{ /<caret>`. `AntlersCompletionInfo(kind, tagHead, pathPrefix, paramName)`.
- `AntlersNestingTreeBuilder` open/close rules: openers = catalog `isPair` tag head OR `if`/`unless` condition; closers = closing tag (`closedName` before `:`) OR `endif`/`endunless`. `CONDITION_OPENERS`/`CONDITION_CLOSERS` are private vals there.
- `AntlersTagInsertTest` exists (the `completeTag(textWithCaret, tag)` lookup-select helper); `AntlersTypedHandler` is a `TypedHandlerDelegate`.
- Branch: `antlers-completion-insert-ux` (current).

---

### Task 1: C1 — parameter/modifier insert caret fix

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/ParameterInsertHandler.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/ModifierInsertHandler.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamModifierInsertTest.kt` (create)

- [ ] **Step 1: Write the failing test** (drives the real handler)

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersParamModifierInsertTest : BasePlatformTestCase() {

    private fun completeItem(textWithCaret: String, predicate: (String) -> Boolean): Boolean {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        val lookup = myFixture.lookup ?: return false
        val item = lookup.items.firstOrNull { predicate(it.lookupString) } ?: return false
        lookup.currentItem = item
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        return true
    }

    fun testParameterCaretInsideQuotes() {
        // any collection parameter works; assert the caret lands between the quotes.
        assertTrue("a parameter should be offered", completeItem("{{ collection <caret> }}") { true })
        val text = myFixture.file.text
        val caret = myFixture.caretOffset
        assertTrue("inserted name=\"\": $text", text.contains("=\"\""))
        assertEquals("char before caret is opening quote", '"', text[caret - 1])
        assertEquals("char at caret is closing quote", '"', text[caret])
    }

    fun testModifierCaretInsideParens() {
        // 'truncate' takes arguments → `()` inserted, caret inside.
        assertTrue("truncate modifier offered", completeItem("{{ title | <caret> }}") { it == "truncate" })
        val text = myFixture.file.text
        val caret = myFixture.caretOffset
        assertTrue("inserted (): $text", text.contains("truncate()"))
        assertEquals('(', text[caret - 1])
        assertEquals(')', text[caret])
    }
}
```

- [ ] **Step 2: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersParamModifierInsertTest"`
Expected: FAIL — caret lands past the quotes/parens (in the `}}`).

- [ ] **Step 3: Fix `ParameterInsertHandler`** — capture the offset once:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/** Inserts a tag parameter as `name="<caret>"`. */
object ParameterInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val tail = "=\"\""
        val at = context.tailOffset                              // capture BEFORE inserting (tailOffset advances)
        context.document.insertString(at, tail)
        context.editor.caretModel.moveToOffset(at + tail.length - 1)
        context.commitDocument()
    }
}
```

- [ ] **Step 4: Fix `ModifierInsertHandler`** — same:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/** Inserts a modifier; if it takes arguments, adds `()` and puts the caret inside. */
class ModifierInsertHandler(private val takesArguments: Boolean) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        if (!takesArguments) return
        val tail = "()"
        val at = context.tailOffset
        context.document.insertString(at, tail)
        context.editor.caretModel.moveToOffset(at + 1)
        context.commitDocument()
    }
}
```

- [ ] **Step 5: Run, confirm pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersParamModifierInsertTest"`
Expected: PASS. If `truncate` isn't offered, pick any modifier whose `takesArguments` is true from the catalog and adjust the predicate.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/ParameterInsertHandler.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/ModifierInsertHandler.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamModifierInsertTest.kt
git commit -m "Fix parameter/modifier completion caret (capture tailOffset before inserting)"
```

---

### Task 2: C2 — colon-form tag prefills

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt`
- Test: append to `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt`

- [ ] **Step 1: Append the failing tests** to `AntlersTagInsertTest` (it has `completeTag(textWithCaret, tag)`):

```kotlin
    fun testCollectionPrefillsColonForm() {
        completeTag("{{ collection<caret> }}", "collection")
        assertEquals("{{ collection: }}{{ /collection }}", myFixture.file.text)
        assertEquals("{{ collection:".length, myFixture.caretOffset)   // caret right after the colon
    }

    fun testPartialPrefillsColonForm() {
        completeTag("{{ partial<caret> }}", "partial")
        assertEquals("{{ partial: }}", myFixture.file.text)
        assertEquals("{{ partial:".length, myFixture.caretOffset)
    }

    fun testNonHandleTagKeepsParamSlot() {
        // 'cache' is a pair tag NOT in the colon-handle set → param-slot behavior (no colon).
        completeTag("{{ cache<caret> }}", "cache")
        assertEquals("{{ cache  }}{{ /cache }}", myFixture.file.text)
        assertEquals("{{ cache ".length, myFixture.caretOffset)
    }
```

(If `cache` is not a catalog pair tag, substitute any catalog `isPair` tag not in the colon set; verify via the catalog.)

- [ ] **Step 2: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersTagInsertTest"`
Expected: FAIL — `collection`/`partial` currently get the param-slot, not the colon form.

- [ ] **Step 3: Add the colon-form branch at the TOP of `AntlersTagInsertHandler.handleInsert`**

Add the set as a class-level `private val`:
```kotlin
    private val colonHandleTags = setOf("collection", "taxonomy", "nav", "foreach", "partial")
```
Insert this block immediately after `val alreadyClosed = …` and BEFORE the `if (!isPair)` block:
```kotlin
        if (name in colonHandleTags) {
            // Idiomatic colon form: `{{ collection:<caret> }}` (+ closer for pair tags).
            if (alreadyClosed) document.insertString(nameEnd, ":")
            else document.insertString(nameEnd, ": }}")
            val caretPos = nameEnd + 1                       // right after the ':'
            if (isPair) {
                val close = document.charsSequence.toString().indexOf("}}", caretPos)
                if (close >= 0) document.insertString(close + 2, "{{ /$name }}")
            }
            context.editor.caretModel.moveToOffset(caretPos)
            context.commitDocument()
            return
        }
```

- [ ] **Step 4: Run, confirm pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersTagInsertTest"`
Expected: PASS (the new 3 + the existing pair/single tests). If a colon-form expected string is off by whitespace, trace the offsets against the actual `myFixture.file.text` and adjust the expected string — keep the colon-after-name + caret-after-colon contract.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt
git commit -m "Colon-form prefill for handle-taking tags (collection/nav/taxonomy/foreach/partial)"
```

---

### Task 3: C4 — closing-tag pre-selects the nearest unclosed

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt` (add `isClosing`)
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilder.kt` (add `nearestUnclosedAt`)
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersClosingTagTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersClosingTagTest : BasePlatformTestCase() {

    private fun firstSuggestion(text: String): String? {
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", "<caret>"))
        myFixture.completeBasic()
        return myFixture.lookupElementStrings?.firstOrNull()
    }

    fun testNearestUnclosedPreselected() {
        // innermost open tag is `collection`
        assertEquals("collection",
            firstSuggestion("{{ collection:blog }}{{ /<caret> }}"))
    }

    fun testInnermostOfNested() {
        // collection then if open → nearest unclosed is `if`
        assertEquals("if",
            firstSuggestion("{{ collection:blog }}{{ if x }}{{ /<caret> }}"))
    }

    fun testNoUnclosedNoCrash() {
        // nothing open before the closer → first suggestion is just whatever the catalog offers (no crash)
        firstSuggestion("{{ /<caret> }}")   // must not throw
    }
}
```

- [ ] **Step 2: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersClosingTagTest"`
Expected: FAIL — the nearest-unclosed tag is not prioritized first.

- [ ] **Step 3: Add `isClosing` to `AntlersCompletionContext`**

In `AntlersCompletionInfo`, add `val isClosing: Boolean = false`. In the `T_SLASH` branch, return the
closing case with the flag:
```kotlin
            AntlersTypes.T_SLASH ->
                if (prevSignificantLeaf(prev, statement)?.node?.elementType == AntlersTypes.T_LDOUBLE)
                    AntlersCompletionInfo(AntlersCompletionKind.TAG_NAME, isClosing = true)
                else AntlersCompletionInfo(AntlersCompletionKind.NONE)
```

- [ ] **Step 4: Add `nearestUnclosedAt` to `AntlersNestingTreeBuilder`**

```kotlin
    /** Name of the innermost still-open pair tag / condition strictly before [beforeOffset], or null. */
    fun nearestUnclosedAt(root: PsiElement, beforeOffset: Int, project: Project): String? {
        val catalog = if (project.isDefault) null else AntlersCatalogService.getInstance(project)
        val stack = ArrayDeque<String>()
        val statements = PsiTreeUtil.findChildrenOfType(root, AntlersStatement::class.java)
            .filter { it.textRange.startOffset < beforeOffset }
            .sortedBy { it.textRange.startOffset }
        for (stmt in statements) {
            stmt.closingTag?.let { closing ->
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':')
                if (name != null) { val i = stack.indexOfLast { it == name }; if (i >= 0) while (stack.size > i) stack.removeLast() }
                return@let
            }
            stmt.condition?.let { cond ->
                val kw = (cond as? AntlersConditionMixin)?.keyword ?: return@let
                val opener = CONDITION_CLOSERS[kw]
                if (opener != null) { val i = stack.indexOfLast { it == opener }; if (i >= 0) while (stack.size > i) stack.removeLast() }
                else if (kw in CONDITION_OPENERS) stack.addLast(kw)
                return@let
            }
            val head = (stmt.namePath as? AntlersNamePathMixin)?.head ?: continue
            if (head.isNotBlank() && catalog?.tag(head)?.isPair == true) stack.addLast(head)
        }
        return stack.lastOrNull()
    }
```
Note: `stmt.closingTag`/`stmt.condition`/`stmt.namePath` accessors are the ones the existing `build()`
uses — match their exact names/types. The `?.let { … return@let }` pattern skips to the next statement
after handling a closer/condition; if `continue`-from-`let` doesn't read cleanly in this SDK, restructure
as `when`/`if-else if` mirroring `build()`.

- [ ] **Step 5: Prioritize it in the provider's `TAG_NAME` branch**

At the START of the `AntlersCompletionKind.TAG_NAME ->` block (before offering the catalog tags):
```kotlin
                if (info.isClosing) {
                    val stmt = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                        parameters.position, com.github.balotias.intellijantlers.psi.AntlersStatement::class.java)
                    val before = stmt?.textRange?.startOffset ?: parameters.offset
                    val unclosed = com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
                        .nearestUnclosedAt(parameters.position.containingFile, before, project)
                    if (unclosed != null) {
                        result.addElement(
                            com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority(
                                LookupElementBuilder.create(unclosed)
                                    .withIcon(AntlersIcons.FILE).withTypeText("Close tag"),
                                Double.MAX_VALUE
                            )
                        )
                    }
                }
```
(The closer's `}}` is already present from `{{ /<caret> }}`, so the default insert — just the name — yields `{{ /collection }}`; no custom insert handler needed.)

- [ ] **Step 6: Run, confirm pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersClosingTagTest"`
Expected: PASS. If `firstOrNull()` isn't the prioritized item, confirm `PrioritizedLookupElement.withPriority(..., Double.MAX_VALUE)` sorts first and that `lookupElementStrings` reflects sort order (it does). If the nested `if` case returns `collection`, the stack walk's condition handling is off — debug against `nearestUnclosedAt` directly.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilder.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersClosingTagTest.kt
git commit -m "Pre-select the nearest unclosed tag when completing {{ / }}"
```

---

### Task 4: C3 — auto-popup inside `{{ }}` (verify; fix if suppressed)

**Files:**
- Modify (if needed): `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTypedHandler.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersAutoPopupTest.kt` (create)

- [ ] **Step 1: Verify whether auto-popup already works**

Write a `CompletionAutoPopupTestCase`-based test (it extends the platform's auto-popup harness, NOT
`BasePlatformTestCase`):
```kotlin
package com.github.balotias.intellijantlers.editor

import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase

class AntlersAutoPopupTest : CompletionAutoPopupTestCase() {
    fun testPopupInsideBraces() {
        myFixture.configureByText("p.antlers.html", "{{ <caret> }}")
        type("c")
        assertNotNull("completion should auto-popup inside {{ }}", lookup)
    }
}
```
Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.editor.AntlersAutoPopupTest"`
- If it **PASSES** → auto-popup already works; skip Steps 2-3, note "no fix needed", and commit just the test.
- If it **FAILS** (or the harness is unavailable here) → proceed to Step 2 to add the trigger. If the
  harness genuinely can't run in this environment, write a plain `BasePlatformTestCase` unit test of
  the `checkAutoPopup` predicate instead (right char + inside `{{ }}` → STOP/scheduled; HTML region or
  wrong char → CONTINUE) and document that scheduling is verified manually.

- [ ] **Step 2: Add `checkAutoPopup` to `AntlersTypedHandler` (only if Step 1 showed it's suppressed)**

```kotlin
    override fun checkAutoPopup(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.viewProvider.baseLanguage !== AntlersLanguage.INSTANCE) return Result.CONTINUE
        if (!(c.isLetter() || c == '_' || c == ':' || c == '|')) return Result.CONTINUE
        val el = file.findElementAt(editor.caretModel.offset - 1)
        if (PsiTreeUtil.getParentOfType(el, com.github.balotias.intellijantlers.psi.AntlersStatement::class.java) == null)
            return Result.CONTINUE
        com.intellij.codeInsight.completion.AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }
```
Add imports (`AutoPopupController`, `PsiTreeUtil`, `AntlersStatement`).

- [ ] **Step 3: Run, confirm the auto-popup test now passes (if Step 2 was needed)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.editor.AntlersAutoPopupTest"`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Auto-popup completion inside {{ }} (<verified working | added checkAutoPopup trigger>)"
```
(Use the accurate subject for the outcome.)

---

### Task 5: Full-suite verification

- [ ] **Step 1:** `./gradlew --rerun-tasks test` → BUILD SUCCESSFUL.
- [ ] **Step 2:** `echo "failed_files=$(grep -lo 'failures=\"[1-9]\|errors=\"[1-9]' build/test-results/test/*.xml | wc -l | tr -d ' ')"` → `failed_files=0`. Note total (prior 236 + new).
- [ ] **Step 3:** `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL.

---

## Self-Review

**Spec coverage:** C1 caret fix → Task 1; C2 colon prefills → Task 2; C4 closing-tag prioritization → Task 3; C3 auto-popup (verify-first) → Task 4; verification → Task 5. Out-of-scope (true auto-insert, per-TagDef flag) not implemented.

**Placeholder scan:** none — handler fixes are full code; tests have concrete assertions (with documented "adjust to actual catalog param / whitespace" escapes). Task 4 is verify-first with both branches specified (no fix vs add trigger) — a bounded decision, not a placeholder.

**Type/name consistency:** `AntlersCompletionInfo.isClosing` defined in Task 3 Step 3, consumed in Step 5; `nearestUnclosedAt(root, beforeOffset, project)` defined Step 4, called Step 5; `colonHandleTags` set in Task 2; the insert-handler `at = context.tailOffset` capture identical in both C1 handlers. The nesting accessors (`closingTag`/`condition`/`namePath`/`closedName`/`keyword`/`head`) match `AntlersNestingTreeBuilder.build()`.

**Known SDK-shape risks flagged inline:** `CompletionAutoPopupTestCase` availability (Task 4); the `?.let { return@let }` skip pattern (Task 3 Step 4, restructure if needed); catalog param/modifier names in tests (`truncate`, `cache` — substitute if absent).
