# Antlers Formatter — Unified Indentation Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two-model (HTML-formatter + additive Antlers post-processors) indentation with a single post-format pass that indents every line to one combined absolute depth.

**Architecture:** A new `AntlersIndentProcessor` sums four region contributors — HTML `XmlTag`s (from the HTML data PSI tree, via a new `AntlersHtmlNesting` helper), Antlers pairs/conditions (`AntlersNestingTreeBuilder`), template-named `<{{ }}>` tags (`AntlersTemplateTags`), and multi-line `{{ }}` params — and rewrites each non-preserved line's leading whitespace to `unit × depth`. The platform HTML formatting model builder becomes an indent no-op; the two old indent post-processors are deleted. The spacing post-processor is unchanged.

**Tech Stack:** Kotlin, IntelliJ Platform SDK; tests via `BasePlatformTestCase` and JUnit. Build/test: `./gradlew --rerun-tasks test`.

**Spec:** `docs/superpowers/specs/2026-06-07-antlers-formatter-unified-indent-design.md`

**Conventions:** Work on a feature branch off `main` (subagent-driven-development creates one). Gate the suite with `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` printing nothing. Commit trailer:
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```
Build note: `--rerun-tasks` is REQUIRED (Gradle caches). Avoid bare shell globs that error when no file matches (use `if grep …; then … else … fi`).

---

## File Structure

- **New** `formatter/AntlersHtmlNesting.kt` — given the `Document` + HTML data `PsiFile`, returns per-line HTML `XmlTag` depth and the `<pre>`/`<textarea>` interior lines to preserve.
- **New** `formatter/AntlersIndentProcessor.kt` — the unified `PostFormatProcessor`.
- **Rewrite** `formatter/AntlersHtmlFormattingModelBuilder.kt` — a plain `FormattingModelBuilder` returning an indent-no-op block (no longer extends the XML template base).
- **Delete** `formatter/AntlersBlockIndentProcessor.kt`, `formatter/AntlersMultilineTagIndentProcessor.kt`.
- **Edit** `META-INF/plugin.xml` — drop the two old `postFormatProcessor` regs, add the new one.
- **Tests**: new `formatter/AntlersHtmlNestingTest.kt`, new `formatter/AntlersIndentTest.kt`; reconcile existing `formatter/AntlersBlockIndentTest.kt`, `formatter/AntlersMultilineFormatTest.kt`, `formatter/AntlersHtmlFormatTest.kt` to corrected columns. Keep `AntlersSpacingFormatterTest`, `AntlersTemplateTagsTest`, `AntlersFormatterOptOutTest`, `AntlersFormatterSettingsTest`.
- **Edit** `README.md` — drop the now-fixed formatter "known limitations".

---

## Task 1: `AntlersHtmlNesting` helper

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersHtmlNesting.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersHtmlNestingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersHtmlNestingTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.formatter

import com.intellij.lang.html.HTMLLanguage
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersHtmlNestingTest : BasePlatformTestCase() {

    private fun compute(text: String): AntlersHtmlNesting.Result {
        val file = myFixture.configureByText("p.antlers.html", text)
        val html = file.viewProvider.getPsi(HTMLLanguage.INSTANCE)
        val doc = file.viewProvider.document!!
        return AntlersHtmlNesting.compute(doc, html)
    }

    fun testNestedDivSpanDepth() {
        // line0 <div>  line1 <span>  line2 x  line3 </span>  line4 </div>
        val r = compute("<div>\n<span>\nx\n</span>\n</div>")
        assertEquals(0, r.depth[0])   // <div>
        assertEquals(1, r.depth[1])   // <span> is inside div
        assertEquals(2, r.depth[2])   // x is inside div+span
        assertEquals(1, r.depth[3])   // </span> inside div
        assertEquals(0, r.depth[4])   // </div>
    }

    fun testVoidElementAddsNoDepth() {
        // <br> has no multi-line interior → contributes nothing to following lines
        val r = compute("<div>\n<br>\nx\n</div>")
        assertEquals(1, r.depth[1])   // <br>
        assertEquals(1, r.depth[2])   // x — only div, not br
    }

    fun testPreInteriorPreserved() {
        val r = compute("<pre>\n  keep me\n</pre>")
        assertTrue("pre interior preserved", r.preserve.contains(1))
        assertFalse("pre open line not preserved", r.preserve.contains(0))
    }

    fun testNullHtmlYieldsZeros() {
        val r = AntlersHtmlNesting.compute(
            myFixture.configureByText("p.antlers.html", "<div>\nx\n</div>").viewProvider.document!!,
            null
        )
        assertTrue(r.depth.all { it == 0 })
        assertTrue(r.preserve.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.AntlersHtmlNestingTest"`
