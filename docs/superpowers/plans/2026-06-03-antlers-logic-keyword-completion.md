# Antlers Logic-Keyword Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Offer Antlers logic keywords (`if`, `unless`, `else`, `elseif`, `endif`, `endunless`) as in-brace completions, context-aware so the followers appear only inside the matching open block.

**Architecture:** One new file holds the keyword model + a session-free insert handler. The existing `TAG_NAME` provider arm gains a small context-aware block that always offers `if`/`unless` and offers the followers based on `AntlersNestingTreeBuilder.nearestUnclosedAt`. No grammar/catalog change — balance/folding already understand the inserted constructs.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`CompletionProvider`, `InsertHandler`, `LookupElementBuilder`), `BasePlatformTestCase`, Gradle.

**Reference spec:** `docs/superpowers/specs/2026-06-03-antlers-logic-keyword-completion-design.md`

**Branch:** `antlers-logic-keyword-completion` (create from `main` before Task 1).

**Test gate (after every test run):** `./gradlew --rerun-tasks test` then
`grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` — must print nothing. `--rerun-tasks` is required (Gradle caches otherwise).

---

### Task 1: Keyword model + insert handler

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersLogicKeywords.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/** How a logic keyword is inserted. */
enum class LogicKind { OPENER, MID, PLAIN }

/** An Antlers logic keyword and how to insert it. [closer] is the closing keyword for OPENERs. */
data class LogicKeyword(val name: String, val kind: LogicKind, val closer: String? = null)

/** Block openers — always offered in a `{{ }}` tag-name position. */
val LOGIC_OPENERS = listOf(
    LogicKeyword("if", LogicKind.OPENER, closer = "if"),
    LogicKeyword("unless", LogicKind.OPENER, closer = "unless"),
)

/** Offered only when the innermost open condition is `if`. */
val LOGIC_IF_FOLLOWERS = listOf(
    LogicKeyword("else", LogicKind.PLAIN),
    LogicKeyword("elseif", LogicKind.MID),
    LogicKeyword("endif", LogicKind.PLAIN),
)

/** Offered only when the innermost open condition is `unless` (Antlers `unless` has no `elseif`). */
val LOGIC_UNLESS_FOLLOWERS = listOf(
    LogicKeyword("else", LogicKind.PLAIN),
    LogicKeyword("endunless", LogicKind.PLAIN),
)

/**
 * Inserts a logic keyword. The platform has already placed the bare keyword with the caret right after
 * it. OPENER/MID build a centred condition slot `{{ kw  }}` (caret centred, a space each side) — OPENER
 * also appends `{{ /closer }}`; PLAIN keeps the single-space `{{ kw }}` and drops the caret just after
 * the closing `}}`. No `AntlersParamSession` is armed (a condition is one expression, not params).
 */
class AntlersKeywordInsertHandler(private val kw: LogicKeyword) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val nameEnd = context.tailOffset
        val text = document.charsSequence.toString()

        val nextClose = text.indexOf("}}", nameEnd)
        val nextOpen = text.indexOf("{{", nameEnd)
        val alreadyClosed = nextClose >= 0 && (nextOpen < 0 || nextClose < nextOpen)

        if (kw.kind == LogicKind.PLAIN) {
            val caret: Int
            if (alreadyClosed) {
                if (text.substring(nameEnd, nextClose) != " ") document.replaceString(nameEnd, nextClose, " ")
                caret = nameEnd + 1 + 2                       // past the single space + "}}"
            } else {
                val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
                val ins = if (needsSpace) " }}" else "}}"
                document.insertString(nameEnd, ins)
                caret = nameEnd + ins.length
            }
            context.editor.caretModel.moveToOffset(caret)
            context.commitDocument()
            return
        }

        // OPENER or MID: centred condition slot (same normalization as AntlersTagInsertHandler).
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

        if (kw.kind == LogicKind.OPENER) {
            document.insertString(openTagCloseStart + 2, "{{ /${kw.closer} }}")
        }
        context.editor.caretModel.moveToOffset(paramCaret)
        context.commitDocument()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersLogicKeywords.kt
