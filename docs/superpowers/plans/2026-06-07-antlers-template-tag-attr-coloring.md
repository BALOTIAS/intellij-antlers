# Template-Named-Tag Attribute Coloring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Color HTML attributes (names + values) inside template-named `<{{ … }}>` tags, which the layered lexer leaves as plain text.

**Architecture:** A pure text scanner (`AntlersTemplateTagAttributes.parts`) finds the attribute name/value ranges of `<{{ }}>` open tags (skipping `{{ }}` and quotes, splitting values around interpolations). A thin `language="Antlers"` annotator overlays HTML's own color keys (`HTML_ATTRIBUTE_NAME` / `HTML_ATTRIBUTE_VALUE`) on those ranges. No change to the lexer/highlighter pipeline; normal tags are untouched.

**Tech Stack:** Kotlin, IntelliJ Platform SDK; tests via JUnit (pure) + `BasePlatformTestCase` (`doHighlighting`). Build/test: `./gradlew --rerun-tasks test`.

**Spec:** `docs/superpowers/specs/2026-06-07-antlers-template-tag-attr-coloring-design.md`

**Conventions:** Work on a feature branch off `main` (subagent-driven-development creates one). Gate the suite with `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` printing nothing. Commit trailer:
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```
Build note: `--rerun-tasks` is REQUIRED (Gradle caches). Avoid bare shell globs that error on no match (use `if grep …; then … else … fi`).

---

## File Structure

- **New** `editor/AntlersTemplateTagAttributes.kt` — pure scanner: `AttrPart(start, end, kind)` + `parts(text)`.
- **New** `editor/AntlersTemplateTagAttributeAnnotator.kt` — `Annotator` overlaying HTML keys.
- **Edit** `META-INF/plugin.xml` — register the annotator.
- **New tests** `editor/AntlersTemplateTagAttributesTest.kt` (pure), `highlighting/AntlersTemplateTagAttrHighlightTest.kt` (`doHighlighting`).
- **Edit** `README.md` — narrow the ⑤ known-limitation note (attributes now colored).

---

## Task 1: `AntlersTemplateTagAttributes` (pure scanner)

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributes.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributesTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.editor.AntlersTemplateTagAttributes.AttrPart
import com.github.balotias.intellijantlers.editor.AntlersTemplateTagAttributes.Kind.NAME
import com.github.balotias.intellijantlers.editor.AntlersTemplateTagAttributes.Kind.VALUE
import org.junit.Assert.assertEquals
import org.junit.Test

class AntlersTemplateTagAttributesTest {

    private fun parts(t: String) = AntlersTemplateTagAttributes.parts(t)

    @Test fun singleAttribute() =
        assertEquals(
            listOf(AttrPart(10, 15, NAME), AttrPart(16, 19, VALUE)),
            parts("<{{ as }} class=\"x\">")
        )

    @Test fun valueSplitAroundInterpolation() {
        // <{{ as }} class="a {{ x }} b">  → VALUE `"a `, (skip {{ x }}), VALUE ` b"`
        val t = "<{{ as }} class=\"a {{ x }} b\">"
        assertEquals(
            listOf(AttrPart(10, 15, NAME), AttrPart(16, 19, VALUE), AttrPart(26, 29, VALUE)),
            parts(t)
        )
    }

    @Test fun multiLineValue() {
        val t = "<{{ as }} class=\"\nprose\n\">"
        // names/values present; the VALUE range spans the newlines from the opening to closing quote.
        val ps = parts(t)
        assertEquals(NAME, ps[0].kind)
        assertEquals("class", t.substring(ps[0].start, ps[0].end))
        assertEquals(VALUE, ps[1].kind)
        assertEquals("\"\nprose\n\"", t.substring(ps[1].start, ps[1].end))
    }

    @Test fun booleanAttributeHasNameOnly() {
        val ps = parts("<{{ as }} hidden>")
        assertEquals(1, ps.size)
        assertEquals(NAME, ps[0].kind)
        assertEquals("hidden", "<{{ as }} hidden>".substring(ps[0].start, ps[0].end))
    }

    @Test fun prefixedAndDashedNames() {
        val t = "<{{ as }} x-ref=\"f\" :href=\"u\" @click=\"go\">"
        val names = parts(t).filter { it.kind == NAME }.map { t.substring(it.start, it.end) }
        assertEquals(listOf("x-ref", ":href", "@click"), names)
    }

    @Test fun singleQuotes() {
        val ps = parts("<{{ as }} class='x'>")
        assertEquals(VALUE, ps[1].kind)
        assertEquals("'x'", "<{{ as }} class='x'>".substring(ps[1].start, ps[1].end))
    }

    @Test fun gtInsideValueDoesNotEndTag() {
        // the `>` inside the quoted value must not terminate the tag; `data-x` still parses
        val t = "<{{ as }} title=\"a > b\" id=\"y\">"
        val names = parts(t).filter { it.kind == NAME }.map { t.substring(it.start, it.end) }
        assertEquals(listOf("title", "id"), names)
    }

    @Test fun gtInsideExpressionDoesNotEndTag() {
        val t = "<{{ if x > 0 }} class=\"y\">"
        val names = parts(t).filter { it.kind == NAME }.map { t.substring(it.start, it.end) }
        assertEquals(listOf("class"), names)
    }

    @Test fun normalTagYieldsNothing() {
        assertEquals(emptyList<AttrPart>(), parts("<div class=\"x\">text</div>"))
    }

    @Test fun closingTemplateTagYieldsNothing() {
        assertEquals(emptyList<AttrPart>(), parts("</{{ as }}>"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.editor.AntlersTemplateTagAttributesTest"`