Expected: FAIL to compile (`AntlersHtmlNesting` unresolved).

- [ ] **Step 3: Write the implementation**

Create `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersHtmlNesting.kt`:

```kotlin
package com.github.balotias.intellijantlers.formatter

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

/**
 * Per-line HTML nesting depth from the template-data (HTML) PSI tree: every [XmlTag] adds 1 to the lines
 * strictly inside it (its open and close lines stay at the enclosing level). The HTML parser handles
 * void / self-closing / optional-close / inline tags and multi-line attribute lists for free.
 * `<pre>` / `<textarea>` interiors are reported as preserve lines (significant whitespace). Never throws.
 */
object AntlersHtmlNesting {

    class Result(val depth: IntArray, val preserve: Set<Int>)

    private val PRESERVE_TAGS = setOf("pre", "textarea")

    fun compute(document: Document, htmlFile: PsiFile?): Result {
        val lineCount = document.lineCount
        val depth = IntArray(lineCount)
        val preserve = HashSet<Int>()
        if (htmlFile == null) return Result(depth, preserve)

        for (tag in PsiTreeUtil.findChildrenOfType(htmlFile, XmlTag::class.java)) {
            val range = tag.textRange
            if (range.isEmpty) continue
            val sLine = document.getLineNumber(range.startOffset)
            val eLine = document.getLineNumber(range.endOffset - 1)
            if (eLine <= sLine) continue                       // single-line tag → no interior
            val isPreserve = tag.name.lowercase() in PRESERVE_TAGS
            for (l in (sLine + 1) until eLine) {
                depth[l] += 1
                if (isPreserve) preserve.add(l)
            }
        }
        return Result(depth, preserve)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.AntlersHtmlNestingTest"`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*AntlersHtmlNestingTest*.xml || echo CLEAN`
Expected: PASS (4 tests), CLEAN.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersHtmlNesting.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersHtmlNestingTest.kt
git commit -m "feat(formatter): AntlersHtmlNesting — per-line HTML depth from the data PSI tree

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `AntlersIndentProcessor` + pipeline switch (atomic)

This replaces the indentation engine. After this task the whole suite must be green: the new processor is wired in, the two old processors and the additive model are gone, and the existing formatter tests are reconciled to the corrected columns.

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersIndentProcessor.kt`
- Create: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersIndentTest.kt`
- Rewrite: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersHtmlFormattingModelBuilder.kt`
- Delete: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentProcessor.kt`, `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt`
- Edit: `src/main/resources/META-INF/plugin.xml`
- Reconcile: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt`, `AntlersMultilineFormatTest.kt`, `AntlersHtmlFormatTest.kt`

