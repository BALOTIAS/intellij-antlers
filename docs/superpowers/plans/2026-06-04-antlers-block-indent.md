# Antlers Block Indentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reformat Code indents the body of a paired Antlers tag/condition one level deeper than its opener (`{{ if }}` / `{{ collection }}` bodies, including nested pairs and HTML body lines).

**Architecture:** A new `PostFormatProcessor` (`AntlersBlockIndentProcessor`) runs after the HTML formatter + spacing pass and BEFORE the existing multiline-param processor. It uses the shared `AntlersNestingTreeBuilder` to assign each line an Antlers nesting depth, then sets Antlers/text lines absolutely (`base + depth*unit`) and shifts HTML element lines additively (`currentIndent + depth*unit`). Text/document-based, idempotent, never throws.

**Tech Stack:** Kotlin, IntelliJ `PostFormatProcessor`, `AntlersNestingTreeBuilder`. Build/test: `./gradlew --rerun-tasks test` (`--rerun-tasks` REQUIRED — Gradle caches). Gate: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

---

## File Structure

- `formatter/AntlersBlockIndentProcessor.kt` (create) — the new post-format processor.
- `META-INF/plugin.xml` (modify) — register it BEFORE the multiline processor.
- `formatter/AntlersBlockIndentTest.kt` (create, test) — reformat-based behavior + idempotency tests.

