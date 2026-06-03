# Antlers Multi-line Tag Parameter Indentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On Reformat Code, indent a multi-line Antlers `{{ … }}` tag's parameter lines one level under the `{{` line and keep `}}` on its own line.

**Architecture:** A new `PostFormatProcessor` overwrites the leading whitespace of each interior line of a multi-line `{{ … }}` region (params → opener-indent + one unit; `}}` line → opener-indent), plus a one-line fix to the existing spacing processor so it stops collapsing newline gaps (which was yanking `}}` up). Both are text/token-based and stay out of the formatting model.

**Tech Stack:** Kotlin, IntelliJ Platform (`PostFormatProcessor`, `Document`, `CodeStyle.getIndentOptions`, `AntlersStatement` PSI), JUnit / `BasePlatformTestCase` + `CodeStyleManager.reformatText`.

**Spec:** `docs/superpowers/specs/2026-06-03-antlers-multiline-param-indent-design.md`
**Branch:** `antlers-multiline-param-indent` (already created from `main`; spec already committed).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty). `--rerun-tasks` is REQUIRED (Gradle caches).

## File Structure

- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingPostFormatProcessor.kt` — one guard line in `normalizeGap` (preserve newline gaps).
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt` — the new processor.
- **Modify** `src/main/resources/META-INF/plugin.xml` — register the new processor.
- Tests: `formatter/AntlersMultilineFormatTest.kt`.

> **Test gotchas (project memory):** (1) `reformatText` edits the DOCUMENT directly — the test MUST
> `PsiDocumentManager.commitAllDocuments()` before reading `file.text`. (2) Kotlin evaluates the *expected*
> argument of `assertEquals` first, so calling `unit()`/`myFixture.file` inside the expected string BEFORE
> `reformat(...)` has configured the fixture throws NPE — always `val out = reformat(text)` first, then
> compute `unit()`, then assert.

---

