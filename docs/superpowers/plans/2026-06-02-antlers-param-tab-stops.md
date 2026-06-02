# Antlers Param Tab Stops Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Antlers tag completion a repeating parameter "tab-stop" flow — after completing a tag, Tab in the opening tag creates a fresh param slot when a param was typed, and jumps the caret to the terminal stop (inside the block for a pair, after the tag for a single) when the slot is empty.

**Architecture:** Three additive pieces in `editor/` + `completion/`: (1) `AntlersParamSession`, a marker holder stored on the editor; (2) `AntlersParamTabHandler`, an `EditorTab` action handler that runs the slot logic only while a session is live and delegates otherwise; (3) arming the session (plus a one-space tweak so single tags start in a real slot) from `AntlersTagInsertHandler`. Exit is by lazy validation at Tab time — no caret listener. No parser/grammar/dependency change.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`EditorActionHandler`, `RangeMarker`, editor user-data `Key`), `BasePlatformTestCase`, Gradle.

**Reference spec:** `docs/superpowers/specs/2026-06-02-antlers-param-tab-stops-design.md`

**Branch:** `antlers-param-tab-stops` (create from `main` before Task 1).

**Test gate (use after every test run):** `./gradlew --rerun-tasks test` then
`grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` — it must print **nothing**. The `--rerun-tasks` flag matters: without it Gradle returns cached results. (Per project memory, `./gradlew test` does execute in this environment.)

---