- [ ] **Step 1: Write the failing behavioral tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersIndentTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersIndentTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        val file = myFixture.configureByText("p.antlers.html", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        return file.text
    }

    private fun u(): String {
        val o = CodeStyle.getIndentOptions(myFixture.file)
        return if (o.USE_TAB_CHARACTER) "\t" else " ".repeat(o.INDENT_SIZE)
    }

    private fun assertStable(src: String) {
        val a = reformat(src); val b = reformat(a)
        assertEquals("not idempotent:\n$a", a, b)
    }

    fun testHtmlInsideTwoAntlersPairs() {
        val u = u()
        val out = reformat("{{ if a }}\n{{ if b }}\n<div>\n<span>x</span>\n</div>\n{{ /if }}\n{{ /if }}")
        assertEquals(
            "{{ if a }}\n${u}{{ if b }}\n${u}${u}<div>\n${u}${u}${u}<span>x</span>\n${u}${u}</div>\n${u}{{ /if }}\n{{ /if }}",
            out
        )
    }

    fun testAntlersInsideHtmlInsideAntlers() {
        val u = u()
        val out = reformat("{{ collection:x }}\n<div>\n{{ if a }}\n<span>y</span>\n{{ /if }}\n</div>\n{{ /collection }}")
        assertEquals(
            "{{ collection:x }}\n${u}<div>\n${u}${u}{{ if a }}\n${u}${u}${u}<span>y</span>\n${u}${u}{{ /if }}\n${u}</div>\n{{ /collection }}",
            out
        )
    }

    fun testPureHtmlIndentsByHtmlNesting() {
        val u = u()
        assertEquals("<div>\n${u}<span>x</span>\n</div>", reformat("<div>\n<span>x</span>\n</div>"))
    }

    fun testPreInteriorUntouched() {
        val out = reformat("<div>\n<pre>\n      keep\n</pre>\n</div>")
        val u = u()
        // <pre> indented as html child, but its interior line keeps its original whitespace
        assertEquals("<div>\n${u}<pre>\n      keep\n${u}</pre>\n</div>", out)
    }

    fun testIdempotentMatrix() {
        assertStable("{{ if a }}\n{{ if b }}\n<div>\n<span>x</span>\n</div>\n{{ /if }}\n{{ /if }}")
        assertStable("{{ collection:x }}\n<div>\n{{ if a }}\n<span>y</span>\n{{ /if }}\n</div>\n{{ /collection }}")
        assertStable("<{{ as or 'a' }}>\n<span>x</span>\n</{{ as or 'a' }}>")
    }

    fun testElseDedents() {
        val u = u()
        assertEquals(
            "{{ if a }}\n${u}x\n{{ else }}\n${u}y\n{{ /if }}",
            reformat("{{ if a }}\nx\n{{ else }}\ny\n{{ /if }}")
        )
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.AntlersIndentTest"`
Expected: multiple FAILs — current code produces the buggy columns (e.g. `<div>` at 3 levels in `testHtmlInsideTwoAntlersPairs`).

- [ ] **Step 3: Create the unified processor**

Create `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersIndentProcessor.kt`:

```kotlin
package com.github.balotias.intellijantlers.formatter

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.github.balotias.intellijantlers.scope.NestingNode
import com.github.balotias.intellijantlers.settings.AntlersFormatterSettings
import com.intellij.application.options.CodeStyle
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil

/**
 * Single absolute-indent pass. For each physical line it sums four region contributors into one depth and
 * rewrites the leading whitespace to `unit * depth` — so indentation reflects the true combined nesting of
 * HTML elements, Antlers pairs/conditions, template-named `<{{ }}>` tags, and multi-line `{{ }}` params.
 * Whitespace-significant / opaque regions (`<pre>`/`<textarea>`, multi-line strings, comment/noparse/PHP
 * interiors) are preserved. Indentation-only, idempotent, tolerant; never throws. In-`{{ }}` spacing is
 * owned by [AntlersSpacingPostFormatProcessor].
 */
class AntlersIndentProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (!AntlersFormatterSettings.getInstance(source.project).reformatEnabled) return rangeToReformat
        val document = source.viewProvider.document ?: return rangeToReformat
        // The spacing pass may have edited the document without committing PSI; re-sync so PSI offsets match.
        PsiDocumentManager.getInstance(source.project).commitDocument(document)
        val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat
        val html = source.viewProvider.getPsi(HTMLLanguage.INSTANCE)

        val lineCount = document.lineCount
        if (lineCount == 0) return rangeToReformat
        val opts = CodeStyle.getIndentOptions(source)
        val unit = if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)

        val depth = IntArray(lineCount)
        val preserve = BooleanArray(lineCount)

        // (1) HTML element nesting + pre/textarea preserve.
        val htmlNesting = AntlersHtmlNesting.compute(document, html)
        for (l in 0 until lineCount) depth[l] += htmlNesting.depth.getOrElse(l) { 0 }
        for (l in htmlNesting.preserve) if (l in 0 until lineCount) preserve[l] = true

        // (2) Antlers pairs & conditions. Body is opener-END+1 .. closer-START-1 (so a multi-line opener's
        // own param lines are left to contributor 4); else/elseif lines stay at the opener level.
        val elseLines = PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)
            .filter {
                val kw = (it.condition as? AntlersConditionMixin)?.keyword
                kw == "else" || kw == "elseif"
            }
            .map { document.getLineNumber(it.textRange.startOffset) }
            .toSet()
        fun walk(node: NestingNode) {
            val oStart = document.getLineNumber(node.opener.textRange.startOffset)
            val oEnd = document.getLineNumber(node.opener.textRange.endOffset - 1)
            // Unclosed multi-line opener: a self-contained tag, not a block — don't indent a "body".
            if (node.closer == null && oEnd > oStart) { node.children.forEach(::walk); return }
            val cStart = node.closer?.let { document.getLineNumber(it.textRange.startOffset) }
            val bodyEnd = if (cStart != null) cStart - 1 else lineCount - 1
            for (l in (oEnd + 1)..bodyEnd) if (l in 0 until lineCount && l !in elseLines) depth[l] += 1
            node.children.forEach(::walk)
        }
        AntlersNestingTreeBuilder.build(antlers, source.project).roots.forEach(::walk)

        // (3) Template-named tags `<{{ … }}> … </{{ … }}>`: interior +1, excluding the `>` boundary line.
        for (e in AntlersTemplateTags.elements(document.text)) {
            val end = e.closeLine ?: lineCount
            for (l in (e.openStartLine + 1) until end) {
                if (l != e.openEndLine && l in 0 until lineCount) depth[l] += 1
            }
        }

        // (4) Multi-line `{{ }}` params: each interior line +1 + bracket nesting; the `}}` line gets +0.
        for (stmt in PsiTreeUtil.findChildrenOfType(antlers, AntlersStatement::class.java)) {
            val sLine = document.getLineNumber(stmt.textRange.startOffset)
            val eLine = document.getLineNumber(stmt.textRange.endOffset - 1)
            if (eLine == sLine) continue
            val lastLeaf = PsiTreeUtil.getDeepestLast(stmt)
            val rdoubleStart =
                if (lastLeaf.node.elementType == AntlersTypes.T_RDOUBLE) lastLeaf.textRange.startOffset else -1
            val brackets = PsiTreeUtil.collectElements(stmt) { BRACKET_DELTAS.containsKey(it.node?.elementType) }
                .map { it.textRange.startOffset to BRACKET_DELTAS.getValue(it.node!!.elementType) }
            for (line in (sLine + 1)..eLine) {
                if (line !in 0 until lineCount) continue
                val lineStart = document.getLineStartOffset(line)
                val text = document.getText(TextRange(lineStart, document.getLineEndOffset(line)))
                val firstNonWs = text.indexOfFirst { it != ' ' && it != '\t' }
                if (firstNonWs < 0) continue
                val contentOffset = lineStart + firstNonWs
                if (line == eLine && contentOffset == rdoubleStart) continue   // `}}` on its own line → +0
                val openBefore = brackets.filter { it.first < contentOffset }.sumOf { it.second }
                val closesFirst = if (text[firstNonWs] in "])}") 1 else 0
                depth[line] += maxOf(1, 1 + openBefore - closesFirst)
            }
        }

        // Preserve: interiors of multi-line strings and comment/noparse/PHP blocks (a line whose START is
        // strictly inside one of these ranges).
        val opaqueRanges = PsiTreeUtil.collectElements(antlers) { it.node?.elementType in OPAQUE_TYPES }
            .map { it.textRange }

        val edits = mutableListOf<Pair<TextRange, String>>()
        for (line in 0 until lineCount) {
            val lineStart = document.getLineStartOffset(line)
            if (lineStart < rangeToReformat.startOffset || lineStart > rangeToReformat.endOffset) continue
            if (preserve[line]) continue
            if (opaqueRanges.any { it.startOffset < lineStart && lineStart < it.endOffset }) continue
            val lineEnd = document.getLineEndOffset(line)
            val text = document.getText(TextRange(lineStart, lineEnd))
            val firstNonWs = text.indexOfFirst { it != ' ' && it != '\t' }
            if (firstNonWs < 0) {                                  // blank line → strip
                if (lineStart != lineEnd) edits.add(TextRange(lineStart, lineEnd) to "")
                continue
            }
            val contentOffset = lineStart + firstNonWs
            val target = unit.repeat(depth[line].coerceAtLeast(0))
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

    companion object {
        private val BRACKET_DELTAS: Map<IElementType, Int> = mapOf(
            AntlersTypes.T_LBRACE to 1, AntlersTypes.T_LBRACKET to 1, AntlersTypes.T_LPAREN to 1,
            AntlersTypes.T_RBRACE to -1, AntlersTypes.T_RBRACKET to -1, AntlersTypes.T_RPAREN to -1,
        )
        private val OPAQUE_TYPES = setOf(
            AntlersTypes.T_STRING, AntlersTypes.T_COMMENT_TEXT,
            AntlersTypes.T_NOPARSE_TEXT, AntlersTypes.T_PHP_TEXT,
        )
    }
}
```

