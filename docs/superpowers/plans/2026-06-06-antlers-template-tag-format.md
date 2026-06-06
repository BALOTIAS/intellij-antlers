# Combined-HTML Formatter — Template-Named Tags Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reformat Code indents the attributes and body of template-named HTML tags `<{{ html_tag }} … >` … `</{{ html_tag }}>`.

**Architecture:** A pure scanner (`AntlersTemplateTags`) finds template-named tags; `AntlersBlockIndentProcessor` adds their per-line depth to its existing combined depth (set absolutely → idempotent). No HTML-formatter change, no real-HTML reimplementation.

**Tech Stack:** Kotlin, IntelliJ `PostFormatProcessor`, `BasePlatformTestCase`. Build/test: `./gradlew --rerun-tasks test` (`--rerun-tasks` REQUIRED — Gradle caches). Gate: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

---

## File Structure

- `formatter/AntlersTemplateTags.kt` (create) — pure scanner for template-named tags.
- `formatter/AntlersBlockIndentProcessor.kt` (modify) — add template depth to the combined indent.
- `formatter/AntlersTemplateTagsTest.kt` (create, test) — pure scanner unit tests.
- `formatter/AntlersBlockIndentTest.kt` (modify, test) — template indentation + composition + golden.

---

### Task 1: `AntlersTemplateTags` pure scanner

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersTemplateTags.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersTemplateTagsTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `AntlersTemplateTagsTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.formatter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntlersTemplateTagsTest {
    @Test fun singleLineOpenClose() =
        assertEquals(listOf(TemplateElement(0, 0, 0)), AntlersTemplateTags.elements("<{{ tag }}>x</{{ tag }}>"))

    @Test fun multiLineOpenAndBody() =
        assertEquals(
            listOf(TemplateElement(0, 2, 4)),
            AntlersTemplateTags.elements("<{{ tag }}\nclass=\"x\"\n>\nbody\n</{{ tag }}>"))

    @Test fun selfClosingIsNotAContainer() =
        assertTrue(AntlersTemplateTags.elements("<{{ tag }} class=\"x\" />").isEmpty())

    @Test fun gtInsideAntlersIsNotTagEnd() =
        assertEquals(
            listOf(TemplateElement(0, 2, 3)),
            AntlersTemplateTags.elements("<{{ tag }}\n{{ x > 0 ? 'a' : 'b' }}\n>\n</{{ tag }}>"))

    @Test fun gtInsideQuoteIsNotTagEnd() =
        assertEquals(
            listOf(TemplateElement(0, 0, 0)),
            AntlersTemplateTags.elements("<{{ tag }} class=\"a>b\">x</{{ tag }}>"))

    @Test fun nestedTemplateTags() =
        assertEquals(2, AntlersTemplateTags.elements("<{{ a }}>\n<{{ b }}>\nx\n</{{ b }}>\n</{{ a }}>").size)

    @Test fun unbalancedOpenIsUnclosed() =
        assertEquals(
            listOf(TemplateElement(0, 0, null)),
            AntlersTemplateTags.elements("<{{ tag }}>\nbody"))

    @Test fun plainHtmlAndPlainAntlersAreIgnored() =
        assertTrue(AntlersTemplateTags.elements("<div>{{ x }}</div>\n{{ if y }}{{ /if }}").isEmpty())
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersTemplateTagsTest"`
Expected: FAIL — `AntlersTemplateTags` / `TemplateElement` unresolved.

- [ ] **Step 3: Implement the scanner**

