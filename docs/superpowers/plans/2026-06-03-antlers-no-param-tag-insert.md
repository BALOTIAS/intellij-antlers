# Antlers No-Param Tag Insert Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When completing a tag with no parameters, drop the caret into the block (pair) or after the tag (single) instead of an empty param slot — no pointless double-Tab.

**Architecture:** Add a `hasParams` flag to `AntlersTagInsertHandler` (passed `tag.parameters.isNotEmpty()` from the provider). When false, build a single-space `{{ name }}`, place the caret past the opening `}}`, append the closer for pairs, and arm NO session. When true, the existing centred-slot + `AntlersParamSession` path is unchanged.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, `BasePlatformTestCase`, Gradle.

**Reference spec:** `docs/superpowers/specs/2026-06-03-antlers-no-param-tag-insert-design.md`

**Branch:** `antlers-no-param-tag-insert` (create from `main` before Task 1).

**Test gate (after every test run):** `./gradlew --rerun-tasks test` then
`grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` — must print nothing. `--rerun-tasks` is required (Gradle caches).

---

### Task 1: No-param insert branch + provider signal + tests

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt`

- [ ] **Step 1: Update the tests (failing first)**

In `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt`:

(1a) Add this import after the existing `import com.intellij.testFramework.fixtures.BasePlatformTestCase`:
```kotlin
import com.github.balotias.intellijantlers.editor.AntlersParamSession
```

(1b) Replace the method `testSingleTagStartsInParamSlot` (yield) with:
```kotlin
    fun testNoParamSingleCaretAfterTag() {
        // `yield` has no params → caret lands after the tag, no slot, no session.
        completeTag("{{ yield<caret> }}", "yield")
        assertEquals("{{ yield }}", myFixture.file.text)
        assertEquals("{{ yield }}".length, myFixture.caretOffset)
        assertNull(AntlersParamSession.of(myFixture.editor))
    }
```

(1c) Add these three methods to the class:
```kotlin
    fun testNoParamPairCaretInBlock() {
        // `nocache` has no params → caret lands in the block, no slot, no session.
        completeTag("{{ nocache<caret> }}", "nocache")
        assertEquals("{{ nocache }}{{ /nocache }}", myFixture.file.text)
        assertEquals("{{ nocache }}".length, myFixture.caretOffset)
        assertNull(AntlersParamSession.of(myFixture.editor))
    }

    fun testNoParamSingleNoSession() {
        completeTag("{{ svg<caret> }}", "svg")
        assertEquals("{{ svg }}", myFixture.file.text)
        assertNull(AntlersParamSession.of(myFixture.editor))
    }

    fun testParamTagStillArmsSession() {
        completeTag("{{ collection<caret> }}", "collection")
        assertEquals("{{ collection  }}{{ /collection }}", myFixture.file.text)
        assertEquals("{{ collection ".length, myFixture.caretOffset)
        assertNotNull(AntlersParamSession.of(myFixture.editor))
    }
```

(Leave `testCollectionDefaultIsParamSlot`, `testPartialStartsInParamSlot` (partial has params → centred slot), and `testNonHandleTagKeepsParamSlot` unchanged.)

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersTagInsertTest"`
Expected: FAIL — the no-param tests expect `{{ yield }}` / `{{ nocache }}{{ /nocache }}` with no session, but the handler still builds the centred slot and arms a session.

- [ ] **Step 3: Add the `hasParams` no-param branch to the handler**

Replace the entire contents of `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt` with:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Inserts an Antlers tag. A tag that takes parameters gets a centred param slot `{{ name <caret> }}`
 * (plus `{{ /name }}` for pairs) and arms the repeating-param [AntlersParamSession]. A tag with NO
 * parameters skips the slot/session: the caret lands in the block (pair) or right after the tag
 * (single), since there is nothing to type inside the tag.
 */