- [ ] **Step 4: Rewrite the model builder to an indent no-op**

Replace the entire contents of `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersHtmlFormattingModelBuilder.kt` with:

```kotlin
package com.github.balotias.intellijantlers.formatter

import com.intellij.formatting.Block
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock

/**
 * Indentation for `*.antlers.html` is handled entirely by the post-format processors
 * ([AntlersIndentProcessor] for indentation, [AntlersSpacingPostFormatProcessor] for `{{ }}` spacing).
 * The formatting model itself is a single whole-file leaf that changes nothing, so the platform HTML
 * formatter never competes for indentation.
 */
class AntlersHtmlFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val file = formattingContext.containingFile
        return FormattingModelProvider.createFormattingModelForPsiFile(
            file, NoopBlock(file.node), formattingContext.codeStyleSettings
        )
    }

    private class NoopBlock(node: ASTNode) : AbstractBlock(node, null, null) {
        override fun buildChildren(): List<Block> = emptyList()
        override fun getSpacing(child1: Block?, child2: Block): Spacing? = null
        override fun isLeaf(): Boolean = true
        override fun getIndent(): Indent = Indent.getNoneIndent()
    }
}
```

- [ ] **Step 5: Delete the two old processors and switch the registrations**

```bash
git rm src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentProcessor.kt \
       src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt
```