Create `AntlersTemplateTags.kt`:
```kotlin
package com.github.balotias.intellijantlers.formatter

/** A template-named HTML tag `<{{ … }} … >` … `</{{ … }}>`. 0-based lines; [closeLine] null = unclosed. */
data class TemplateElement(val openStartLine: Int, val openEndLine: Int, val closeLine: Int?)

/**
 * Scans raw text for template-named HTML tags. `{{ … }}` regions and HTML attribute quotes are skipped so
 * that a `>` inside an Antlers expression (`{{ x > 0 }}`) or a quoted value (`class="a>b"`) is not mistaken
 * for the tag terminator. Pure / IntelliJ-free; tolerant (unbalanced → unclosed / ignored), never throws.
 */
object AntlersTemplateTags {
    fun elements(text: String): List<TemplateElement> {
        val out = ArrayList<TemplateElement>()
        val open = ArrayDeque<Pair<Int, Int>>()      // (openStartLine, openEndLine) awaiting a close tag
        var i = 0; var line = 0; val n = text.length
        var antlers = 0                              // depth of open `{{`
        var quote: Char? = null
        var openStart = -1                           // line of a `<{{` whose terminator we are seeking, or -1
        while (i < n) {
            val c = text[i]
            if (c == '\n') { line++; i++; continue }
            if (antlers > 0) {
                when {
                    c == '{' && text.getOrNull(i + 1) == '{' -> { antlers++; i += 2 }
                    c == '}' && text.getOrNull(i + 1) == '}' -> { antlers--; i += 2 }
                    else -> i++
                }
                continue
            }
            if (quote != null) { if (c == quote) quote = null; i++; continue }
            when {
                c == '"' || c == '\'' -> { quote = c; i++ }
                c == '{' && text.getOrNull(i + 1) == '{' -> { antlers++; i += 2 }
                openStart >= 0 && c == '/' && text.getOrNull(i + 1) == '>' -> { openStart = -1; i += 2 } // self-closing
                openStart >= 0 && c == '>' -> { open.addLast(openStart to line); openStart = -1; i++ }
                openStart >= 0 -> i++                                                          // scanning open tag
                c == '<' && text.getOrNull(i + 1) == '/' &&
                    text.getOrNull(i + 2) == '{' && text.getOrNull(i + 3) == '{' -> {          // </{{ close tag
                    if (open.isNotEmpty()) { val (os, oe) = open.removeLast(); out.add(TemplateElement(os, oe, line)) }
                    i += 4; antlers++                                                          // enter its {{ … }}
                }
                c == '<' && text.getOrNull(i + 1) == '{' && text.getOrNull(i + 2) == '{' -> {  // <{{ open tag
                    openStart = line; i += 3; antlers++                                        // enter its {{ … }}
                }
                else -> i++
            }
        }
        while (open.isNotEmpty()) { val (os, oe) = open.removeLast(); out.add(TemplateElement(os, oe, null)) }
        return out
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersTemplateTagsTest"`
Expected: PASS (8 tests). If `nestedTemplateTags` or `gtInsideQuoteIsNotTagEnd` fails, check the `when` order (the `openStart >= 0` arms must precede the `<{{`/`</{{` arms so a stray `<`/`>` while scanning an open tag is consumed, and the quote/antlers skips come first).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersTemplateTags.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersTemplateTagsTest.kt
git commit -m "feat(formatter): pure scanner for template-named HTML tags"
```

---

### Task 2: Add template depth to `AntlersBlockIndentProcessor`

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentProcessor.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `AntlersBlockIndentTest.kt`:
```kotlin
    fun testTemplateTagAttributesAndBodyIndent() {
        val out = reformat("<{{ html_tag }}\n{{ x | attribute:y }}\n>\nbody\n</{{ html_tag }}>")
        val u = unit()
        assertEquals(
            "<{{ html_tag }}\n${u}{{ x | attribute:y }}\n>\n${u}body\n</{{ html_tag }}>",
            out
        )
    }

    fun testIfInsideTemplateBodyCompounds() {
        val out = reformat("<{{ html_tag }}\n>\n{{ if a }}\n{{ b }}\n{{ /if }}\n</{{ html_tag }}>")
        val u = unit()
        assertEquals(
            "<{{ html_tag }}\n>\n${u}{{ if a }}\n${u}${u}{{ b }}\n${u}{{ /if }}\n</{{ html_tag }}>",
            out
        )
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersBlockIndentTest"`
Expected: FAIL — template attribute/body lines are not indented yet (no template depth).

- [ ] **Step 3: Wire in the template depth**

In `AntlersBlockIndentProcessor.kt`, after the `stringSpans` block (the `val stringSpans = …` collection),
add the template-depth computation:
```kotlin
        // Template-named HTML tags (`<{{ … }}>` … `</{{ … }}>`): invisible to the HTML parser, so contribute
        // their attribute/body indent here. Attribute lines (between `<{{` and its `>`) and body lines
        // (between `>` and `</{{`) get +1; boundary lines (`<{{`, the `>`, `</{{`) stay at the element level.
        val templateDepth = IntArray(lineCount)
        val templateOwned = BooleanArray(lineCount)
        for (e in AntlersTemplateTags.elements(document.text)) {
            val end = e.closeLine ?: (lineCount - 1)
            for (l in e.openStartLine..end) if (l in 0 until lineCount) templateOwned[l] = true
            for (l in (e.openStartLine + 1) until (e.closeLine ?: lineCount)) {
                if (l != e.openEndLine && l in 0 until lineCount) templateDepth[l]++
            }
        }
```
Change the per-line gate from:
```kotlin
            if (continuation[line] || !touched[line]) continue         // multiline-owned, or top-level (leave it)
```
to:
```kotlin
            if (continuation[line]) continue                            // multiline-owned
            if (!touched[line] && !templateOwned[line]) continue        // top-level (leave it)
```
Change `val d = depth[line]` to:
```kotlin
            val c = depth[line] + templateDepth[line]
```
Change the `isHtmlTag` computation to exclude template close tags (`</{{`), which must indent absolutely:
```kotlin
            val next = lineText.getOrNull(firstNonWs + 1)
            val isHtmlTag = lineText[firstNonWs] == '<' && next != null &&
                (next.isLetter() || (next == '/' && lineText.getOrNull(firstNonWs + 2)?.isLetter() == true))
```
Change the `target` line to use `c`:
```kotlin
            val target = if (isHtmlTag) currentIndent + unit.repeat(c) else base + unit.repeat(c)
```
(`<{{ …` has next char `{` → not `isHtmlTag` → absolute; `</{{ …` now also not `isHtmlTag` → absolute; real `<div>`/`</div>` stay `isHtmlTag` → additive, unchanged.)

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersBlockIndentTest"`
Expected: PASS (the whole class). Existing block-indent tests are unaffected — `templateDepth` is 0 everywhere there are no template-named tags, and `templateOwned` only adds lines that the old `touched` gate skipped.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentProcessor.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt
git commit -m "feat(formatter): indent template-named HTML tag attributes and body"
```

---

### Task 3: Golden (combined), idempotency, regression, gate

**Files:**
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt`

- [ ] **Step 1: Add a combined golden + idempotency test**

Append to `AntlersBlockIndentTest.kt` (this exercises template attributes + the bracket-nested array +
an `if`/`elseif` attribute block + a real `<span>` in the body — the heart of the user's component):
```kotlin
    fun testTemplateTagGoldenCombined() {
        val src = "<{{ html_tag }}\n" +
            "{{\n[\n'btn' => view:size == 'md',\nview:class\n] | classes | attribute:class\n}}\n" +
            "{{ if html_tag === 'button' }}\n{{ view:type | attribute:type }}\n" +
            "{{ elseif html_tag === 'a' }}\n{{ href | attribute:href }}\n{{ /if }}\n" +
            ">\n" +
            "{{ if view:icon }}\n<span>{{ view:icon }}</span>\n{{ /if }}\n" +
            "</{{ html_tag }}>"
        val u = unit()
        val expected = "<{{ html_tag }}\n" +
            "${u}{{\n${u}${u}[\n${u}${u}${u}'btn' => view:size == 'md',\n${u}${u}${u}view:class\n${u}${u}] | classes | attribute:class\n${u}}}\n" +
            "${u}{{ if html_tag === 'button' }}\n${u}${u}{{ view:type | attribute:type }}\n" +
            "${u}{{ elseif html_tag === 'a' }}\n${u}${u}{{ href | attribute:href }}\n${u}{{ /if }}\n" +
            ">\n" +
            "${u}{{ if view:icon }}\n${u}${u}<span>{{ view:icon }}</span>\n${u}{{ /if }}\n" +
            "</{{ html_tag }}>"
        assertEquals(expected, reformat(src))
    }

    fun testTemplateTagIdempotent() {
        val once = reformat("<{{ html_tag }}\n{{ x | attribute:y }}\n>\n{{ if a }}\n{{ b }}\n{{ /if }}\n</{{ html_tag }}>")
        assertEquals("template-tag reformat is a fixed point", once, reformat(once))
    }
```
NOTE on the `<span>` line: it is `<span>…</span>` on one line (real HTML, same-line open+close → real-HTML
depth 0), inside `{{ if view:icon }}` (antlers +1) inside the template body (+1) → `${u}${u}` (2 levels).
If the real-HTML additive contributes a different base for this self-contained span, adjust ONLY that
line's expected indent to the actual (it must be ≥ `${u}${u}` and idempotent); the template/if/array lines
are exact.

- [ ] **Step 2: Run the tests**

Run: `./gradlew --rerun-tasks test --tests "*AntlersBlockIndentTest"`
Expected: PASS. If the golden's `<span>` line differs, reconcile per the note (it's the one real-HTML-additive line).

- [ ] **Step 3: Regression — full formatter suite**

Run: `./gradlew --rerun-tasks test --tests "*AntlersHtmlFormatTest" --tests "*AntlersMultilineFormatTest" --tests "*AntlersBlockIndentTest" --tests "*AntlersSpacingFormatterTest" --tests "*AntlersFormatterOptOutTest"`
Expected: all PASS. No existing fixture uses template-named tags, and `templateDepth`/`templateOwned` are
zero/empty without them, so existing behavior is unchanged. If any pre-existing fixture differs, report it.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersBlockIndentTest.kt
git commit -m "test(formatter): template-tag combined golden + idempotency"
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
Expected: previous total + ~12 new, zero failures/errors.

If green, proceed to whole-feature review and `superpowers:finishing-a-development-branch`.

---

## Notes for the implementer

- The scanner is the correctness surface; debug it against `AntlersTemplateTagsTest`, do NOT weaken the tests.
- The `>` of a multi-line open tag is `openEndLine` and is EXCLUDED from `templateDepth` (it aligns with `<{{`).
- `<{{ …` and `</{{ …` lines indent ABSOLUTELY (not additively) — the refined `isHtmlTag` excludes them; real `<div>`/`</div>` stay additive (unchanged).
- Combined depth is set absolutely → idempotent; `testTemplateTagIdempotent` pins it.
- Run the **full suite with `--rerun-tasks`** and gate on the result XML.