### Task 1: Spacing processor preserves newline gaps

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingPostFormatProcessor.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineFormatTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineFormatTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMultilineFormatTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()  // sync PSI with the doc edits
        }
        return file.text
    }

    /** The active indent unit — call AFTER reformat() has configured the fixture (NPE otherwise). */
    private fun unit(): String {
        val opts = CodeStyle.getIndentOptions(myFixture.file)
        return if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
    }

    fun testMultilineCloserNotPulledUp() {
        val out = reformat("{{ collection:blog\nlimit=\"3\"\n}}")
        assertTrue("closer stays on its own line, got:\n$out", out.trimEnd().endsWith("\n}}"))
        assertFalse("closer not joined to the last param, got:\n$out", out.contains("\"3\" }}"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersMultilineFormatTest"`
Expected: FAIL — today the spacing processor pulls `}}` up, so the output contains `"3" }}` (the
`assertFalse` fails) and does not end with `\n}}`.

- [ ] **Step 3: Add the newline guard**

In `AntlersSpacingPostFormatProcessor.kt`, the `normalizeGap` function reads:
```kotlin
            val current = document.getText(gap)
            if (current == " ") return                 // already correct (idempotent)
```
Insert a guard between those two lines so it becomes:
```kotlin
            val current = document.getText(gap)
            if (current.contains('\n')) return          // preserve intentional line breaks (multi-line tags)
            if (current == " ") return                 // already correct (idempotent)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersMultilineFormatTest"`
Expected: PASS (1 test). The `}}` now stays on its own line (params are still at column 0 — Task 2 indents
them).

- [ ] **Step 5: Run the existing spacing suite to confirm no regression**

Run: `./gradlew --rerun-tasks test --tests "*AntlersSpacingFormatterTest"`
Expected: PASS (all existing single-line spacing cases — they contain no newlines, so the guard never
fires).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersSpacingPostFormatProcessor.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineFormatTest.kt
git commit -m "$(cat <<'EOF'
fix(formatter): stop collapsing newline gaps in {{ }} (keeps `}}` on its line)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Multi-line tag indent processor

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineFormatTest.kt` (add cases)

- [ ] **Step 1: Add the failing tests**

Append these methods to `AntlersMultilineFormatTest` (inside the class):
```kotlin
    fun testCanonicalMultiline() {
        val out = reformat("{{ collection:blog\nlimit=\"3\"\nas=\"posts\"\n}}")
        val u = unit()
        assertEquals("{{ collection:blog\n${u}limit=\"3\"\n${u}as=\"posts\"\n}}", out)
    }

    fun testOverwritesExistingIndent() {
        val out = reformat("{{ collection:blog\n        limit=\"3\"\n}}")   // 8 leading spaces
        val u = unit()
        assertEquals("{{ collection:blog\n${u}limit=\"3\"\n}}", out)
    }

    fun testIdempotent() {
        val once = reformat("{{ collection:blog\nlimit=\"3\"\n}}")
        val twice = reformat(once)
        assertEquals(once, twice)
    }

    fun testMultilineInsideElement() {
        val out = reformat("<div>\n{{ collection:blog\nlimit=\"3\"\n}}\n</div>")
        val u = unit()
        assertEquals("<div>\n$u{{ collection:blog\n$u${u}limit=\"3\"\n$u}}\n</div>", out)
    }

    fun testSingleLineUntouched() {
        assertEquals("{{ collection:blog limit=\"3\" }}", reformat("{{ collection:blog limit=\"3\" }}"))
    }

    fun testCloserInlineWithLastParam() {
        val out = reformat("{{ collection:blog\nlimit=\"3\" }}")
        val u = unit()
        assertEquals("{{ collection:blog\n${u}limit=\"3\" }}", out)
    }

    fun testMultilineStringValueNotReindented() {
        // A param string value that spans lines must keep its inner content verbatim.
        val out = reformat("{{ partial:src=\"a\nb\" }}")
        assertTrue("multi-line string content preserved, got:\n$out", out.contains("\"a\nb\""))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersMultilineFormatTest"`
Expected: FAIL — `testCanonicalMultiline` / `testOverwritesExistingIndent` / `testMultilineInsideElement` /
`testCloserInlineWithLastParam` fail (params are still at column 0 / the opener indent — no indent
processor yet). `testSingleLineUntouched`, `testIdempotent`, `testMultilineStringValueNotReindented`, and
the Task-1 test may already pass.

- [ ] **Step 3: Write the processor**

Create `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt`:
```kotlin
package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiTreeUtil

/**
 * Indents the parameter lines of a MULTI-LINE Antlers `{{ … }}` region one level under the `{{` line and
 * leaves `}}` on its own line at the opener indent. Text-based: it sets each interior line's indentation
 * absolutely, so it is independent of the HTML formatter's opaque-block layout, and idempotent. Single-
 * line tags, comments, PHP, noparse and HTML are untouched. In-`{{ }}` spacing is owned by
 * [AntlersSpacingPostFormatProcessor]. Never throws.
 */
class AntlersMultilineTagIndentProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat
        val document = source.viewProvider.document ?: return rangeToReformat

        val opts = CodeStyle.getIndentOptions(source)
        val unit = if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)

        // (indent range to replace, replacement); applied end-to-start so offsets stay valid.
        val edits = mutableListOf<Pair<TextRange, String>>()

        for (stmt in PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)) {
            val range = stmt.textRange
            if (range.startOffset < rangeToReformat.startOffset || range.endOffset > rangeToReformat.endOffset) continue

            val openerLine = document.getLineNumber(range.startOffset)
            val closerLine = document.getLineNumber(range.endOffset - 1)
            if (closerLine == openerLine) continue                       // single-line tag → skip

            val openerIndent = leadingWhitespace(document, openerLine)
            val contIndent = openerIndent + unit
            val rdoubleStart = range.endOffset - 2                       // start of the `}}` token

            // Antlers strings may contain newlines; never reindent a line that begins inside one.
            val stringSpans = PsiTreeUtil.collectElements(stmt) {
                it.node?.elementType == AntlersTypes.T_STRING
            }.map { it.textRange }

            for (line in (openerLine + 1)..closerLine) {
                val lineStart = document.getLineStartOffset(line)
                val lineEnd = document.getLineEndOffset(line)
                val lineText = document.getText(TextRange(lineStart, lineEnd))
                val firstNonWs = lineText.indexOfFirst { it != ' ' && it != '\t' }
                val contentOffset = if (firstNonWs < 0) lineEnd else lineStart + firstNonWs

                if (stringSpans.any { it.startOffset < lineStart && lineStart < it.endOffset }) continue

                val target = when {
                    firstNonWs < 0 -> ""                                  // blank line → strip
                    line == closerLine && contentOffset == rdoubleStart -> openerIndent  // `}}` on its own line
                    else -> contIndent                                    // param / content line
                }
                val indentRange = TextRange(lineStart, contentOffset)
                if (document.getText(indentRange) != target) edits.add(indentRange to target)
            }
        }

        var delta = 0
        for ((indentRange, replacement) in edits.distinct().sortedByDescending { it.startOffset }) {
            document.replaceString(indentRange.startOffset, indentRange.endOffset, replacement)
            delta += replacement.length - indentRange.length
        }
        return TextRange(rangeToReformat.startOffset, rangeToReformat.endOffset + delta)
    }

    private fun leadingWhitespace(document: Document, line: Int): String {
        val start = document.getLineStartOffset(line)
        val text = document.getText(TextRange(start, document.getLineEndOffset(line)))
        val n = text.indexOfFirst { it != ' ' && it != '\t' }
        return if (n < 0) text else text.substring(0, n)
    }
}
```

