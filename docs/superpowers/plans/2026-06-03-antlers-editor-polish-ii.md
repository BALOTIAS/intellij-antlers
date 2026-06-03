# Antlers Editor Polish II Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Three independent tasks; each gates on the full suite.

**Goal:** (A) Tab from a completed condition jumps into the block; (B) modifier/tag/param hover docs + official link; (C) tags colored distinctly from logic keywords.

**Reference spec:** `docs/superpowers/specs/2026-06-03-antlers-editor-polish-ii-design.md`
**Branch:** `antlers-editor-polish-ii` (create from `main`).

**Test gate (after every test run):** `./gradlew --rerun-tasks test` then
`grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` — must print nothing. `--rerun-tasks` required.

---

### Task A: Tab from a condition jumps into the block

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamSession.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamTabHandler.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersLogicKeywords.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersConditionTabTest.kt` (new)

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersConditionTabTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersConditionTabTest : BasePlatformTestCase() {

    private fun complete(textWithCaret: String, keyword: String) {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        myFixture.lookup?.let { lk ->
            val item = lk.items.firstOrNull { it.lookupString == keyword } ?: return
            lk.currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }

    private fun tab() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)
    private fun doc() = myFixture.editor.document.text

    fun testTabAfterConditionJumpsIntoBlock() {
        complete("{{ i<caret> }}", "if")
        myFixture.type("test")
        tab()
        assertEquals("{{ if test }}{{ /if }}", doc())
        assertEquals("{{ if test }}".length, myFixture.caretOffset)
    }

    fun testEmptyConditionTabJumpsIntoBlock() {
        complete("{{ i<caret> }}", "if")          // caret in the empty condition slot
        tab()
        assertEquals("{{ if }}{{ /if }}", doc())
        assertEquals("{{ if }}".length, myFixture.caretOffset)
    }

    fun testSecondTabEndsConditionSession() {
        complete("{{ i<caret> }}", "if")
        myFixture.type("x")
        tab()                                      // into block, reachedTerminal
        assertNotNull(AntlersParamSession.of(myFixture.editor))
        tab()                                      // ends session
        assertNull(AntlersParamSession.of(myFixture.editor))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersConditionTabTest"`
Expected: FAIL — no session is armed for `if`, so Tab does nothing (caret stays in the condition).

- [ ] **Step 3: Add `conditionMode` to `AntlersParamSession`**