Expected: FAIL to compile (`AntlersTemplateTagAttributes` unresolved).

- [ ] **Step 3: Write the implementation**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributes.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

/**
 * Pure scanner for the attribute name/value ranges of template-named `<{{ … }}>` open tags. The editor's
 * layered HTML highlighter can't color these (it re-lexes each outer-HTML chunk independently, so the part
 * after `}}` is plain text), so [AntlersTemplateTagAttributeAnnotator] overlays HTML colors onto the ranges
 * this returns. Only `<{{ }}>` tags are scanned — normal `<div>` tags already color via the lexer.
 *
 * IntelliJ-free and tolerant: skips `{{ … }}` regions and quoted strings while finding the tag's closing
 * `>` (so a `>` inside an expression or value doesn't end the tag), and splits quoted values around any
 * `{{ … }}` so interpolations stay Antlers-colored. Never throws.
 */
object AntlersTemplateTagAttributes {

    enum class Kind { NAME, VALUE }

    /** Half-open `[start, end)` offsets into the scanned text. */
    data class AttrPart(val start: Int, val end: Int, val kind: Kind)

    fun parts(text: String): List<AttrPart> {
        val out = ArrayList<AttrPart>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (text[i] == '<' && text.getOrNull(i + 1) == '{' && text.getOrNull(i + 2) == '{') {
                i = scanOpenTag(text, i, out)
            } else {
                i++
            }
        }
        return out
    }

    /** Scan one `<{{ … }}` open tag from [start] (`<`); returns the index after its closing `>` (or EOF). */
    private fun scanOpenTag(text: String, start: Int, out: MutableList<AttrPart>): Int {
        val n = text.length
        var i = skipInterpolation(text, start + 1)   // skip the `{{ … }}` tag name
        while (i < n) {
            val c = text[i]
            when {
                c == '>' -> return i + 1
                c == '/' && text.getOrNull(i + 1) == '>' -> return i + 2
                c == '{' && text.getOrNull(i + 1) == '{' -> i = skipInterpolation(text, i)
                c == ' ' || c == '\t' || c == '\n' || c == '\r' -> i++
                isNameChar(c) -> {
                    val ns = i
                    while (i < n && isNameChar(text[i])) i++
                    out.add(AttrPart(ns, i, Kind.NAME))
                    var j = i
                    while (j < n && isSpace(text[j])) j++
                    if (j < n && text[j] == '=') {
                        j++
                        while (j < n && isSpace(text[j])) j++
                        i = scanValue(text, j, out)
                    } else {
                        i = j
                    }
                }
                else -> i++
            }
        }
        return n
    }

    /** From a `{{` at [at], return the index after the matching `}}` (string-aware); EOF if unterminated. */
    private fun skipInterpolation(text: String, at: Int): Int {
        val n = text.length
        var i = at + 2
        var quote: Char? = null
        while (i < n) {
            val c = text[i]
            when {
                quote != null -> { if (c == quote) quote = null; i++ }
                c == '"' || c == '\'' -> { quote = c; i++ }
                c == '}' && text.getOrNull(i + 1) == '}' -> return i + 2
                else -> i++
            }
        }
        return n
    }

    /** Emit VALUE part(s) for the value at [at]; returns the index after the value. */
    private fun scanValue(text: String, at: Int, out: MutableList<AttrPart>): Int {
        val n = text.length
        val q = text.getOrNull(at) ?: return at
        if (q == '"' || q == '\'') {
            var segStart = at                         // opening quote starts the first segment
            var i = at + 1
            while (i < n) {
                val c = text[i]
                when {
                    c == '{' && text.getOrNull(i + 1) == '{' -> {
                        if (i > segStart) out.add(AttrPart(segStart, i, Kind.VALUE))
                        i = skipInterpolation(text, i)
                        segStart = i
                    }
                    c == q -> {
                        if (i + 1 > segStart) out.add(AttrPart(segStart, i + 1, Kind.VALUE))  // include closing quote
                        return i + 1
                    }
                    else -> i++
                }
            }
            if (n > segStart) out.add(AttrPart(segStart, n, Kind.VALUE))   // unterminated quote
            return n
        }
        // Unquoted value: run until whitespace / `>` / `{{`.
        var i = at
        while (i < n) {
            val c = text[i]
            if (isSpace(c) || c == '>') break
            if (c == '{' && text.getOrNull(i + 1) == '{') break
            i++
        }
        if (i > at) out.add(AttrPart(at, i, Kind.VALUE))
        return i
    }

    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    /** HTML attribute-name chars, incl. framework prefixes/separators (`x-ref`, `attr:class`, `@click`, `:href`). */
    private fun isNameChar(c: Char) =
        c.isLetterOrDigit() || c == '-' || c == '_' || c == ':' || c == '.' || c == '@'
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.editor.AntlersTemplateTagAttributesTest"`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*AntlersTemplateTagAttributesTest*.xml || echo CLEAN`
Expected: PASS (10 tests), CLEAN. If a range assertion is off, fix the SCANNER to match the spec rule (don't loosen the test).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributes.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributesTest.kt
git commit -m "feat(highlighting): scan attribute ranges of template-named <{{ }}> tags

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: annotator + registration + highlighting test + README

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributeAnnotator.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersTemplateTagAttrHighlightTest.kt`
- Edit: `src/main/resources/META-INF/plugin.xml`
- Edit: `README.md`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersTemplateTagAttrHighlightTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.highlighting

import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersTemplateTagAttrHighlightTest : BasePlatformTestCase() {

    /** Keys our annotator forced over a piece of text whose content contains [needle]. */
    private fun forcedKeysOver(text: String, needle: String): List<TextAttributesKey> {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .filter { it.forcedTextAttributesKey != null && it.text?.contains(needle) == true }
            .map { it.forcedTextAttributesKey!! }
    }

    fun testTemplateTagAttributeValueColored() {
        val keys = forcedKeysOver("<{{ as }} class=\"prose\">x</{{ as }}>", "prose")
        assertTrue("value carries HTML_ATTRIBUTE_VALUE: $keys",
            keys.contains(XmlHighlighterColors.HTML_ATTRIBUTE_VALUE))
    }

    fun testTemplateTagAttributeNameColored() {
        val keys = forcedKeysOver("<{{ as }} class=\"prose\">x</{{ as }}>", "class")
        assertTrue("name carries HTML_ATTRIBUTE_NAME: $keys",
            keys.contains(XmlHighlighterColors.HTML_ATTRIBUTE_NAME))
    }

    fun testNormalDivNotAnnotatedByUs() {
        // A normal <div> is colored by the lexer, not by our annotator: no FORCED key over its value.
        myFixture.configureByText("p.antlers.html", "<div class=\"prose\">x</div>")
        val forced = myFixture.doHighlighting()
            .any { it.forcedTextAttributesKey == XmlHighlighterColors.HTML_ATTRIBUTE_VALUE && it.text?.contains("prose") == true }
        assertFalse("our annotator must not touch normal <div> tags", forced)
    }

    fun testInterpolationInsideValueNotAttributeColored() {
        // Distinct token `slot` (≠ the attribute name) so a match can only come from the interpolation.
        myFixture.configureByText("p.antlers.html", "<{{ as }} class=\"{{ slot }}\">x</{{ as }}>")
        val valueOverSlot = myFixture.doHighlighting().any {
            it.forcedTextAttributesKey == XmlHighlighterColors.HTML_ATTRIBUTE_VALUE && it.text?.contains("slot") == true
        }
        assertFalse("the {{ slot }} interpolation must not be HTML_ATTRIBUTE_VALUE-colored", valueOverSlot)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.highlighting.AntlersTemplateTagAttrHighlightTest"`
Expected: FAIL — `testTemplateTagAttributeValueColored` / `…NameColored` fail (no annotator yet); the negative tests may pass already.

- [ ] **Step 3: Write the annotator**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributeAnnotator.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.parser.AntlersFile
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Overlays HTML attribute colors onto the attribute names/values of template-named `<{{ … }}>` tags, which
 * the layered lexer leaves as plain text (the chunk after `}}` is re-lexed without its in-tag context).
 * Runs once on the file root; tolerant. The `{{ }}` interpolations are left to the base highlighter.
 */
class AntlersTemplateTagAttributeAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is AntlersFile) return            // run once, on the Antlers file root
        if (element.project.isDefault) return
        for (part in AntlersTemplateTagAttributes.parts(element.text)) {
            val key = when (part.kind) {
                AntlersTemplateTagAttributes.Kind.NAME -> XmlHighlighterColors.HTML_ATTRIBUTE_NAME
                AntlersTemplateTagAttributes.Kind.VALUE -> XmlHighlighterColors.HTML_ATTRIBUTE_VALUE
            }
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(part.start, part.end))
                .textAttributes(key)
                .create()
        }
    }
}
```

- [ ] **Step 4: Register the annotator**

In `src/main/resources/META-INF/plugin.xml`, after the `AntlersHintAnnotator` line, add:

```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersTemplateTagAttributeAnnotator"/>
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.highlighting.AntlersTemplateTagAttrHighlightTest"`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*AntlersTemplateTagAttrHighlightTest*.xml || echo CLEAN`
Expected: PASS (4 tests), CLEAN.