Reference (read, don't change): `formatter/AntlersMultilineTagIndentProcessor.kt` (the pattern this mirrors), `scope/AntlersNestingTreeBuilder.kt` (`build(root, project): NestingTree`, `NestingNode(opener, name, closer?, children)`), `formatter/AntlersMultilineFormatTest.kt` (the `reformat()` harness).

---

### Task 1: The block-indent processor + basic nested-tag indent

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentProcessor.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt`

- [ ] **Step 1: Write the failing test**

Create `AntlersBlockIndentTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersBlockIndentTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    private fun unit(): String {
        val opts = CodeStyle.getIndentOptions(myFixture.file)
        return if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
    }

    fun testIfBodyIndented() {
        val out = reformat("{{ if x }}\n{{ title }}\n{{ /if }}")
        val u = unit()
        assertEquals("{{ if x }}\n${u}{{ title }}\n{{ /if }}", out)
    }

    fun testNestedBlocksCompound() {
        val out = reformat("{{ if a }}\n{{ if b }}\n{{ title }}\n{{ /if }}\n{{ /if }}")
        val u = unit()
        assertEquals(
            "{{ if a }}\n${u}{{ if b }}\n${u}${u}{{ title }}\n${u}{{ /if }}\n{{ /if }}",
            out
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersBlockIndentTest"`
Expected: FAIL — the body is not indented (no processor yet).

- [ ] **Step 3: Implement the processor**

Create `AntlersBlockIndentProcessor.kt`:
```kotlin
package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.github.balotias.intellijantlers.scope.NestingNode
import com.github.balotias.intellijantlers.settings.AntlersFormatterSettings
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiTreeUtil

/**
 * Indents the body of paired Antlers tags/conditions one level per enclosing pair, the level the inline
 * model in [AntlersHtmlFormattingModelBuilder] omits. Antlers `{{ }}` and text lines are set absolutely
 * (`base + depth*unit`) from the stable outermost-opener anchor; HTML element lines are shifted additively
 * (`currentIndent + depth*unit`) on top of the HTML formatter's own re-normalized indent — so the pass is
 * idempotent. Multi-line tag param lines are left to [AntlersMultilineTagIndentProcessor] (runs after).
 * Text-based; never throws. In-`{{ }}` spacing is owned by [AntlersSpacingPostFormatProcessor].
 */
class AntlersBlockIndentProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (!AntlersFormatterSettings.getInstance(source.project).reformatEnabled) return rangeToReformat
        val document = source.viewProvider.document ?: return rangeToReformat
        // Re-sync PSI after the spacing pass edited the document without committing (same guard the
        // multiline processor uses) so statement/string ranges match the current text.
        PsiDocumentManager.getInstance(source.project).commitDocument(document)
        val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat

        val tree = AntlersNestingTreeBuilder.build(antlers, source.project)
        if (tree.roots.isEmpty()) return rangeToReformat               // no paired blocks → nothing to do

        val opts = CodeStyle.getIndentOptions(source)
        val unit = if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
        val lineCount = document.lineCount

        val depth = IntArray(lineCount)
        val touched = BooleanArray(lineCount)                          // a line some node's walk assigned
        val baseOf = arrayOfNulls<String>(lineCount)
        val continuation = BooleanArray(lineCount)                     // line strictly inside a multi-line tag

        for (stmt in PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)) {
            val sLine = document.getLineNumber(stmt.textRange.startOffset)
            val eLine = document.getLineNumber(stmt.textRange.endOffset - 1)
            for (l in (sLine + 1)..eLine) if (l in 0 until lineCount) continuation[l] = true
        }

        fun openerLine(n: NestingNode) = document.getLineNumber(n.opener.textRange.startOffset)

        fun walk(node: NestingNode, d: Int, base: String) {
            val oLine = openerLine(node)
            val cLine = node.closer?.let { document.getLineNumber(it.textRange.startOffset) }
            val bodyEnd = if (cLine != null) cLine - 1 else lineCount - 1
            for (l in (oLine + 1)..bodyEnd) if (l in 0 until lineCount) {
                depth[l] = d + 1; touched[l] = true; baseOf[l] = base
            }
            if (cLine != null && cLine in 0 until lineCount) { depth[cLine] = d; touched[cLine] = true; baseOf[cLine] = base }
            if (oLine in 0 until lineCount) { depth[oLine] = d; touched[oLine] = true; baseOf[oLine] = base }
            for (child in node.children) walk(child, d + 1, base)
        }
        for (root in tree.roots) walk(root, 0, leadingWhitespace(document, openerLine(root)))

        val stringSpans = PsiTreeUtil.collectElements(antlers) {
            it.node?.elementType == AntlersTypes.T_STRING
        }.map { it.textRange }

        val edits = mutableListOf<Pair<TextRange, String>>()
        for (line in 0 until lineCount) {
            val lineStart = document.getLineStartOffset(line)
            if (lineStart < rangeToReformat.startOffset || lineStart > rangeToReformat.endOffset) continue
            if (continuation[line] || !touched[line]) continue         // multiline-owned, or top-level (leave it)
            if (stringSpans.any { it.startOffset < lineStart && lineStart < it.endOffset }) continue

            val lineEnd = document.getLineEndOffset(line)
            val lineText = document.getText(TextRange(lineStart, lineEnd))
            val firstNonWs = lineText.indexOfFirst { it != ' ' && it != '\t' }
            if (firstNonWs < 0) {                                      // blank line → strip
                if (lineStart != lineEnd) edits.add(TextRange(lineStart, lineEnd) to "")
                continue
            }
            val contentOffset = lineStart + firstNonWs
            val currentIndent = lineText.substring(0, firstNonWs)
            val base = baseOf[line] ?: ""
            val d = depth[line]
            val next = lineText.getOrNull(firstNonWs + 1)
            val isHtmlTag = lineText[firstNonWs] == '<' && (next != null && (next.isLetter() || next == '/'))
            val target = if (isHtmlTag) currentIndent + unit.repeat(d) else base + unit.repeat(d)

            val indentRange = TextRange(lineStart, contentOffset)
            if (document.getText(indentRange) != target) edits.add(indentRange to target)
        }

        var delta = 0
        for ((indentRange, replacement) in edits.distinct().sortedByDescending { it.first.startOffset }) {
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

- [ ] **Step 4: Register it BEFORE the multiline processor**

In `src/main/resources/META-INF/plugin.xml`, the formatter registrations are:
```xml
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersSpacingPostFormatProcessor"/>
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersMultilineTagIndentProcessor"/>
```
Insert the new processor between them so it runs after spacing and before multiline:
```xml
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersSpacingPostFormatProcessor"/>
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersBlockIndentProcessor"/>
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersMultilineTagIndentProcessor"/>
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersBlockIndentTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentProcessor.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt
git commit -m "feat(formatter): indent paired-tag bodies one level per enclosing pair"
```

---

### Task 2: HTML body, catalog-pair tag, composition with multiline, idempotency

**Files:**
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt`

These exercise behavior the Task 1 processor should already satisfy. If one fails, fix the processor (it's a real defect) and report.

- [ ] **Step 1: Add the tests**

Append to `AntlersBlockIndentTest.kt`:
```kotlin
    fun testCollectionPairTagBodyIndented() {
        // `collection` opens because the catalog marks it isPair (the project service is available in tests).
        val out = reformat("{{ collection:blog }}\n{{ title }}\n{{ /collection }}")
        val u = unit()
        assertEquals("{{ collection:blog }}\n${u}{{ title }}\n{{ /collection }}", out)
    }

    fun testHtmlBodyLineShiftsAtLeastOneLevel() {
        // The HTML formatter owns the HTML line's base; we only assert it gains the Antlers level and that
        // the opener/closer stay put — exact column is HTML-formatter dependent (documented).
        val out = reformat("{{ if x }}\n<span>hi</span>\n{{ /if }}")
        val u = unit()
        val lines = out.split("\n")
        assertEquals("{{ if x }}", lines[0])
        assertTrue("html body indented at least one level, got: <${lines[1]}>", lines[1].startsWith(u))
        assertTrue("html body content present", lines[1].trim() == "<span>hi</span>")
        assertEquals("{{ /if }}", lines.last())
    }

    fun testMultilineParamInsideBlockComposes() {
        // A multi-line opener inside a pair: block sets the opener line indent, multiline indents its params
        // one level under that — they must compound.
        val out = reformat("{{ if x }}\n{{ collection:blog\nlimit=\"3\"\n}}\n{{ /if }}")
        val u = unit()
        assertEquals(
            "{{ if x }}\n${u}{{ collection:blog\n${u}${u}limit=\"3\"\n${u}}}\n{{ /if }}",
            out
        )
    }

    fun testIdempotentAntlers() {
        val once = reformat("{{ if a }}\n{{ if b }}\n{{ title }}\n{{ /if }}\n{{ /if }}")
        val twice = reformat(once)
        assertEquals(once, twice)
    }

    fun testIdempotentMixedHtml() {
        val once = reformat("{{ collection:blog }}\n<article>\n<h2>{{ title }}</h2>\n</article>\n{{ /collection }}")
        val twice = reformat(once)
        assertEquals("reformat must be a fixed point (no runaway), got:\n$twice", once, twice)
    }
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew --rerun-tasks test --tests "*AntlersBlockIndentTest"`
Expected: PASS (7 tests total). If `testMultilineParamInsideBlockComposes` fails on offsets, verify the processor runs BEFORE the multiline processor (plugin.xml order) — the block pass must set the `{{ collection` opener-line indent first. If `testIdempotentMixedHtml` shows growth between runs, the HTML-line additive is double-applying; confirm HTML element lines use `isHtmlTag` (not the absolute branch) and that `currentIndent` is read fresh each pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt
git commit -m "test(formatter): block-indent html body, composition, idempotency"
```

---

### Task 3: Edge cases + no-regression

**Files:**
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt`

- [ ] **Step 1: Add the edge-case tests**

Append to `AntlersBlockIndentTest.kt`:
```kotlin
    fun testOptOutLeavesBodyUntouched() {
        val settings = com.github.balotias.intellijantlers.settings.AntlersFormatterSettings.getInstance(project)
        val prev = settings.reformatEnabled
        settings.reformatEnabled = false
        try {
            val src = "{{ if x }}\n{{ title }}\n{{ /if }}"
            assertEquals(src, reformat(src))
        } finally {
            settings.reformatEnabled = prev
        }
    }

    fun testTopLevelContentUntouched() {
        // No paired block → processor is a no-op; a plain {{ }} + text keep column 0.
        val out = reformat("{{ title }}\nplain text")
        assertEquals("{{ title }}\nplain text", out)
    }

    fun testUnclosedIfDoesNotCrashAndIndentsBody() {
        val out = reformat("{{ if x }}\n{{ title }}")
        val u = unit()
        assertEquals("{{ if x }}\n${u}{{ title }}", out)
    }

    fun testStrayCloserDoesNotCrash() {
        // An unmatched closer contributes no depth; surrounding content is untouched, no exception.
        val out = reformat("{{ /collection }}\n{{ title }}")
        assertEquals("{{ /collection }}\n{{ title }}", out)
    }
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew --rerun-tasks test --tests "*AntlersBlockIndentTest"`
Expected: PASS (11 tests total).

- [ ] **Step 3: Confirm no regression in the other formatter tests**

Run: `./gradlew --rerun-tasks test --tests "*AntlersHtmlFormatTest" --tests "*AntlersMultilineFormatTest" --tests "*AntlersSpacingFormatterTest" --tests "*AntlersFormatterOptOutTest"`
Expected: all PASS. The processor early-outs when there are no paired Antlers blocks, so single-tag / HTML-only fixtures are unaffected. If a multiline fixture that contains a paired block shifted, reconcile (the block + multiline composition is the intended new behavior — update that fixture's expectation only if the new indentation is correct).

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt
git commit -m "test(formatter): block-indent opt-out, unclosed, stray-closer, no-regression"
```

---

### Task 4: Full-suite gate

**Files:** none (verification only).

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml; echo "exit:$?"`
Expected: prints nothing, `exit:1`.

- [ ] **Step 3: Count check**

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END{print s}'`
Expected: previous total + 11, zero failures/errors.

If green, proceed to whole-feature review and `superpowers:finishing-a-development-branch`.

---

## Notes for the implementer

- **Idempotency is the core risk.** Antlers/text lines are set ABSOLUTELY (`base + depth*unit`, never reading their own prior indent); HTML element lines are ADDITIVE (`currentIndent + depth*unit`) and rely on the HTML formatter re-normalizing them each reformat. The `testIdempotent*` tests pin this — if they fail, do not "fix" by tweaking the test.
- **`<?php`/`<?=` lines** start with `<` but `next` is `?` (not a letter or `/`), so `isHtmlTag` is false → they take the absolute branch (correct; HTML doesn't own them).
- **Processor order in plugin.xml matters:** spacing → block-indent → multiline.
- Run the **full suite with `--rerun-tasks`** and gate on the result XML.