git commit -m "$(cat <<'EOF'
feat: add logic-keyword model + session-free insert handler

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Offer the keywords context-aware in the provider

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersLogicKeywordTest.kt` (new)

- [ ] **Step 1: Write the failing offering tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersLogicKeywordTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersLogicKeywordTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testOpenersOffered() {
        assertTrue(lookups("{{ i<caret> }}").contains("if"))
        assertTrue(lookups("{{ un<caret> }}").contains("unless"))
    }

    fun testFollowersSuppressedAtTopLevel() {
        val l = lookups("{{ e<caret> }}")
        assertFalse("else not offered with nothing open", l.contains("else"))
        assertFalse("elseif not offered with nothing open", l.contains("elseif"))
        assertFalse("endif not offered with nothing open", l.contains("endif"))
    }

    fun testFollowersOfferedInsideIf() {
        val l = lookups("{{ if x }}{{ e<caret> }}")
        assertTrue(l.contains("else"))
        assertTrue(l.contains("elseif"))
        assertTrue(l.contains("endif"))
        assertFalse("endunless belongs to unless", l.contains("endunless"))
    }

    fun testFollowersOfferedInsideUnless() {
        val l = lookups("{{ unless x }}{{ e<caret> }}")
        assertTrue(l.contains("else"))
        assertTrue(l.contains("endunless"))
        assertFalse("elseif not valid for unless", l.contains("elseif"))
        assertFalse("endif belongs to if", l.contains("endif"))
    }

    fun testFollowersSuppressedWhenPairTagInnermost() {
        val l = lookups("{{ if x }}{{ collection }}{{ e<caret> }}")
        assertFalse("else suppressed when a pair tag is innermost", l.contains("else"))
        assertFalse("endif suppressed when a pair tag is innermost", l.contains("endif"))
        assertTrue("openers still offered", l.contains("if"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersLogicKeywordTest"`
Expected: FAIL — `testOpenersOffered` (and the inside-if/unless tests) fail because the keywords aren't offered yet. `testFollowersSuppressedAtTopLevel` may pass already (nothing offers them).

- [ ] **Step 3: Wire the keywords into the provider**

In `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`:

First add these imports after the existing `import com.intellij.codeInsight.completion.CompletionResultSet` line:

```kotlin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.intellij.psi.util.PsiTreeUtil
```

Then replace the **start** of the `TAG_NAME` arm. The current code is:

```kotlin
            AntlersCompletionKind.TAG_NAME -> {
                var unclosed: String? = null
                if (info.isClosing) {
                    val stmt = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                        parameters.position, com.github.balotias.intellijantlers.psi.AntlersStatement::class.java)
                    val before = stmt?.textRange?.startOffset ?: parameters.offset
                    unclosed = com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
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
                for (tag in catalog.tags()) {
```

Replace it with (compute `stmtStart` once; add the logic block for the non-closing path):

```kotlin
            AntlersCompletionKind.TAG_NAME -> {
                val file = parameters.position.containingFile
                val stmt = PsiTreeUtil.getParentOfType(parameters.position, AntlersStatement::class.java)
                val stmtStart = stmt?.textRange?.startOffset ?: parameters.offset
                var unclosed: String? = null
                if (info.isClosing) {
                    unclosed = AntlersNestingTreeBuilder.nearestUnclosedAt(file, stmtStart, project)
                    if (unclosed != null) {
                        result.addElement(
                            com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority(
                                LookupElementBuilder.create(unclosed)
                                    .withIcon(AntlersIcons.FILE).withTypeText("Close tag"),
                                Double.MAX_VALUE
                            )
                        )
                    }
                } else {
                    // Logic keywords: openers always; followers scoped to the innermost open condition.
                    LOGIC_OPENERS.forEach { addLogic(result, it) }
                    when (AntlersNestingTreeBuilder.nearestUnclosedAt(file, stmtStart, project)) {
                        "if" -> LOGIC_IF_FOLLOWERS.forEach { addLogic(result, it) }
                        "unless" -> LOGIC_UNLESS_FOLLOWERS.forEach { addLogic(result, it) }
                    }
                }
                for (tag in catalog.tags()) {
```

Then add this private helper method to the class (e.g. right before `private fun offerMembers`):

```kotlin
    private fun addLogic(result: CompletionResultSet, kw: LogicKeyword) {
        result.addElement(
            LookupElementBuilder.create(kw.name)
                .withIcon(AntlersIcons.FILE)
                .withTypeText("Logic")
                .withInsertHandler(AntlersKeywordInsertHandler(kw))
        )
    }
```