class AntlersTagInsertHandler(
    private val isPair: Boolean,
    private val hasParams: Boolean,
) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val name = item.lookupString
        val nameEnd = context.tailOffset
        val text = document.charsSequence.toString()

        val nextClose = text.indexOf("}}", nameEnd)
        val nextOpen = text.indexOf("{{", nameEnd)
        val alreadyClosed = nextClose >= 0 && (nextOpen < 0 || nextClose < nextOpen)

        if (!hasParams) {
            // No params → no slot. Single-space the opener; caret past "}}" (in the block for a pair).
            val closeStart: Int
            if (alreadyClosed) {
                val gap = text.substring(nameEnd, nextClose)
                if (gap.isBlank()) {
                    if (gap != " ") document.replaceString(nameEnd, nextClose, " ")
                    closeStart = nameEnd + 1
                } else {
                    closeStart = nextClose            // existing params (rare) → don't mangle text
                }
            } else {
                val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
                val ins = if (needsSpace) " }}" else "}}"
                document.insertString(nameEnd, ins)
                closeStart = nameEnd + ins.length - 2
            }
            val afterClose = closeStart + 2
            if (isPair) document.insertString(afterClose, "{{ /$name }}")
            context.editor.caretModel.moveToOffset(afterClose)
            context.commitDocument()
            return
        }

        // Param tag: centred slot, capture where the caret goes and where "}}" starts.
        val paramCaret: Int
        val openTagCloseStart: Int
        if (alreadyClosed) {
            val gap = text.substring(nameEnd, nextClose)
            if (gap.isBlank()) {
                document.replaceString(nameEnd, nextClose, "  ")
                paramCaret = nameEnd + 1
                openTagCloseStart = nameEnd + 2
            } else {
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

- [ ] **Step 4: Pass the signal from the provider**

In `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`, find the `TAG_NAME` arm's tag loop line:
```kotlin
                            .withInsertHandler(AntlersTagInsertHandler(tag.isPair))
```
and replace it with:
```kotlin
                            .withInsertHandler(AntlersTagInsertHandler(tag.isPair, tag.parameters.isNotEmpty()))
```

- [ ] **Step 5: Run the insert tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersTagInsertTest"`
Expected: PASS. `{{ yield }}` / `{{ nocache }}{{ /nocache }}` land the caret past `}}` with no session; `collection` keeps the centred slot + session.

- [ ] **Step 6: Full suite gate + reconcile other no-param-tag tests**

First search for other tests completing a no-param tag that may assert the old centred slot:
```bash
grep -rn 'yield\|nocache\|markdown\|obfuscate\|"svg"\|"trans"\|"mix"\|"redirect"\|section' src/test/kotlin
```
For any test that completes such a tag and asserts `{{ name  }}` (two spaces) or a centred caret, update it to the new `{{ name }}` (caret in block / after tag) behavior. Param tags (`collection`, `cache`, `partial`, `nav`, …) must be untouched. Report any test changed and why.

Then run the full suite:
```bash
./gradlew --rerun-tasks test
grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml
```
Expected: the grep prints nothing.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt
# include any other test reconciled in Step 6
git commit -m "$(cat <<'EOF'
feat: no-param tags drop the caret in the block / after the tag, no slot

A completed tag with no catalog parameters (nocache, yield, markdown, …) no
longer lands the caret in an empty param slot — pairs put it in the block,
singles after the tag, and no param session is armed. Param tags keep the
centred slot + repeating-param session.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (completed during planning)

- **Spec coverage:** `hasParams` arg + provider signal (`tag.parameters.isNotEmpty()`) ✓; no-param pair → block caret + closer, no session ✓; no-param single → caret after `}}`, no session ✓; param path unchanged (centred slot + session) ✓; tests: yield changed, nocache/svg/collection added, partial/cache/collection-slot kept ✓; reconcile step for other no-param tests ✓.
- **Type consistency:** `AntlersTagInsertHandler(isPair, hasParams)` constructed identically in the provider; `AntlersParamSession.of/install` referenced as elsewhere.
- **Trace checks:** `{{ nocache }}` (blank single-space gap) → `closeStart = nameEnd+1`, `afterClose = nameEnd+3`, pair closer appended, caret = `"{{ nocache }}".length` ✓. `{{ yield }}` single → caret = `"{{ yield }}".length` ✓. `collection` (hasParams) → centred slot unchanged ✓.
- **No placeholders:** every code step is complete; every run step has its command + expected result; Step 6 forbids weakening param-tag behavior while reconciling.
```