- [ ] **Step 6: Update README**

In `README.md`, the ⑤ known-limitation bullet currently says the HTML attribute/value coloring on an interpolated-tag-name element "can be lost." Update it to reflect that attribute name/value coloring is now restored (via the annotator), narrowing the remaining caveat to non-attribute HTML coloring inside such elements. Read the bullet first, then edit it to:

```markdown
- When an **HTML tag name is itself an Antlers interpolation** (`<{{ as or 'h2' }}> … </{{ as or 'h2' }}>`),
  the IDE colors HTML by re-lexing each outer-HTML segment independently, so the segment after `}}` lacks
  its in-tag context. Attribute **names and values** on such tags are re-colored to match normal tags, and
  the spurious "Closing tag matches nothing" error is suppressed; any other HTML coloring nuance inside that
  specific element may still differ.
```

- [ ] **Step 7: Full-suite gate + commit**

Run: `./gradlew --rerun-tasks test`
Then: `if grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml 2>/dev/null; then echo FAIL; else echo CLEAN; fi`
Expected: BUILD SUCCESSFUL, CLEAN.

```bash
git add -A
git commit -m "feat(highlighting): color attributes inside template-named <{{ }}> tags

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Definition of Done

- `<{{ as }} class="prose">` colors `class` (name) and `"prose"` (value) with HTML's attribute keys.
- Multi-line values and framework-prefixed names (`x-ref`, `:href`, `@click`, `attr:class`) are covered.
- `{{ }}` interpolations inside values stay Antlers-colored (not HTML-value-colored).
- Normal `<div>` tags are untouched by the annotator.
- Full suite green; README ⑤ note updated.
- **Manual check (cannot be automated):** open a `.antlers.php`/`.antlers.html` with `<{{ as }} class="…">` in PhpStorm and confirm the value renders in the attribute color.
```