In `src/main/resources/META-INF/plugin.xml`, replace these two lines:

```xml
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersBlockIndentProcessor"/>
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersMultilineTagIndentProcessor"/>
```

with this single line:

```xml
        <postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersIndentProcessor"/>
```

(Leave the `AntlersSpacingPostFormatProcessor` line above them and the `lang.formatter` line below them unchanged.)

- [ ] **Step 6: Run the new behavioral tests**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.AntlersIndentTest"`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*AntlersIndentTest*.xml || echo CLEAN`
Expected: PASS (6 tests), CLEAN. If a case is off, fix `AntlersIndentProcessor` (not the test) until it matches the spec's depth rule.

- [ ] **Step 7: Reconcile the legacy formatter tests**

Run the three legacy suites and update their expectations to the corrected output:

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.AntlersBlockIndentTest" --tests "com.github.balotias.intellijantlers.formatter.AntlersMultilineFormatTest" --tests "com.github.balotias.intellijantlers.formatter.AntlersHtmlFormatTest"`

For EACH failing case:
1. Read the test's input and current expected output.
2. Compute the correct output by the spec's depth rule (sum of enclosing HTML tags + Antlers pairs + template tags + multiline params; opaque/pre interiors preserved).
3. **Verify** the new actual output equals that correct value — do not just paste the actual output without checking it against the rule. If actual ≠ rule, the processor has a bug; fix the processor.
4. Update the test's expected string to the verified-correct value.
5. Delete tests that asserted the old "known limitations" (e.g. `testVoidAfterAntlersAttribute`, and any `testContent...` that pinned the buggy content-dependent columns) — these behaviors are now fixed; replace with a correct-behavior assertion if the scenario is still worth covering, otherwise remove.

Keep `AntlersSpacingFormatterTest`, `AntlersTemplateTagsTest`, `AntlersFormatterOptOutTest`, `AntlersFormatterSettingsTest` unchanged — they don't assert column math and (verified) none import the deleted classes; `AntlersFormatterOptOutTest` drives `reformat` through the settings gate, which `AntlersIndentProcessor` honors identically. The only reference to the deleted processors is the two `plugin.xml` lines removed in Step 5.