In `AntlersParamSession.kt`, add the constructor parameter (keep the others):
```kotlin
class AntlersParamSession(
    val openTagStartMarker: RangeMarker,
    val openTagEndMarker: RangeMarker,
    val isPair: Boolean,
    val conditionMode: Boolean = false,
) {
```
(Default `false` keeps `AntlersTagInsertHandler`'s existing call sites unchanged.)

- [ ] **Step 4: Force the terminal jump in condition mode — `AntlersParamTabHandler`**

In `AntlersParamTabHandler.kt`, change the `slotEmpty` line (currently
`val slotEmpty = before.isEmpty() || before.last() == ' '`) to:
```kotlin
        // Condition mode (if/unless/elseif): always jump to the block — never open a param slot.
        val slotEmpty = session.conditionMode || before.isEmpty() || before.last() == ' '
```

- [ ] **Step 5: Arm a condition session — `AntlersLogicKeywords.kt`**

Add the import (after the existing imports):
```kotlin
import com.github.balotias.intellijantlers.editor.AntlersParamSession
```
Then in `AntlersKeywordInsertHandler.handleInsert`, replace the OPENER/MID tail:
```kotlin
        if (kw.kind == LogicKind.OPENER) {
            document.insertString(openTagCloseStart + 2, "{{ /${kw.closer} }}")
        }
        context.editor.caretModel.moveToOffset(paramCaret)
        context.commitDocument()
    }
}
```
with:
```kotlin
        if (kw.kind == LogicKind.OPENER) {
            document.insertString(openTagCloseStart + 2, "{{ /${kw.closer} }}")
        }
        context.editor.caretModel.moveToOffset(paramCaret)
        context.commitDocument()

        // Arm a condition-mode session so Tab jumps into the block (no param-slot repeating).
        val openTagStart = text.lastIndexOf("{{", nameEnd)
        if (openTagStart >= 0) {
            val startMarker = document.createRangeMarker(openTagStart, openTagStart + 2)
            val endMarker = document.createRangeMarker(openTagCloseStart, openTagCloseStart + 2)
            startMarker.isGreedyToRight = false
            endMarker.isGreedyToLeft = false
            AntlersParamSession.install(
                context.editor,
                AntlersParamSession(startMarker, endMarker, isPair = kw.kind == LogicKind.OPENER, conditionMode = true),
            )
        }
    }
}
```
(PLAIN keywords return earlier and arm nothing — unchanged.)

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersConditionTabTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Full-suite gate** (shared session/handler)

Run: `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: prints nothing.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamSession.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamTabHandler.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersLogicKeywords.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersConditionTabTest.kt
git commit -m "$(cat <<'EOF'
feat: Tab from a completed condition jumps into the block

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task B: Hover documentation for modifiers (and catalog tags/params)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProviderTest.kt`

- [ ] **Step 1: Write the failing test** — add to `AntlersDocumentationProviderTest`:
```kotlin
    fun testHoverResolvesModifierDocElement() {
        val text = "{{ title | up<caret>per }}"
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("t.antlers.html", text.replace("<caret>", ""))
        val provider = AntlersDocumentationProvider()
        val ctx = myFixture.file.findElementAt(caret)
        val el = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, ctx, caret)
        assertNotNull("hover must resolve a documentation element on the modifier", el)
        val doc = provider.generateDoc(el!!, el)
        assertNotNull(doc)
        assertTrue("mentions upper: $doc", doc!!.contains("upper"))
        assertTrue("links to docs: $doc", doc.contains("statamic.dev"))
    }

    fun testHoverNoDocElementOnNonIdent() {
        val text = "{{ title <caret>| upper }}"   // the pipe, not an ident
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("t.antlers.html", text.replace("<caret>", ""))
        val provider = AntlersDocumentationProvider()
        val ctx = myFixture.file.findElementAt(caret)
        assertNull(provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, ctx, caret))
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersDocumentationProviderTest"`
Expected: FAIL — `getCustomDocumentationElement` returns null today (the base `AbstractDocumentationProvider` returns null).

- [ ] **Step 3: Implement `getCustomDocumentationElement`**

In `AntlersDocumentationProvider.kt`, add these imports if missing
(`com.intellij.openapi.editor.Editor`, `com.intellij.psi.PsiFile`) and add the override (e.g. right
after `generateDoc`):
```kotlin
    override fun getCustomDocumentationElement(
        editor: com.intellij.openapi.editor.Editor,
        file: com.intellij.psi.PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        // Modifiers, catalog tags, and params have no PSI reference, so the platform can't resolve a
        // documentation target on hover/Ctrl-Q. Route the T_IDENT under the cursor to generateDoc.
        if (contextElement?.node?.elementType == AntlersTypes.T_IDENT) return contextElement
        val at = file.findElementAt(targetOffset)
        return if (at?.node?.elementType == AntlersTypes.T_IDENT) at else null
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersDocumentationProviderTest"`
Expected: PASS (existing + 2 new).

- [ ] **Step 5: Full-suite gate**

Run: `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: prints nothing.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProviderTest.kt
git commit -m "$(cat <<'EOF'
feat: resolve hover docs for modifiers/tags/params (getCustomDocumentationElement)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task C: Color tags distinctly from logic keywords

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt`

- [ ] **Step 1: Change the `TAG` base color**

In `AntlersSyntaxHighlighter.kt`, change the `TAG` key from `KEYWORD` to `FUNCTION_CALL`:
```kotlin
        val TAG = TextAttributesKey.createTextAttributesKey("ANTLERS_TAG", DefaultLanguageHighlighterColors.FUNCTION_CALL)
```
Leave `KEYWORD` (logic) as `DefaultLanguageHighlighterColors.KEYWORD`. The external name `ANTLERS_TAG`
is unchanged, so the color settings page and all `ANTLERS_TAG`-keyed tests are unaffected.

- [ ] **Step 2: Full-suite gate** (no behavioral test — a default base-color mapping isn't meaningfully
unit-testable; this verifies no regression since the key is unchanged)

Run: `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: prints nothing.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt
git commit -m "$(cat <<'EOF'
feat: color Antlers tags as function-calls, distinct from logic keywords

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review
- **A:** `conditionMode` flag (default false → tag sessions unchanged); one-line `slotEmpty` OR; arming
  mirrors `AntlersTagInsertHandler`. Traced: `if`+`test`+Tab → `{{ if test }}{{ /if }}`, caret at block.
- **B:** `getCustomDocumentationElement` returns the hovered `T_IDENT`; `generateDoc` already yields
  modifier doc + docUrl link; new tests exercise the resolution path the old test skipped.
- **C:** one base-color swap; keys unchanged so the color settings page + semantic-highlight tests hold.
- No parser/grammar/catalog change; each task gates on the full suite.
```
