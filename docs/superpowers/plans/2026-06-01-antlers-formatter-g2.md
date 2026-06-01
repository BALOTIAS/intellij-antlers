# Antlers Formatter G2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On "Reformat Code", normalize spacing inside Antlers `{{ }}` regions — one space against the `{{`/`}}` delimiters and around each `|` pipe — via a safe, token-based `PostFormatProcessor`.

**Architecture:** A global `PostFormatProcessor` fetches the Antlers PSI from the file's view provider, collects whitespace-gap edit sites at `T_LDOUBLE`/`T_RDOUBLE`/`T_PIPE` boundaries within the reformat range, and applies them end-to-start to the document. Token-based, so strings are safe; runs after the HTML formatter and never fights it.

**Tech Stack:** Kotlin, IntelliJ Platform (`PostFormatProcessor`), JUnit `BasePlatformTestCase` + `CodeStyleManager.reformatText`.

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-formatter-g2-design.md`

**TDD note:** `./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`. Full suite is currently **177/177 green**. Keep it green.

---

## Task 1: Spacing post-format processor

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingPostFormatProcessor.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingFormatterTest.kt` (new)

- [ ] **Step 1: Write the failing formatter tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingFormatterTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.formatter

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersSpacingFormatterTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
        }
        return file.text
    }

    fun testEdgeSpacing() {
        assertEquals("{{ x }}", reformat("{{x}}"))
        assertEquals("{{ x }}", reformat("{{  x  }}"))
        assertEquals("{{ x }}", reformat("{{ x }}"))   // idempotent
    }

    fun testSeparatorsUntouched() {
        assertEquals("{{ collection:blog }}", reformat("{{collection:blog}}"))
        assertEquals("{{ author.name }}", reformat("{{ author.name }}"))
    }

    fun testPipeSpacing() {
        assertEquals("{{ x | upper }}", reformat("{{ x|upper }}"))
        assertEquals("{{ x | a | b }}", reformat("{{ x|a|b }}"))
        assertEquals("{{ x | upper }}", reformat("{{ x | upper }}"))  // idempotent
    }

    fun testStringSafety() {
        assertEquals("{{ x | replace('a|b', 'c') }}", reformat("{{ x | replace('a|b', 'c') }}"))
    }

    fun testParamsUntouched() {
        assertEquals("{{ partial:src=\"blog/card\" }}", reformat("{{ partial:src=\"blog/card\" }}"))
    }

    fun testMultipleRegions() {
        assertEquals("{{ x }} {{ y | z }}", reformat("{{x}} {{ y|z }}"))
    }

    fun testNonExprUntouched() {
        assertEquals("{{# comment #}}", reformat("{{# comment #}}"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.formatter.AntlersSpacingFormatterTest" --no-configuration-cache`
Expected: FAIL — no processor registered, so `{{x}}` stays `{{x}}`.

- [ ] **Step 3: Create the processor**

Create `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingPostFormatProcessor.kt`:

```kotlin
package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiTreeUtil

/**
 * Normalizes spacing inside Antlers {{ }} regions after Reformat Code: exactly one space against the
 * delimiters and around each modifier pipe. Token-based (a `|` inside a string is T_STRING, never
 * T_PIPE, so strings are safe). Runs after the HTML formatter and edits only inside {{ }} spans.
 * Never throws.
 */
class AntlersSpacingPostFormatProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat
        val document = source.viewProvider.document ?: return rangeToReformat

        // All leaves of the Antlers tree, in document order.
        val leaves = mutableListOf<PsiElement>()
        var leaf: PsiElement? = PsiTreeUtil.getDeepestFirst(antlers)
        while (leaf != null) {
            leaves.add(leaf)
            leaf = PsiTreeUtil.nextLeaf(leaf)
        }

        fun isWs(e: PsiElement?): Boolean =
            e != null && (e is PsiWhiteSpace || e.node.elementType == AntlersTypes.T_WS)

        fun nextSignificant(i: Int): PsiElement? {
            var j = i + 1
            while (j < leaves.size && isWs(leaves[j])) j++
            return leaves.getOrNull(j)
        }
        fun prevSignificant(i: Int): PsiElement? {
            var j = i - 1
            while (j >= 0 && isWs(leaves[j])) j--
            return leaves.getOrNull(j)
        }

        // (range to replace, replacement) — an insertion is a zero-length range.
        val edits = mutableListOf<Pair<TextRange, String>>()

        fun normalizeGap(aEnd: Int, bStart: Int) {
            if (bStart < aEnd) return
            val gap = TextRange(aEnd, bStart)
            if (gap.startOffset < rangeToReformat.startOffset || gap.endOffset > rangeToReformat.endOffset) return
            val current = document.getText(gap)
            if (current == " ") return                 // already correct (idempotent)
            if (current.isNotEmpty() && current.isNotBlank()) return  // non-whitespace in gap → skip (defensive)
            edits.add(gap to " ")
        }

        for ((i, e) in leaves.withIndex()) {
            when (e.node.elementType) {
                AntlersTypes.T_LDOUBLE -> {
                    val nxt = nextSignificant(i) ?: continue
                    if (nxt.node.elementType == AntlersTypes.T_RDOUBLE) continue   // empty {{ }}
                    normalizeGap(e.textRange.endOffset, nxt.textRange.startOffset)
                }
                AntlersTypes.T_RDOUBLE -> {
                    val prv = prevSignificant(i) ?: continue
                    if (prv.node.elementType == AntlersTypes.T_LDOUBLE) continue   // empty {{ }}
                    normalizeGap(prv.textRange.endOffset, e.textRange.startOffset)
                }
                AntlersTypes.T_PIPE -> {
                    prevSignificant(i)?.let { normalizeGap(it.textRange.endOffset, e.textRange.startOffset) }
                    nextSignificant(i)?.let { normalizeGap(e.textRange.endOffset, it.textRange.startOffset) }
                }
            }
        }

        var delta = 0
        for ((range, replacement) in edits.sortedByDescending { it.first.startOffset }) {
            document.replaceString(range.startOffset, range.endOffset, replacement)
            delta += replacement.length - range.length
        }
        return TextRange(rangeToReformat.startOffset, rangeToReformat.endOffset + delta)
    }
}
```

- [ ] **Step 4: Register the processor**

Edit `src/main/resources/META-INF/plugin.xml` — add after the `lang.psiStructureViewFactory` line (inside `<extensions>`):

```xml
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersSpacingPostFormatProcessor"/>
```

- [ ] **Step 5: Run the formatter tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.formatter.AntlersSpacingFormatterTest" --no-configuration-cache`
Expected: PASS (7 tests). If `testMultipleRegions` or `testNonExprUntouched` shifts unexpectedly, the HTML
formatter may have touched the outer text — confirm by printing `reformat(...)`; the Antlers regions
should still be correctly spaced and the assertions only cover Antlers-adjacent text.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingPostFormatProcessor.kt src/main/resources/META-INF/plugin.xml src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingFormatterTest.kt
git commit -m "Add post-format processor normalizing Antlers in-delimiter spacing"
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
Expected: **~184** (177 prior + 7 new). Exact count may differ; **0 failures** is the gate.

- [ ] **Step 3: Verify production compilation**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

---

## Notes for the implementer

- The processor is **global** (`<postFormatProcessor implementation="…">`, not language-scoped). It fetches
  the Antlers PSI via `source.viewProvider.getPsi(AntlersLanguage.INSTANCE)`, so it works whether the
  formatter passes the HTML or the Antlers file; non-Antlers files → null → range returned unchanged.
- All edit sites are collected (as document offsets, reading the pre-edit document) BEFORE any edit is
  applied; edits are then applied **end-to-start** so offsets stay valid. The stale PSI after editing is
  fine — we only use offsets.
- Token boundaries make strings safe: a `|` inside `'a|b'` is part of a `T_STRING` leaf, never a `T_PIPE`
  anchor, so it's never touched.
- Only edges (`{{`/`}}`) and `|` are normalized; `:` / `.` / `=` / operators are deliberately left alone.
- If `testMultipleRegions`/`testNonExprUntouched` prove flaky due to the HTML formatter reflowing outer
  text, adjust the fixture to text the HTML formatter leaves verbatim (e.g. no surrounding tags) while
  keeping the Antlers-spacing assertions — the processor behavior is what's under test.
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
```