- [ ] **Step 4: Run the offering tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersLogicKeywordTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Full suite gate (the provider is shared)**

Run: `./gradlew --rerun-tasks test`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: prints nothing. If a test that asserts the exact completion set at `{{ <caret> }}` now sees extra `if`/`unless`, update that test to expect them (search: `grep -rn 'lookupElementStrings\|assertSameElements\|completeBasic' src/test`). Report any test changed.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersLogicKeywordTest.kt
git commit -m "$(cat <<'EOF'
feat: offer logic keywords in-brace, context-aware via nearestUnclosedAt

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Insert-behavior tests

**Files:**
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersLogicKeywordTest.kt`

These verify the `AntlersKeywordInsertHandler` end-to-end through real completion.

- [ ] **Step 1: Add the insert tests**

Add these imports at the top of `AntlersLogicKeywordTest.kt` (after the existing import):

```kotlin
import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.lookup.Lookup
```

Add these methods inside the class:

```kotlin
    private fun insert(text: String, keyword: String) {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        myFixture.lookup?.let { lk ->
            val item = lk.items.firstOrNull { it.lookupString == keyword } ?: return
            lk.currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }

    private fun docText() = myFixture.editor.document.text

    fun testOpenerInsertsConditionSlotAndCloser() {
        insert("{{ i<caret> }}", "if")
        assertEquals("{{ if  }}{{ /if }}", docText())
        assertEquals("{{ if ".length, myFixture.caretOffset)
        // A condition is not params → no repeating-param session is armed.
        assertNull(AntlersParamSession.of(myFixture.editor))
    }

    fun testElseifInsertsConditionSlotNoCloser() {
        insert("{{ if x }}{{ el<caret> }}", "elseif")
        assertEquals("{{ if x }}{{ elseif  }}", docText())
        assertEquals("{{ if x }}{{ elseif ".length, myFixture.caretOffset)
    }

    fun testEndifInsertsPlainAndCaretAfterBraces() {
        insert("{{ if x }}{{ end<caret> }}", "endif")
        assertEquals("{{ if x }}{{ endif }}", docText())
        assertEquals("{{ if x }}{{ endif }}".length, myFixture.caretOffset)
    }
```

- [ ] **Step 2: Run the test class**

Run: `./gradlew --rerun-tasks test --tests "*AntlersLogicKeywordTest"`
Expected: PASS (8 tests).

If `testOpenerInsertsConditionSlotAndCloser` shows a one-space `{{ if }}` instead of two, the platform may have consumed a space differently — print `docText()` and reconcile, but the intended state is two spaces `{{ if  }}` with the caret centred (do not weaken to match a wrong actual).

- [ ] **Step 3: Full suite gate**

Run: `./gradlew --rerun-tasks test`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: prints nothing.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersLogicKeywordTest.kt
git commit -m "$(cat <<'EOF'
test: cover logic-keyword insertion (opener/mid/plain)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (completed during planning)

- **Spec coverage:** keyword model + session-free handler (Task 1) ✓; context-aware offering via `nearestUnclosedAt` with openers-always / if-followers / unless-followers (Task 2) ✓; all 8 spec tests L1–L8 mapped (L1–L5 Task 2, L6–L8 Task 3) ✓; OPENER centred slot + mirrored closer, MID centred slot, PLAIN caret-past-`}}` ✓; no session ✓.
- **Type consistency:** `LogicKeyword(name, kind, closer)`, `LogicKind.{OPENER,MID,PLAIN}`, `LOGIC_OPENERS/LOGIC_IF_FOLLOWERS/LOGIC_UNLESS_FOLLOWERS`, `AntlersKeywordInsertHandler(kw)`, `addLogic(result, kw)` referenced identically across tasks.
- **No placeholders:** every code step is complete; every run step has its exact command + expected result.
- **Shared-state check:** `stmtStart` is computed once and used by both the closing and non-closing paths; the `when` arms are mutually exclusive so `else` is never added twice.
- **Reconciliation guards:** Task 2 Step 5 and Task 3 Step 2 tell the implementer to reconcile (not weaken) if a shared completion-set test or the two-space opener state surprises them.
```
