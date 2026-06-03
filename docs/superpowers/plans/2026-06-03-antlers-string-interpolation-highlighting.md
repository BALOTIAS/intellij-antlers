# Antlers String-Interpolation Highlighting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Highlight Antlers `{ … }` interpolation inside string literals as a real Antlers expression, instead of one flat `STRING` color.

**Architecture:** A new `Annotator` overlays Antlers colors on the `{ … }` sub-spans of each `T_STRING` leaf (annotators run after lexer highlighting and override sub-ranges, exactly like the existing `AntlersSemanticHighlightAnnotator`). A pure span scanner finds the `{ … }` ranges; the annotator re-lexes each span's inner content with `_AntlersLexer` started in the `EXPR` state and paints each token. No lexer/parser/grammar/regen changes; no new color keys.

**Tech Stack:** Kotlin, IntelliJ Platform (`Annotator`, `FlexAdapter`, `_AntlersLexer`, `AntlersSyntaxHighlighter` color keys), JUnit / `BasePlatformTestCase`.

**Spec:** `docs/superpowers/specs/2026-06-03-antlers-string-interpolation-highlighting-design.md`
**Branch:** `antlers-string-interpolation-highlighting` (already created from `main`; spec already committed).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (must be empty).

## File Structure

- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersInterpolationScanner.kt`
  — pure `object` that finds `{ … }` spans in a string's raw text. No IntelliJ deps → unit-testable in isolation.
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationAnnotator.kt`
  — the `Annotator`: gate on `T_STRING` leaves, call the scanner, paint braces, sub-lex + color the inner content.
- **Modify** `src/main/resources/META-INF/plugin.xml` — register the annotator (next to the other two, ~line 38).
- **Create** `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersInterpolationScannerTest.kt` — pure JUnit tests of the scanner.
- **Create** `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt` — fixture tests of the painted colors.

---