### Task 1: `AntlersParamSession` marker holder

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamSession.kt`

This is a foundation type with no directly-observable behavior on its own (it wraps editor `RangeMarker`s); it is exercised by the integration tests in Tasks 2 and 4. No standalone test.

- [ ] **Step 1: Create the session holder**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamSession.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.Key

/**
 * One active param-entry session, armed right after a tag completion. Tracks the opening tag's `{{`
 * and `}}` via range markers (which shift as the user types params), so the Tab handler can tell
 * "still inside this tag" from "caret moved away" and locate the terminal stop. Stored on the editor
 * via [KEY]; validated lazily on each Tab — there is no caret listener.
 */
class AntlersParamSession(
    val openTagStartMarker: RangeMarker,
    val openTagEndMarker: RangeMarker,
    val isPair: Boolean,
) {
    /** Set once the caret has jumped to the terminal stop; the next Tab ends the session. */
    var reachedTerminal: Boolean = false

    /** Just after the opening `}}` — inside the block for a pair tag, after the tag for a single. */
    fun terminalOffset(): Int = openTagEndMarker.endOffset

    /** True only when both markers are valid, there is a single caret, and it sits inside the tag. */
    fun isValidFor(editor: Editor): Boolean {
        if (!openTagStartMarker.isValid || !openTagEndMarker.isValid) return false
        if (editor.caretModel.caretCount != 1) return false
        val caret = editor.caretModel.offset
        return caret in openTagStartMarker.startOffset..openTagEndMarker.endOffset
    }

    fun dispose() {
        openTagStartMarker.dispose()
        openTagEndMarker.dispose()
    }

    companion object {
        private val KEY: Key<AntlersParamSession> = Key.create("antlers.param.session")

        fun install(editor: Editor, session: AntlersParamSession) {
            clear(editor)
            editor.putUserData(KEY, session)
        }

        fun of(editor: Editor): AntlersParamSession? = editor.getUserData(KEY)

        fun clear(editor: Editor) {
            editor.getUserData(KEY)?.dispose()
            editor.putUserData(KEY, null)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamSession.kt
git commit -m "$(cat <<'EOF'
feat: add AntlersParamSession marker holder for param tab-stops

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `AntlersParamTabHandler` + registration

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamTabHandler.kt`
- Modify: `src/main/resources/META-INF/plugin.xml` (add one `<editorActionHandler>` inside the existing `<extensions defaultExtensionNs="com.intellij">` block, after the `<typedHandler .../>` line at line 31)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamTabTest.kt` (new — start with the no-session delegation case)

- [ ] **Step 1: Write the failing test (no-session delegation)**

Create `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamTabTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersParamTabTest : BasePlatformTestCase() {

    private fun complete(textWithCaret: String, tag: String) {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        myFixture.lookup?.let { lookup ->
            val item = lookup.items.firstOrNull { it.lookupString == tag } ?: return
            lookup.currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }

    private fun tab() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)

    fun testNoSessionTabDelegates() {
        // No completion ran, so no session is armed: Tab must behave normally and not throw.
        myFixture.configureByText("p.antlers.html", "<caret>hello")
        assertNull(AntlersParamSession.of(myFixture.editor))
        tab()
        assertNull(AntlersParamSession.of(myFixture.editor))
        assertTrue("document keeps its content", myFixture.file.text.contains("hello"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

Run: `./gradlew --rerun-tasks test --tests "*AntlersParamTabTest"`
Expected: FAIL — `AntlersParamTabHandler` does not exist yet only if referenced; here it fails because the handler isn't registered, but the test references only `AntlersParamSession` (exists) — so this step may actually PASS already (the platform Tab handler runs, no session). That is acceptable: it pins the delegation contract. If it passes, proceed; if it fails, read the error.

- [ ] **Step 3: Create the Tab handler**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamTabHandler.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

/**
 * Implements the repeating param tab-stop flow on `EditorTab`. Engages only while an
 * [AntlersParamSession] is armed and the caret sits inside the tracked opening tag; otherwise it
 * delegates to the platform's original Tab handler (indent), so normal editing is untouched.
 *
 * Slot rule (param region = just-after-`{{` .. start-of-`}}`):
 *  - the slot is *empty* when the text before the caret is empty or ends with a space → jump to the
 *    terminal stop (collapsing any dangling spaces to a single separator first);
 *  - otherwise a param token ends at the caret → open a fresh slot (insert one space, keep the caret
 *    in the new slot).
 */
class AntlersParamTabHandler(private val original: EditorActionHandler) : EditorActionHandler() {

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        original.isEnabledForCaret(editor, caret, dataContext) || AntlersParamSession.of(editor) != null

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val session = AntlersParamSession.of(editor)
        if (session == null) {
            original.execute(editor, caret, dataContext)
            return
        }
        if (!session.isValidFor(editor) || session.reachedTerminal) {
            AntlersParamSession.clear(editor)
            original.execute(editor, caret, dataContext)
            return
        }

        val document = editor.document
        val offset = editor.caretModel.offset
        val regionStart = session.openTagStartMarker.endOffset  // just after "{{"
        val tagEnd = session.openTagEndMarker.startOffset        // start of "}}"
        if (offset < regionStart || offset > tagEnd) {
            AntlersParamSession.clear(editor)
            original.execute(editor, caret, dataContext)
            return
        }

        val chars = document.charsSequence
        val before = chars.subSequence(regionStart, offset).toString()
        val slotEmpty = before.isEmpty() || before.last() == ' '

        if (!slotEmpty) {
            // A param token ends at the caret → open a fresh slot after it.
            document.insertString(offset, " ")
            editor.caretModel.moveToOffset(offset + 1)
        } else {
            // Empty slot → collapse dangling spaces to a single separator, jump to the terminal stop.
            var lastNonSpace = tagEnd - 1
            while (lastNonSpace >= regionStart && chars[lastNonSpace] == ' ') lastNonSpace--
            val gapStart = lastNonSpace + 1
            if (gapStart < tagEnd) {
                document.replaceString(gapStart, tagEnd, " ")
            }
            editor.caretModel.moveToOffset(session.terminalOffset())
            session.reachedTerminal = true
        }
    }
}
```

- [ ] **Step 4: Register it in `plugin.xml`**

In `src/main/resources/META-INF/plugin.xml`, immediately after the line
`<typedHandler implementation="com.github.balotias.intellijantlers.editor.AntlersTypedHandler"/>` (line 31), add:

```xml
        <editorActionHandler action="EditorTab"
            implementationClass="com.github.balotias.intellijantlers.editor.AntlersParamTabHandler"/>
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersParamTabTest"`
Expected: PASS (1 test). Then run the gate:
`grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` → prints nothing.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersParamTabHandler.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamTabTest.kt
git commit -m "$(cat <<'EOF'
feat: add EditorTab handler for Antlers param tab-stops

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Arm the session from `AntlersTagInsertHandler` (+ single-tag slot tweak)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt`
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt` (single-tag expectations change to the centred param slot)

Currently single tags insert `{{ name }}` with the caret right after the name (`testSingleTagUnchanged`, `testPartialDefaultSingle`). To start the caret in a real slot, single tags now insert the same centred opener as pairs (`{{ name  }}`, caret between the two spaces), minus the closer. This is the only behavioral change to existing insertion, and it updates those two tests.

- [ ] **Step 1: Update the existing single-tag tests to the new expectation (these will fail first)**

In `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt`, replace `testSingleTagUnchanged` and `testPartialDefaultSingle` with:

```kotlin
    fun testSingleTagStartsInParamSlot() {
        completeTag("{{ yield<caret> }}", "yield")
        assertEquals("{{ yield  }}", myFixture.file.text)
        assertEquals("{{ yield ".length, myFixture.caretOffset)
    }

    fun testPartialStartsInParamSlot() {
        completeTag("{{ partial<caret> }}", "partial")
        assertEquals("{{ partial  }}", myFixture.file.text)
        assertEquals("{{ partial ".length, myFixture.caretOffset)
    }
```

(Leave `testCollectionDefaultIsParamSlot` and `testNonHandleTagKeepsParamSlot` unchanged — pair behavior is identical.)

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersTagInsertTest"`
Expected: FAIL — the two renamed tests expect `{{ yield  }}` / `{{ partial  }}` but the handler still produces `{{ yield }}` / `{{ partial }}`.

- [ ] **Step 3: Rewrite `AntlersTagInsertHandler` to unify the opener and arm the session**

Replace the entire body of `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt` with:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Inserts an Antlers tag with a centred parameter slot: `{{ name <caret> }}` (one space each side of
 * the caret), plus `{{ /name }}` appended for pair tags. Starting the caret in a real slot lets the
 * user type a param immediately, and the [AntlersParamSession] armed here drives the repeating
 * Tab-to-next-param / Tab-to-block flow (see AntlersParamTabHandler).
 */
class AntlersTagInsertHandler(private val isPair: Boolean) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val name = item.lookupString
        val nameEnd = context.tailOffset
        val text = document.charsSequence.toString()

        val nextClose = text.indexOf("}}", nameEnd)
        val nextOpen = text.indexOf("{{", nameEnd)
        val alreadyClosed = nextClose >= 0 && (nextOpen < 0 || nextClose < nextOpen)

        // Build the opening tag's param slot. `paramCaret` is the centred caret; `openTagCloseStart`
        // is the offset of this opening tag's "}}".
        val paramCaret: Int
        val openTagCloseStart: Int
        if (alreadyClosed) {
            val gap = text.substring(nameEnd, nextClose)
            if (gap.isBlank()) {
                // `{{ name<gap>}}` → normalize to `{{ name  }}`, caret centred.
                document.replaceString(nameEnd, nextClose, "  ")
                paramCaret = nameEnd + 1
                openTagCloseStart = nameEnd + 2
            } else {
                // Existing params already in the opener → caret right after the name.
                paramCaret = nameEnd
                openTagCloseStart = nextClose
            }
        } else {
            val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
            val opener = if (needsSpace) "  }}" else " }}"
            document.insertString(nameEnd, opener)
            paramCaret = nameEnd + (if (needsSpace) 1 else 0)
            openTagCloseStart = nameEnd + opener.length - 2
        }

        if (isPair) {
            document.insertString(openTagCloseStart + 2, "{{ /$name }}")
        }
        context.editor.caretModel.moveToOffset(paramCaret)
        context.commitDocument()

        // Arm the param-entry session. `{{` for this statement is the last "{{" at/before the name.
        val openTagStart = text.lastIndexOf("{{", nameEnd)
        if (openTagStart >= 0) {
            val startMarker = document.createRangeMarker(openTagStart, openTagStart + 2)
            val endMarker = document.createRangeMarker(openTagCloseStart, openTagCloseStart + 2)
            startMarker.isGreedyToRight = false
            endMarker.isGreedyToLeft = false
            AntlersParamSession.install(
                context.editor,
                AntlersParamSession(startMarker, endMarker, isPair),
            )
        }
    }
}
```

- [ ] **Step 4: Run the updated insert tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersTagInsertTest"`
Expected: PASS (4 tests). `{{ yield  }}`, `{{ partial  }}`, and the two pair cases all hold.

- [ ] **Step 5: Run the full suite gate (the insert change touches a shared handler)**

Run: `./gradlew --rerun-tasks test`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: the grep prints nothing. If any other test (e.g. `AntlersCompletionTest`, `AntlersShorthandInsertTest`) now asserts the old single-tag `{{ name }}` form, update its expectation to `{{ name  }}` and re-run. (Search first: `grep -rn '{{ yield }}\|{{ partial }}\|partial }}' src/test` and reconcile.)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt
git commit -m "$(cat <<'EOF'
feat: arm param-entry session on tag completion; single tags start in a slot

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Integration tests for the full Tab flow

**Files:**
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamTabTest.kt` (add T1–T5 to the file created in Task 2)

All offsets/strings below were hand-traced against the Task 2/3 code. The session must survive between `finishLookup`, `type`, and `tab` (it lives on the editor's user data).

- [ ] **Step 1: Add the pair, single, and click-away tests**

Append these methods inside `AntlersParamTabTest` (before the closing brace):

```kotlin
    fun testPairTabOpensNewSlot() {
        complete("{{ collection<caret> }}", "collection")
        // {{ collection  }}{{ /collection }}, caret centred.
        myFixture.type("from=\"blog\"")
        // {{ collection from="blog" }}{{ /collection }}, caret after the value, one trailing space.
        tab()
        assertEquals(
            "{{ collection from=\"blog\"  }}{{ /collection }}",
            myFixture.file.text,
        )
        assertEquals("{{ collection from=\"blog\" ".length, myFixture.caretOffset)
    }

    fun testPairEmptySlotJumpsIntoBlock() {
        complete("{{ collection<caret> }}", "collection")
        myFixture.type("from=\"blog\"")
        tab()                  // opens a fresh empty slot
        tab()                  // empty slot → collapse + jump into the block
        assertEquals(
            "{{ collection from=\"blog\" }}{{ /collection }}",
            myFixture.file.text,
        )
        assertEquals("{{ collection from=\"blog\" }}".length, myFixture.caretOffset)
    }

    fun testTerminalTabEndsSession() {
        complete("{{ collection<caret> }}", "collection")
        myFixture.type("from=\"blog\"")
        tab()                  // new slot
        tab()                  // into block, reachedTerminal = true
        assertNotNull(AntlersParamSession.of(myFixture.editor))
        tab()                  // terminal → end session, normal indent
        assertNull(AntlersParamSession.of(myFixture.editor))
    }

    fun testSingleTagFlow() {
        complete("{{ partial<caret> }}", "partial")
        // {{ partial  }}, caret centred.
        myFixture.type("foo=\"x\"")
        tab()                  // new slot
        tab()                  // empty slot → terminal is just after "}}"
        assertEquals("{{ partial foo=\"x\" }}", myFixture.file.text)
        assertEquals("{{ partial foo=\"x\" }}".length, myFixture.caretOffset)
    }

    fun testClickAwayFallsBackToNormalTab() {
        complete("{{ collection<caret> }}", "collection")
        assertNotNull(AntlersParamSession.of(myFixture.editor))
        myFixture.editor.caretModel.moveToOffset(0)   // click away
        tab()
        assertNull("session cleared when caret left the tag", AntlersParamSession.of(myFixture.editor))
        assertTrue(
            "the tag itself is untouched",
            myFixture.file.text.contains("{{ collection  }}{{ /collection }}"),
        )
    }
```

- [ ] **Step 2: Run the param-tab tests**

Run: `./gradlew --rerun-tasks test --tests "*AntlersParamTabTest"`
Expected: PASS (6 tests total: the Task 2 delegation test + these 5).

If `testPairTabOpensNewSlot` or `testPairEmptySlotJumpsIntoBlock` fails on the exact string, print the actual with a temporary `println(myFixture.file.text)` and reconcile spaces — the intended states are `{{ collection from="blog"  }}` (two spaces) after one Tab and `{{ collection from="blog" }}` (one space) after the empty-slot Tab. Do not change behavior to match a wrong expectation; verify against the slot rule in the spec.

- [ ] **Step 3: Full suite gate**

Run: `./gradlew --rerun-tasks test`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: prints nothing.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamTabTest.kt
git commit -m "$(cat <<'EOF'
test: cover repeating param tab-stop flow end-to-end

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (completed during planning)

- **Spec coverage:** session holder (Task 1) ✓; Tab handler + slot rule + lazy validation + terminal/reachedTerminal (Task 2) ✓; arming + single-tag slot tweak (Task 3) ✓; all six spec tests T1–T6 mapped (T6 in Task 2, T1–T5 in Task 4) ✓; registration (Task 2 Step 4) ✓.
- **Type consistency:** `AntlersParamSession(openTagStartMarker, openTagEndMarker, isPair)`, `terminalOffset()`, `isValidFor()`, `reachedTerminal`, `of/install/clear` are referenced identically across the handler, the insert handler, and the tests.
- **No placeholders:** every code step shows complete code; every run step shows the exact command and expected result.
- **Known trace assumptions:** the two-space/one-space expected strings in Task 4 were derived from the Task 2/3 code; Step 2 of Task 4 includes a reconciliation note that forbids "fixing" them by weakening behavior.
```