- [ ] **Step 8: Full formatter package green**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.*"`
Then: `if grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml 2>/dev/null; then echo FAIL; else echo CLEAN; fi`
Expected: BUILD SUCCESSFUL, CLEAN.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(formatter): unified absolute-indent pass over HTML+Antlers nesting

Replaces the HTML-formatter + additive post-processors with a single AntlersIndentProcessor
that indents each line to one combined depth. Deletes AntlersBlockIndentProcessor and
AntlersMultilineTagIndentProcessor; the model builder is now an indent no-op.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Edge-case + idempotency sweep, and README

**Files:**
- Edit: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersIndentTest.kt` (add cases)
- Edit: `README.md`

- [ ] **Step 1: Add coverage**

Append these tests to `AntlersIndentTest.kt` (inside the class):

```kotlin
    fun testInlineTagAddsLevel() {
        val u = u()
        assertEquals("<a href=\"x\">\n${u}link\n</a>", reformat("<a href=\"x\">\nlink\n</a>"))
    }

    fun testMultilineParamsIndentUnderOpener() {
        val u = u()
        val out = reformat("{{ collection:blog\nlimit=\"3\"\n}}\n{{ /collection }}")
        assertEquals("{{ collection:blog\n${u}limit=\"3\"\n}}\n{{ /collection }}", out)
    }

    fun testNoparseInteriorPreserved() {
        val out = reformat("{{ if a }}\n{{ noparse }}\n      raw {{ x }}\n{{ /noparse }}\n{{ /if }}")
        val u = u()
        assertEquals("{{ if a }}\n${u}{{ noparse }}\n      raw {{ x }}\n${u}{{ /noparse }}\n{{ /if }}", out)
    }

    fun testMultilineStringNotReindented() {
        val out = reformat("{{ if a }}\n<div class=\"\nfoo\nbar\n\">x</div>\n{{ /if }}")
        // the string-interior lines (foo / bar / closing-quote) keep their original indentation
        assertTrue("string interior preserved", out.contains("\nfoo\nbar\n"))
    }

    fun testTemplateNamedTagBodyIndents() {
        val u = u()
        assertEquals("<{{ as or 'a' }}>\n${u}<span>x</span>\n</{{ as or 'a' }}>",
            reformat("<{{ as or 'a' }}>\n<span>x</span>\n</{{ as or 'a' }}>"))
    }

    fun testStrayCloserDoesNotCrashOrRunaway() {
        val out = reformat("{{ /collection }}\n<div>\nx\n</div>")
        assertStable(out)   // tolerant + idempotent
    }
}
```
(Place the closing `}` of the class at the end; do not duplicate it.)

- [ ] **Step 2: Run**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.AntlersIndentTest"`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*AntlersIndentTest*.xml || echo CLEAN`
Expected: PASS, CLEAN. Fix the processor (not the tests) if any genuine behavior is wrong.

- [ ] **Step 3: Update README**

In `README.md`, under the Formatting section, **remove** the "known limitations" bullets that described the now-fixed mixed-nesting / void-after-attribute / content-dependent indentation. Keep the description of what the formatter does (indentation-only, Prettier-safe, configurable indent). If the "Known limitations" section becomes empty, remove the heading.

- [ ] **Step 4: Full suite gate**

Run: `./gradlew --rerun-tasks test`
Then: `if grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml 2>/dev/null; then echo FAIL; else echo CLEAN; fi`
Expected: BUILD SUCCESSFUL, CLEAN.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test(formatter): edge-case + idempotency coverage; README known-limitations removed

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Definition of Done

- Mixed HTML/Antlers nesting indents to the true combined depth (the reproductions are fixed).
- Pure-HTML `.antlers.html` indents by HTML nesting; inline tags add a level.
- `<pre>`/`<textarea>`, multi-line strings, and comment/noparse/PHP interiors are preserved.
- Multi-line `{{ }}` params, conditions (else/elseif dedent), and template-named tags indent correctly.
- Every case is idempotent (`reformat == reformat²`).
- The two old indent processors are gone; one `AntlersIndentProcessor` owns indentation; spacing pass unchanged.
- Full suite green; README "known limitations" removed.
```