### Task 1: Interpolation span scanner

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersInterpolationScanner.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersInterpolationScannerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersInterpolationScannerTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class AntlersInterpolationScannerTest {

    /** The inner content (between the braces) of each span found in [text]. */
    private fun contents(text: String): List<String> =
        AntlersInterpolationScanner.scan(text).map { text.substring(it.contentStart, it.contentEnd) }

    @Test fun findsSingleSpan() =
        assertEquals(listOf("logo:focus_css"), contents("\"object-position: {logo:focus_css}\""))

    @Test fun findsTwoSpans() =
        assertEquals(listOf("one", "two"), contents("\"a {one} b {two} c\""))

    @Test fun ignoresEscapedBrace() =
        assertEquals(emptyList<String>(), contents("\"a \\{not} b\""))

    @Test fun unmatchedOpenIsIgnored() =
        assertEquals(emptyList<String>(), contents("\"a {oops\""))

    @Test fun nestedBracesBalanced() =
        assertEquals(listOf("a {b} c"), contents("\"x {a {b} c} y\""))

    @Test fun braceInsideSingleQuoteDoesNotClose() =
        assertEquals(listOf("foo:'}'"), contents("\"{foo:'}'}\""))

    @Test fun emptyBracesYieldEmptyContent() {
        val spans = AntlersInterpolationScanner.scan("\"{}\"")
        assertEquals(1, spans.size)
        assertEquals(spans[0].contentStart, spans[0].contentEnd)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInterpolationScannerTest"`
Expected: FAIL — compile error (`AntlersInterpolationScanner` unresolved).

- [ ] **Step 3: Write the scanner**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersInterpolationScanner.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

/**
 * Finds Antlers `{ … }` interpolation spans inside a string literal's raw text (quotes included).
 * Pure / IntelliJ-free so it can be unit-tested directly. Highlighting only — no parsing.
 */
object AntlersInterpolationScanner {

    /** Offsets relative to the scanned text. `contentStart == openBrace + 1`, `contentEnd == closeBrace`. */
    data class Span(val openBrace: Int, val contentStart: Int, val contentEnd: Int, val closeBrace: Int)

    fun scan(text: String): List<Span> {
        val spans = ArrayList<Span>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '\\') { i += 2; continue }          // escape: skip the next char (e.g. \{ )
            if (c == '{') {
                val span = matchSpan(text, i)
                if (span != null) { spans.add(span); i = span.closeBrace + 1; continue }
            }
            i++
        }
        return spans
    }

    /** From an opening `{` at [open], find the balanced closing `}`; null if unmatched. */
    private fun matchSpan(text: String, open: Int): Span? {
        var depth = 0
        var i = open
        val n = text.length
        var inSingle = false                              // inside a '…' inner string
        while (i < n) {
            val c = text[i]
            when {
                c == '\\' -> { i += 2; continue }         // escape in any state
                inSingle -> { if (c == '\'') inSingle = false; i++ }
                c == '\'' -> { inSingle = true; i++ }
                c == '{' -> { depth++; i++ }
                c == '}' -> {
                    depth--
                    if (depth == 0) return Span(open, open + 1, i, i)
                    i++
                }
                else -> i++
            }
        }
        return null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInterpolationScannerTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersInterpolationScanner.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersInterpolationScannerTest.kt
git commit -m "$(cat <<'EOF'
feat: interpolation span scanner for Antlers strings

Pure brace-balanced scanner that finds `{ … }` interpolation spans in a
string literal (handles nesting, \{ escapes, and } inside '…' inner strings).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Annotator + coloring + registration

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationAnnotator.kt`
- Modify: `src/main/resources/META-INF/plugin.xml` (after the `AntlersSemanticHighlightAnnotator` line, ~38)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersStringInterpolationHighlightTest : BasePlatformTestCase() {

    /** The forced color over the first highlight whose covered text is exactly [token], or null. */
    private fun keyOver(text: String, token: String): TextAttributesKey? {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .firstOrNull { it.text == token && it.forcedTextAttributesKey != null }
            ?.forcedTextAttributesKey
    }

    fun testInterpolatedVariableColored() =
        assertEquals(AntlersSyntaxHighlighter.IDENTIFIER,
            keyOver("{{ \"object-position: {logo:focus_css}\" }}", "logo"))

    fun testInterpolatedFieldColored() =
        assertEquals(AntlersSyntaxHighlighter.IDENTIFIER,
            keyOver("{{ \"object-position: {logo:focus_css}\" }}", "focus_css"))

    fun testInterpolationBraceColored() =
        assertEquals(AntlersSyntaxHighlighter.BRACES,
            keyOver("{{ \"object-position: {logo:focus_css}\" }}", "{"))

    fun testInterpolatedModifierColored() =
        assertEquals(AntlersSyntaxHighlighter.MODIFIER,
            keyOver("{{ \"{title | upper}\" }}", "upper"))

    fun testInterpolatedPipeIsOperator() =
        assertEquals(AntlersSyntaxHighlighter.OPERATOR,
            keyOver("{{ \"{title | upper}\" }}", "|"))

    fun testArrayBracketColored() =
        assertEquals(AntlersSyntaxHighlighter.BRACES,
            keyOver("{{ \"{['a', view:class] | classes}\" }}", "["))

    fun testArrayModifierColored() =
        assertEquals(AntlersSyntaxHighlighter.MODIFIER,
            keyOver("{{ \"{['a', view:class] | classes}\" }}", "classes"))

    fun testSurroundingStringTextNotColored() =
        assertNull(keyOver("{{ \"object-position: {logo:focus_css}\" }}", "object"))

    fun testPlainStringNotColored() =
        assertNull(keyOver("{{ \"hello world\" }}", "hello"))

    fun testEscapedBraceNotColored() =
        assertNull(keyOver("{{ \"a \\{not} b\" }}", "not"))

    fun testTwoInterpolationsColored() {
        myFixture.configureByText("p.antlers.html", "{{ \"a {one} b {two} c\" }}")
        val ids = myFixture.doHighlighting()
            .filter { it.forcedTextAttributesKey == AntlersSyntaxHighlighter.IDENTIFIER }
            .map { it.text }.toSet()
        assertTrue("both 'one' and 'two' colored, got $ids", ids.containsAll(listOf("one", "two")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersStringInterpolationHighlightTest"`
Expected: FAIL — every color assertion fails (no annotator yet → `keyOver` returns null for the colored cases).

- [ ] **Step 3: Write the annotator**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationAnnotator.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lexer.FlexAdapter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

/**
 * Colors Antlers `{ … }` interpolation inside a string literal as a real Antlers expression. The lexer
 * emits the whole literal as one flat T_STRING, so this overlays per-token colors on the interpolation
 * sub-spans (running after lexer highlighting, like [AntlersSemanticHighlightAnnotator]). The surrounding
 * string text keeps STRING. Highlighting only — no PSI/references inside the interpolation.
 */
class AntlersStringInterpolationAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != AntlersTypes.T_STRING) return
        val text = element.text
        val base = element.textRange.startOffset
        for (span in AntlersInterpolationScanner.scan(text)) {
            paint(holder, base + span.openBrace, base + span.openBrace + 1, AntlersSyntaxHighlighter.BRACES)
            paint(holder, base + span.closeBrace, base + span.closeBrace + 1, AntlersSyntaxHighlighter.BRACES)
            if (span.contentStart < span.contentEnd) lexInner(text, span, base, holder)
        }
    }

    private fun lexInner(text: String, span: AntlersInterpolationScanner.Span, base: Int, holder: AnnotationHolder) {
        val lexer = FlexAdapter(_AntlersLexer(null))
        lexer.start(text, span.contentStart, span.contentEnd, _AntlersLexer.EXPR)
        var prev: IElementType? = null
        while (true) {
            val type = lexer.tokenType ?: break
            colorFor(type, prev)?.let { paint(holder, base + lexer.tokenStart, base + lexer.tokenEnd, it) }
            if (type != AntlersTypes.T_WS) prev = type
            lexer.advance()
        }
    }

    private fun colorFor(type: IElementType, prev: IElementType?): TextAttributesKey? = when (type) {
        AntlersTypes.T_IDENT ->
            if (prev == AntlersTypes.T_PIPE) AntlersSyntaxHighlighter.MODIFIER else AntlersSyntaxHighlighter.IDENTIFIER
        AntlersTypes.T_DOLLAR -> AntlersSyntaxHighlighter.IDENTIFIER
        AntlersTypes.T_STRING -> AntlersSyntaxHighlighter.STRING
        AntlersTypes.T_NUMBER -> AntlersSyntaxHighlighter.NUMBER
        AntlersTypes.T_PIPE, AntlersTypes.T_COLON, AntlersTypes.T_DOT, AntlersTypes.T_OP,
        AntlersTypes.T_EQUALS, AntlersTypes.T_ARROW, AntlersTypes.T_SLASH,
        AntlersTypes.T_COMMA, AntlersTypes.T_SEMICOLON -> AntlersSyntaxHighlighter.OPERATOR
        AntlersTypes.T_LBRACE, AntlersTypes.T_RBRACE, AntlersTypes.T_LBRACKET, AntlersTypes.T_RBRACKET,
        AntlersTypes.T_LPAREN, AntlersTypes.T_RPAREN, AntlersTypes.T_AT -> AntlersSyntaxHighlighter.BRACES
        else -> null   // T_WS, BAD_CHARACTER, etc. → leave the STRING base color
    }

    private fun paint(holder: AnnotationHolder, start: Int, end: Int, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(start, end))
            .textAttributes(key)
            .create()
    }
}
```

- [ ] **Step 4: Register the annotator**

In `src/main/resources/META-INF/plugin.xml`, immediately after the line:
```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersSemanticHighlightAnnotator"/>
```
add:
```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersStringInterpolationAnnotator"/>
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersStringInterpolationHighlightTest"`
Expected: PASS (11 tests). If a color assertion fails, print the highlights for that input
(`myFixture.doHighlighting().filter { it.forcedTextAttributesKey != null }.map { it.text to it.forcedTextAttributesKey }`)
and reconcile against `colorFor` — do **not** loosen an assertion to make it pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationAnnotator.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt
git commit -m "$(cat <<'EOF'
feat: highlight `{ … }` interpolation inside Antlers strings