- [ ] **Step 4: Register the processor**

In `src/main/resources/META-INF/plugin.xml`, immediately after the line:
```xml
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersSpacingPostFormatProcessor"/>
```
add:
```xml
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersMultilineTagIndentProcessor"/>
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersMultilineFormatTest"`
Expected: PASS (all 8 tests). If `testMultilineInsideElement` fails on the opener indent, print the raw
output (`println(out.replace(" ", "·"))`) and check whether the HTML formatter put `{{` at one unit under
`<div>` (it should — the processor reads that as `openerIndent`); do NOT change the indent target to make
it pass without understanding the difference. If `testMultilineStringValueNotReindented` fails because the
*HTML formatter itself* (not this processor) mangled the multi-line string, report it as
DONE_WITH_CONCERNS (it is a pre-existing formatter limitation outside this processor's control), do not
hack the processor.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineFormatTest.kt
git commit -m "$(cat <<'EOF'
feat(formatter): indent multi-line tag params one level under `{{`

A new post-format processor overwrites each interior line of a multi-line
{{ … }} region: params at opener-indent + one unit, `}}` on its own line at
the opener indent. Absolute-set so it is robust and idempotent; skips lines
inside multi-line string values.

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

If `AntlersHtmlFormatTest` or `AntlersSpacingFormatterTest` changed: this feature only adds interior
indentation to multi-line `{{ }}` and stops newline-collapsing — it must not change single-line or
HTML-structure formatting. Investigate before proceeding; do not edit other tests to go green.

## Self-Review

- **Spec coverage:** spacing newline-guard (§Components 1) → Task 1; new processor with absolute
  indent / opener+unit / `}}`-on-own-line / string guard / blank-line strip / range guard
  (§Components 2) → Task 2 Step 3; registration (§Components 3) → Task 2 Step 4; all testing rows
  (canonical, indent-agnostic, idempotent, inside-element, single-line, `}}`-inline, string-safety,
  full gate) → Tasks 1-3. No gaps.
- **Placeholder scan:** none — full processor code + full test code provided; no TBD / "handle edge
  cases".
- **Type consistency:** `processText(PsiFile, TextRange, CodeStyleSettings): TextRange` matches the
  `PostFormatProcessor` contract the spacing processor already implements; `CodeStyle.getIndentOptions`
  + `USE_TAB_CHARACTER`/`INDENT_SIZE` match the Enter-handler usage; `AntlersStatement` /
  `AntlersTypes.T_STRING` are existing PSI symbols; the test `unit()` helper computes the same unit the
  processor uses, so assertions track the active code style.