A new annotator re-lexes each interpolation span in the EXPR state and paints
identifiers/operators/braces/strings/numbers — and the modifier name after a
pipe — so `"…{logo:focus_css}…"` and `"{[…]|classes}"` read as real Antlers
instead of one flat string. Highlighting only; no lexer/parser/grammar change.

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

If any pre-existing parsing / highlight / balance test changed: the annotator is additive and must not
alter parsing or existing lexer-token colors. Investigate before proceeding — a regression here means
`colorFor` is painting outside interpolation spans or the scanner is over-matching. Do not edit other
tests to go green.

## Self-Review

- **Spec coverage:** scanner (nesting / `\{` escape / `}`-in-`'…'` / unmatched / empty) → Task 1; annotator
  gate on `T_STRING` + braces `BRACES` + `EXPR`-state sub-lex + `colorFor` table incl. pipe→`MODIFIER` →
  Task 2 Step 3; registration → Task 2 Step 4; reported #11 / #3 / modifier / surrounding-text-stays-string
  / plain-string / two-spans / escaped-brace highlight cases → Task 2 Step 1; full-suite gate → Task 3. No
  gaps.
- **Placeholder scan:** none — every code step has complete code; no TBD/“handle edge cases”.
- **Type consistency:** `AntlersInterpolationScanner.scan(String): List<Span>` and `Span(openBrace,
  contentStart, contentEnd, closeBrace)` are defined in Task 1 and used identically in Task 2; `colorFor`
  and `paint` signatures are self-consistent; `_AntlersLexer.EXPR` is the generated public constant
  (verified `= 2`); color keys (`IDENTIFIER`/`MODIFIER`/`OPERATOR`/`BRACES`/`STRING`/`NUMBER`) are existing
  `AntlersSyntaxHighlighter` companion vals.
