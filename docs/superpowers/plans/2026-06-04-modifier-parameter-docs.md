# Modifier Parameter Docs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Antlers modifiers real parameter data and surface the signature (`replace(search, replacement)`) and per-parameter docs in quick-docs, completion, and a Ctrl+P parameter-info popup.

**Architecture:** Add a `ModifierParam` model and a `parameters` list to `ModifierDef`; author parameter data for all 82 arg-taking bundled modifiers; render a single source-of-truth signature via a pure `ModifierSignature` helper consumed by the documentation provider, the completion lookup/insert handler, and a new `ParameterInfoHandler`. Everything works on real modifier-chain PSI (`{{ x | replace('a','b') }}`); string interpolation is out of scope (no PSI).

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`AbstractDocumentationProvider`, `InsertHandler`, `TemplateManager`, `ParameterInfoHandler`), JUnit via `BasePlatformTestCase`. Build/test: `./gradlew --rerun-tasks test` (`--rerun-tasks` REQUIRED — Gradle caches). Gate after each task: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

---

## File Structure

- `catalog/CatalogModels.kt` (modify) — add `ModifierParam`; add `parameters` to `ModifierDef`.
- `catalog/CatalogModifiers.kt` (modify) — author `parameters` for the 82 arg-taking modifiers.
- `catalog/ModifierSignature.kt` (create) — pure signature renderer + per-param label.
- `catalog/ModifierCatalogConsistencyTest.kt` (create, test) — data-sanity gate.
- `documentation/AntlersDocumentationProvider.kt` (modify) — `modifierDoc(def)` rendering.
- `completion/AntlersCompletionProvider.kt` (modify, MODIFIER branch ~line 228) — signature tail text + new insert handler.
- `completion/ModifierInsertHandler.kt` (modify) — tab-stop template for required params.
- `completion/ModifierArgIndex.kt` (create) — pure current-argument-index from caret.
- `completion/AntlersModifierParameterInfoHandler.kt` (create) — Ctrl+P handler.
- `META-INF/plugin.xml` (modify) — register the parameter-info handler.
- Test files alongside, listed per task.

---

### Task 1: `ModifierParam` model + `ModifierDef.parameters`

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModels.kt`

- [ ] **Step 1: Add the model and field**

In `CatalogModels.kt`, add this data class directly below the existing `ParamDef`:

```kotlin
/** One positional argument of a modifier. [default] is shown only when [optional]. */
data class ModifierParam(
    val name: String,
    val description: String = "",
    val optional: Boolean = false,
    val default: String = "",
)
```

Then add a `parameters` field to `ModifierDef`. Change its constructor from:

```kotlin
data class ModifierDef(
    val name: String,
    val description: String = "",
    val docUrl: String = "",
    val takesArguments: Boolean = false,
    val introducedIn: Int? = null,
    val removedIn: Int? = null,
) {
```

to:

```kotlin
data class ModifierDef(
    val name: String,
    val description: String = "",
    val docUrl: String = "",
    val takesArguments: Boolean = false,
    val parameters: List<ModifierParam> = emptyList(),
    val introducedIn: Int? = null,
    val removedIn: Int? = null,
) {
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew --rerun-tasks compileKotlin`
Expected: BUILD SUCCESSFUL (the new field defaults to `emptyList()`, so existing call sites still compile).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModels.kt
git commit -m "feat(catalog): add ModifierParam model and ModifierDef.parameters"
```

---

### Task 2: `ModifierSignature` pure renderer

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/ModifierSignature.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/ModifierSignatureTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ModifierSignatureTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class ModifierSignatureTest {

    @Test fun noParamsRendersBareName() =
        assertEquals("upper", ModifierSignature.render(ModifierDef(name = "upper")))

    @Test fun requiredParamsRenderPlain() =
        assertEquals("replace(search, replacement)", ModifierSignature.render(
            ModifierDef(name = "replace", takesArguments = true, parameters = listOf(
                ModifierParam("search"), ModifierParam("replacement")))))

    @Test fun optionalWithDefaultRendersAssignment() =
        assertEquals("truncate(length, ellipsis = '…')", ModifierSignature.render(
            ModifierDef(name = "truncate", takesArguments = true, parameters = listOf(
                ModifierParam("length"), ModifierParam("ellipsis", optional = true, default = "'…'")))))

    @Test fun optionalWithoutDefaultRendersQuestionMark() =
        assertEquals("first(count?)", ModifierSignature.render(
            ModifierDef(name = "first", takesArguments = true, parameters = listOf(
                ModifierParam("count", optional = true)))))

    @Test fun paramLabelIsReused() =
        assertEquals("ellipsis = '…'", ModifierSignature.paramLabel(
            ModifierParam("ellipsis", optional = true, default = "'…'")))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*ModifierSignatureTest"`
Expected: FAIL — `ModifierSignature` unresolved (compile error).

- [ ] **Step 3: Implement**

Create `ModifierSignature.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

/** Renders a modifier's positional signature, e.g. `truncate(length, ellipsis = '…')`. Pure / IntelliJ-free. */
object ModifierSignature {

    /** The label for one parameter: `name`, `name = default`, or `name?` (optional, no default). */
    fun paramLabel(p: ModifierParam): String = when {
        !p.optional -> p.name
        p.default.isNotBlank() -> "${p.name} = ${p.default}"
        else -> "${p.name}?"
    }

    /** `name` when there are no params, otherwise `name(p1, p2, …)`. */
    fun render(def: ModifierDef): String =
        if (def.parameters.isEmpty()) def.name
        else "${def.name}(${def.parameters.joinToString(", ") { paramLabel(it) }})"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*ModifierSignatureTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/ModifierSignature.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/catalog/ModifierSignatureTest.kt
git commit -m "feat(catalog): pure ModifierSignature renderer"
```

---

### Task 3: Author parameter data for all 82 arg-taking modifiers

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifiers.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/ModifierCatalogConsistencyTest.kt`

This is data authoring guarded by a completeness test. The 82 modifiers that currently have `takesArguments = true` must each gain a `parameters = listOf(ModifierParam(...))`. Required params first, optional params last with a `default`. Genuinely variadic modifiers (arbitrary list of values) get ONE descriptive parameter. Base each description on the modifier's existing `description` and its page at `https://statamic.dev/modifiers/<name>` (the `docUrl` already on each entry).

- [ ] **Step 1: Write the failing completeness test**

Create `ModifierCatalogConsistencyTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierCatalogConsistencyTest {

    @Test fun everyArgTakingModifierHasParameters() {
        val missing = CatalogModifiers.ALL.filter { it.takesArguments && it.parameters.isEmpty() }.map { it.name }
        assertTrue("modifiers flagged takesArguments but with no ModifierParam data: $missing", missing.isEmpty())
    }

    @Test fun everyModifierWithParametersTakesArguments() {
        val wrong = CatalogModifiers.ALL.filter { it.parameters.isNotEmpty() && !it.takesArguments }.map { it.name }
        assertTrue("modifiers with parameters but takesArguments=false: $wrong", wrong.isEmpty())
    }

    @Test fun optionalParametersSortAfterRequired() {
        val bad = CatalogModifiers.ALL.filter { def ->
            val firstOptional = def.parameters.indexOfFirst { it.optional }
            firstOptional >= 0 && def.parameters.drop(firstOptional).any { !it.optional }
        }.map { it.name }
        assertTrue("required parameters must precede optional ones: $bad", bad.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*ModifierCatalogConsistencyTest"`
Expected: FAIL — `everyArgTakingModifierHasParameters` lists all 82 names.

- [ ] **Step 3: Add `parameters` to each arg-taking entry**

In `CatalogModifiers.kt`, add a `parameters = listOf(...)` argument to each of the 82 `ModifierDef` entries flagged `takesArguments = true`. Use the worked entries below verbatim, and author the remaining entries in the same style (concise lowercase param names; one-line descriptions). The full name list is in the table at the end of this task.

Pattern — replace e.g.:
```kotlin
ModifierDef(name = "replace", description = "Replace a substring.", takesArguments = true, docUrl = "https://statamic.dev/modifiers/replace"),
```
with:
```kotlin
ModifierDef(name = "replace", description = "Replace a substring.", takesArguments = true,
    parameters = listOf(
        ModifierParam("search", "The substring to find."),
        ModifierParam("replacement", "The string to substitute in."),
    ),
    docUrl = "https://statamic.dev/modifiers/replace"),
```

Authoritative worked entries (use these exactly):

```kotlin
// string
ModifierParam list for "truncate":      listOf(ModifierParam("length", "Maximum length to keep."), ModifierParam("ellipsis", "Appended when truncated.", optional = true, default = "'…'"))
"safe_truncate":  listOf(ModifierParam("length", "Maximum length, not splitting words."), ModifierParam("ellipsis", "Appended when truncated.", optional = true, default = "'…'"))
"limit":          listOf(ModifierParam("count", "Maximum number of characters/items to keep."))
"strip_tags":     listOf(ModifierParam("allowed", "Tags to keep, e.g. '<a><strong>'.", optional = true))
"ensure_left":    listOf(ModifierParam("prefix", "Substring the value must start with."))
"ensure_right":   listOf(ModifierParam("suffix", "Substring the value must end with."))
"remove_left":    listOf(ModifierParam("prefix", "Prefix to strip if present."))
"remove_right":   listOf(ModifierParam("suffix", "Suffix to strip if present."))
"backspace":      listOf(ModifierParam("amount", "Number of trailing characters to remove."))
"repeat":         listOf(ModifierParam("times", "How many times to repeat the string."))
"str_pad_left":   listOf(ModifierParam("length", "Target total length."), ModifierParam("pad", "Padding string.", optional = true, default = "' '"))
"pad":            listOf(ModifierParam("size", "Target array size."), ModifierParam("value", "Value to pad with.", optional = true))
"substr":         listOf(ModifierParam("start", "Start index (negative counts from the end)."), ModifierParam("length", "Number of characters to take.", optional = true))
"split":          listOf(ModifierParam("length", "Characters per chunk.", optional = true, default = "1"))
"explode":        listOf(ModifierParam("delimiter", "Boundary string to split on."))
"join":           listOf(ModifierParam("glue", "String placed between items."))
"surround":       listOf(ModifierParam("delimiter", "Wrapped before and after the value."))
"wrap":           listOf(ModifierParam("tag", "HTML tag name to wrap the value in."))
"mark":           listOf(ModifierParam("term", "Text to wrap in <mark> tags."))
"regex_mark":     listOf(ModifierParam("pattern", "Regex whose matches are wrapped in <mark>."))
"regex_replace":  listOf(ModifierParam("pattern", "Regex to match."), ModifierParam("replacement", "Replacement string."))
"count_substring":listOf(ModifierParam("needle", "Substring to count."))
"to_spaces":      listOf(ModifierParam("width", "Spaces per tab.", optional = true, default = "4"))
"to_tabs":        listOf(ModifierParam("width", "Spaces per tab.", optional = true, default = "4"))
"excerpt":        listOf(ModifierParam("start", "Marker for the excerpt start.", optional = true), ModifierParam("end", "Marker for the excerpt end.", optional = true))

// numbers
"add":            listOf(ModifierParam("value", "Number to add."))
"subtract":       listOf(ModifierParam("value", "Number to subtract."))
"multiply":       listOf(ModifierParam("value", "Number to multiply by."))
"divide":         listOf(ModifierParam("value", "Number to divide by."))
"mod":            listOf(ModifierParam("value", "Divisor for the modulo."))
"round":          listOf(ModifierParam("precision", "Decimal places.", optional = true, default = "0"))
"format_number":  listOf(ModifierParam("precision", "Decimal places.", optional = true, default = "0"))

// dates
"format":         listOf(ModifierParam("format", "PHP/Carbon date format string."))
"format_localized": listOf(ModifierParam("format", "strftime-style locale format string."))
"format_translated":listOf(ModifierParam("format", "Date format with translated month/day names."))
"relative":       listOf(ModifierParam("from", "Date to compare against.", optional = true))
"modify_date":    listOf(ModifierParam("modifier", "Relative string, e.g. '+1 day'."))
"is_after":       listOf(ModifierParam("date", "Date the value must be after."))
"is_before":      listOf(ModifierParam("date", "Date the value must be before."))
"is_between":     listOf(ModifierParam("start", "Range start date."), ModifierParam("end", "Range end date."))
"hours_ago":      listOf(ModifierParam("date", "Date to measure from.", optional = true))
"timezone":       listOf(ModifierParam("timezone", "Target timezone, e.g. 'Europe/Berlin'."))
"read_time":      listOf(ModifierParam("wpm", "Words read per minute.", optional = true, default = "200"))

// arrays / collections
"first":          listOf(ModifierParam("count", "Number of items to take.", optional = true))
"last":           listOf(ModifierParam("count", "Number of items to take.", optional = true))
"at":             listOf(ModifierParam("index", "Zero-based index of the item to return."))
"get":            listOf(ModifierParam("key", "Key whose value to return."), ModifierParam("default", "Fallback when the key is missing.", optional = true))
"offset":         listOf(ModifierParam("count", "Number of leading items to skip."))
"chunk":          listOf(ModifierParam("size", "Items per chunk."))
"sort":           listOf(ModifierParam("key", "Property to sort by; append ':desc' to reverse.", optional = true))
"where":          listOf(ModifierParam("key", "Item property to test."), ModifierParam("value", "Value the property must equal."))
"where_in":       listOf(ModifierParam("key", "Item property to test."), ModifierParam("values", "List of allowed values."))
"pluck":          listOf(ModifierParam("key", "Property to pull from each item."))
"select":         listOf(ModifierParam("keys", "One or more properties to keep."))
"group_by":       listOf(ModifierParam("key", "Property to group items by."))
"key_by":         listOf(ModifierParam("key", "Property to use as each item's key."))
"in_array":       listOf(ModifierParam("value", "Value to search for in the array."))
"contains":       listOf(ModifierParam("value", "Value to look for."))
"contains_all":   listOf(ModifierParam("values", "All values that must be present."))
"contains_any":   listOf(ModifierParam("values", "Values, any of which may be present."))
"overlaps":       listOf(ModifierParam("values", "Other array to intersect with."))
"doesnt_overlap": listOf(ModifierParam("values", "Other array that must share nothing."))
"insert":         listOf(ModifierParam("value", "Value to insert."), ModifierParam("index", "Position to insert at."))
"default":        listOf(ModifierParam("fallback", "Value to use when the input is empty."))
"as":             listOf(ModifierParam("alias", "Variable name to expose the value under."))

// url / html
"url":            listOf(ModifierParam("key", "Asset/relation key to resolve.", optional = true))
"link":           listOf(ModifierParam("text", "Anchor text.", optional = true), ModifierParam("title", "title attribute.", optional = true))
"image":          listOf(ModifierParam("alt", "alt text for the <img>.", optional = true))
"favicon":        listOf(ModifierParam("rel", "rel attribute.", optional = true, default = "'icon'"))
"gravatar":       listOf(ModifierParam("size", "Pixel size of the avatar.", optional = true))
"segment":        listOf(ModifierParam("index", "1-based URL segment to return."))
"parse_url":      listOf(ModifierParam("component", "Specific URL part to return.", optional = true))
"pathinfo":       listOf(ModifierParam("component", "Specific path part to return.", optional = true))
"attribute":      listOf(ModifierParam("name", "HTML attribute to extract."))
"background_position": listOf(ModifierParam("default", "Fallback position.", optional = true))
"full_urls":      listOf(ModifierParam("base", "Base URL to prepend.", optional = true))

// misc / callable
"classes":        listOf(ModifierParam("classes", "Class names or conditional [class => bool] maps."))
"contains" /* already above */
"macro":          listOf(ModifierParam("name", "Macro to invoke."))
"piped":          listOf(ModifierParam("callable", "Callable/modifier name to pipe through."))
"partial":        listOf(ModifierParam("name", "Partial template to render."))
"scope":          listOf(ModifierParam("name", "Query scope to apply."))
```

For any of the 82 not spelled out above (none should remain — the list below is the complete set; cross off each as you add it), follow the same conventions. Do NOT change `description`/`docUrl`/`takesArguments` on any entry.

Complete name list to cover (82): truncate, limit, strip_tags, format, relative, first, last, sort, where, pluck, join, explode, replace, url, ensure_left, ensure_right, format_localized, default, add, as, at, attribute, background_position, backspace, chunk, classes, contains, contains_all, contains_any, count_substring, doesnt_overlap, divide, excerpt, favicon, format_number, format_translated, full_urls, get, gravatar, group_by, hours_ago, image, in_array, insert, is_after, is_before, is_between, key_by, link, macro, mark, mod, modify_date, multiply, offset, overlaps, pad, parse_url, partial, pathinfo, piped, read_time, regex_mark, regex_replace, remove_left, remove_right, repeat, round, safe_truncate, scope, segment, select, split, str_pad_left, substr, subtract, surround, timezone, to_spaces, to_tabs, where_in, wrap.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*ModifierCatalogConsistencyTest"`
Expected: PASS (3 tests) — `missing` is empty.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifiers.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/catalog/ModifierCatalogConsistencyTest.kt
git commit -m "feat(catalog): author parameters for all 82 arg-taking modifiers"
```

---

### Task 4: Quick-docs renders the signature + parameters

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersModifierDocTest.kt`

- [ ] **Step 1: Write the failing test**

Create `AntlersModifierDocTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersModifierDocTest : BasePlatformTestCase() {

    /** Quick-doc HTML for the modifier identifier in [text] (caret marked with <caret>). */
    private fun docFor(text: String): String? {
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val caret = text.indexOf("<caret>")
        val el = myFixture.file.findElementAt(caret)!!
        val provider = AntlersDocumentationProvider()
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, el, caret) ?: el
        return provider.generateDoc(target, el)
    }

    fun testSignatureInTitle() {
        val doc = docFor("{{ title | rep<caret>lace('a', 'b') }}")!!
        assertTrue("signature present, got: $doc", doc.contains("replace(search, replacement)"))
    }

    fun testParameterDescriptionsListed() {
        val doc = docFor("{{ title | rep<caret>lace('a', 'b') }}")!!
        assertTrue("search param documented", doc.contains("search") && doc.contains("The substring to find."))
        assertTrue("replacement param documented", doc.contains("replacement"))
    }

    fun testNoParamModifierStillDocuments() {
        val doc = docFor("{{ title | up<caret>per }}")!!
        assertTrue("name shown", doc.contains("upper"))
        assertFalse("no Parameters block for a no-arg modifier", doc.contains("<b>Parameters</b>"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersModifierDocTest"`
Expected: FAIL — `testSignatureInTitle` / `testParameterDescriptionsListed` fail (current doc shows only the bare name).

- [ ] **Step 3: Implement `modifierDoc` and use it**

In `AntlersDocumentationProvider.kt`, add these imports near the other `catalog` imports:

```kotlin
import com.github.balotias.intellijantlers.catalog.ModifierDef
import com.github.balotias.intellijantlers.catalog.ModifierSignature
```

Replace the existing modifier branch body. Change:

```kotlin
        PsiTreeUtil.getParentOfType(ident, AntlersModifierMixin::class.java)?.let { mod ->
            if (mod.modifierName == name) {
                val def = catalog.modifiers().firstOrNull { it.name == name } ?: return null
                return section(
                    "Antlers modifier <b>${esc(name)}</b>",
                    def.description,
                    def.docUrl
                )
            }
        }
```

to:

```kotlin
        PsiTreeUtil.getParentOfType(ident, AntlersModifierMixin::class.java)?.let { mod ->
            if (mod.modifierName == name) {
                val def = catalog.modifiers().firstOrNull { it.name == name } ?: return null
                return modifierDoc(def)
            }
        }
```

Add this method next to `tagDoc` (mirror its structure):

```kotlin
    private fun modifierDoc(def: ModifierDef): String {
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("Antlers modifier <b>${esc(ModifierSignature.render(def))}</b>")
        sb.append(DocumentationMarkup.DEFINITION_END)
        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append(esc(def.description))
        if (def.parameters.isNotEmpty()) {
            sb.append("<br/><br/><b>Parameters</b><br/>")
            for (p in def.parameters) {
                val opt = when {
                    !p.optional -> ""
                    p.default.isNotBlank() -> " <i>(optional, default: ${esc(p.default)})</i>"
                    else -> " <i>(optional)</i>"
                }
                sb.append("<code>${esc(p.name)}</code> — ${esc(p.description)}$opt<br/>")
            }
        }
        sb.append(DocumentationMarkup.CONTENT_END)
        appendDocUrl(sb, def.docUrl)
        return sb.toString()
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersModifierDocTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersModifierDocTest.kt
git commit -m "feat(docs): modifier quick-docs show signature and parameter descriptions"
```

---

### Task 5: Completion shows the signature and inserts a tab-stop template

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/ModifierInsertHandler.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt` (MODIFIER branch, ~line 228)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/ModifierCompletionParamTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ModifierCompletionParamTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModifierCompletionParamTest : BasePlatformTestCase() {

    fun testRequiredParamsInsertedAsTemplate() {
        myFixture.configureByText("p.antlers.html", "{{ title | replac<caret> }}")
        myFixture.completeBasic()
        // single match → auto-inserted; template default values are the parameter names.
        assertEquals("{{ title | replace(search, replacement) }}", myFixture.editor.document.text)
    }

    fun testNoArgModifierInsertsNoParens() {
        myFixture.configureByText("p.antlers.html", "{{ title | uppe<caret> }}")
        myFixture.completeBasic()
        assertEquals("{{ title | upper }}", myFixture.editor.document.text)
    }

    fun testSignatureShownAsTailText() {
        myFixture.configureByText("p.antlers.html", "{{ title | replac<caret> }}")
        val tails = myFixture.completeBasic()?.mapNotNull { le ->
            val p = com.intellij.codeInsight.lookup.LookupElementPresentation()
            le.renderElement(p); p.tailText
        } ?: emptyList()
        assertTrue("a lookup tail shows the signature args, got $tails",
            tails.any { it != null && it.contains("(search, replacement)") })
    }
}
```

Note: `completeBasic()` returns null when it auto-inserts a single match (Tasks 1/2). For `testSignatureShownAsTailText`, if `replace` is the sole match it may auto-insert; if the assertion list is empty, change the prefix to one with multiple matches (e.g. `re<caret>`) and assert the same `contains` over those tails.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*ModifierCompletionParamTest"`
Expected: FAIL — current handler inserts `replace()` (empty parens), not the template; tail text shows the description, not the signature.

- [ ] **Step 3: Rewrite `ModifierInsertHandler` to build a template**

Replace the entire contents of `ModifierInsertHandler.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.catalog.ModifierDef
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression

/**
 * Inserts a modifier. With required parameters it starts a live template `(p1, p2)` whose tab-stops
 * default to the parameter names; otherwise (args but none required) it inserts `()` with the caret
 * inside; a no-argument modifier inserts nothing extra.
 */
class ModifierInsertHandler(private val def: ModifierDef) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val required = def.parameters.filter { !it.optional }
        if (def.parameters.isEmpty() && !def.takesArguments) return

        if (required.isEmpty()) {
            val at = context.tailOffset
            context.document.insertString(at, "()")
            context.editor.caretModel.moveToOffset(at + 1)
            context.commitDocument()
            return
        }

        val mgr = TemplateManager.getInstance(context.project)
        val template = mgr.createTemplate("", "")
        template.isToReformat = false
        template.addTextSegment("(")
        required.forEachIndexed { i, p ->
            if (i > 0) template.addTextSegment(", ")
            template.addVariable(p.name, TextExpression(p.name), true)
        }
        template.addTextSegment(")")
        context.editor.caretModel.moveToOffset(context.tailOffset)
        mgr.startTemplate(context.editor, template)
    }
}
```

- [ ] **Step 4: Update the completion MODIFIER branch**

In `AntlersCompletionProvider.kt`, the `AntlersCompletionKind.MODIFIER` branch currently reads:

```kotlin
            AntlersCompletionKind.MODIFIER ->
                for (mod in catalog.modifiers()) {
                    result.addElement(
                        LookupElementBuilder.create(mod.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText("Modifier")
                            .withTailText(if (mod.description.isNotBlank()) "  ${mod.description}" else null, true)
                            .withInsertHandler(ModifierInsertHandler(mod.takesArguments))
                    )
                }
```

Replace it with:

```kotlin
            AntlersCompletionKind.MODIFIER ->
                for (mod in catalog.modifiers()) {
                    val args = ModifierSignature.render(mod).removePrefix(mod.name)   // "(p1, p2)" or ""
                    val desc = if (mod.description.isNotBlank()) "  ${mod.description}" else ""
                    result.addElement(
                        LookupElementBuilder.create(mod.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText("Modifier")
                            .withTailText(args + desc, true)
                            .withInsertHandler(ModifierInsertHandler(mod))
                    )
                }
```

Add the import near the other catalog imports in this file:

```kotlin
import com.github.balotias.intellijantlers.catalog.ModifierSignature
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*ModifierCompletionParamTest"`
Expected: PASS. If `testRequiredParamsInsertedAsTemplate` shows the template still in-progress (e.g. selection markers), the document text still equals the expected string because `TextExpression` defaults render as the variable name; if the platform leaves an active template that blocks assertion, finish it in the test with `myFixture.type('\n')` before asserting and adjust the expected text accordingly.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/ModifierInsertHandler.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/ModifierCompletionParamTest.kt
git commit -m "feat(completion): modifier signature tail text + tab-stop param template"
```

---

### Task 6: Ctrl+P parameter info

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/completion/ModifierArgIndex.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersModifierParameterInfoHandler.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/ModifierArgIndexTest.kt`

- [ ] **Step 1: Write the failing test for the pure index helper**

Create `ModifierArgIndexTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import org.junit.Assert.assertEquals
import org.junit.Test

class ModifierArgIndexTest {

    // Carets are expressed as the offset into the modifier's own text.
    @Test fun parenFirstArg() =
        assertEquals(0, ModifierArgIndex.indexAt("replace('a', 'b')", "replace('a".length))

    @Test fun parenSecondArg() =
        assertEquals(1, ModifierArgIndex.indexAt("replace('a', 'b')", "replace('a', 'b".length))

    @Test fun commaInsideStringIsNotASeparator() =
        assertEquals(0, ModifierArgIndex.indexAt("replace('a,b', '')", "replace('a,b".length))

    @Test fun caretBeforeParenIsOutside() =
        assertEquals(-1, ModifierArgIndex.indexAt("replace('a')", "repl".length))

    @Test fun colonFormSecondArg() =
        assertEquals(1, ModifierArgIndex.indexAt("replace:'a':'b'", "replace:'a':'b".length))

    @Test fun leadingPipePrefixHandled() =
        assertEquals(1, ModifierArgIndex.indexAt("| replace('a', 'b')", "| replace('a', 'b".length))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*ModifierArgIndexTest"`
Expected: FAIL — `ModifierArgIndex` unresolved.

- [ ] **Step 3: Implement the pure helper**

Create `ModifierArgIndex.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

/**
 * Computes which positional argument the caret sits in, given a modifier's raw text and a caret offset
 * relative to that text. Handles both `replace('a', 'b')` (comma-separated in parens) and
 * `replace:'a':'b'` (colon-separated) forms. Returns -1 when the caret is not inside the argument region.
 * Pure / IntelliJ-free.
 */
object ModifierArgIndex {

    fun indexAt(text: String, caret: Int): Int {
        val lparen = text.indexOf('(')
        if (lparen >= 0) {
            if (caret <= lparen) return -1
            var depth = 0
            var index = 0
            var quote: Char? = null
            var i = lparen
            while (i < text.length && i < caret) {
                val c = text[i]
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '\'' || c == '"' -> quote = c
                    c == '(' || c == '[' -> depth++
                    c == ')' || c == ']' -> { depth--; if (depth == 0) return -1 }
                    c == ',' && depth == 1 -> index++
                }
                i++
            }
            return index
        }

        val colon = text.indexOf(':')
        if (colon >= 0) {
            if (caret <= colon) return -1
            var index = 0
            var quote: Char? = null
            var i = colon + 1
            while (i < text.length && i < caret) {
                val c = text[i]
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '\'' || c == '"' -> quote = c
                    c == ':' -> index++
                }
                i++
            }
            return index
        }
        return -1
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*ModifierArgIndexTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Implement the ParameterInfo handler**

Create `AntlersModifierParameterInfoHandler.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.catalog.ModifierDef
import com.github.balotias.intellijantlers.catalog.ModifierSignature
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.intellij.codeInsight.parameterInfo.CreateParameterInfoContext
import com.intellij.codeInsight.parameterInfo.ParameterInfoContext
import com.intellij.codeInsight.parameterInfo.ParameterInfoHandler
import com.intellij.codeInsight.parameterInfo.ParameterInfoUIContext
import com.intellij.codeInsight.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.util.PsiTreeUtil

/** Ctrl+P signature popup for a modifier call: shows the signature and bolds the current argument. */
class AntlersModifierParameterInfoHandler : ParameterInfoHandler<AntlersModifierMixin, ModifierDef> {

    private fun modifierAt(context: ParameterInfoContext): AntlersModifierMixin? {
        val el = context.file.findElementAt(context.offset) ?: return null
        return PsiTreeUtil.getParentOfType(el, AntlersModifierMixin::class.java, false)
    }

    private fun defFor(mod: AntlersModifierMixin): ModifierDef? =
        AntlersCatalogService.getInstance(mod.project).modifiers()
            .firstOrNull { it.name == mod.modifierName && it.parameters.isNotEmpty() }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): AntlersModifierMixin? {
        val mod = modifierAt(context) ?: return null
        val def = defFor(mod) ?: return null
        context.itemsToShow = arrayOf(def)
        return mod
    }

    override fun showParameterInfo(element: AntlersModifierMixin, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): AntlersModifierMixin? =
        modifierAt(context)

    override fun updateParameterInfo(element: AntlersModifierMixin, context: UpdateParameterInfoContext) {
        val rel = context.offset - element.textRange.startOffset
        context.setCurrentParameter(ModifierArgIndex.indexAt(element.text, rel))
    }

    override fun updateUI(p: ModifierDef, context: ParameterInfoUIContext) {
        if (p.parameters.isEmpty()) {
            context.setupUIComponentPresentation("", -1, -1, false, false, false, context.defaultParameterColor)
            return
        }
        val sb = StringBuilder()
        val ranges = ArrayList<IntRange>()
        p.parameters.forEachIndexed { i, param ->
            if (i > 0) sb.append(", ")
            val start = sb.length
            sb.append(ModifierSignature.paramLabel(param))
            ranges.add(start until sb.length)
        }
        val cur = context.currentParameterIndex
        val clamped = if (cur < 0) -1 else cur.coerceAtMost(p.parameters.size - 1)
        val hs = if (clamped >= 0) ranges[clamped].first else -1
        val he = if (clamped >= 0) ranges[clamped].last + 1 else -1
        context.setupUIComponentPresentation(sb.toString(), hs, he, false, false, false, context.defaultParameterColor)
    }

    override fun couldShowInLookup(): Boolean = false
    override fun getParametersForLookup(item: com.intellij.codeInsight.lookup.LookupElement?, context: com.intellij.codeInsight.parameterInfo.ParameterInfoContext?): Array<Any>? = null
}
```

- [ ] **Step 6: Register the handler in `plugin.xml`**

In `src/main/resources/META-INF/plugin.xml`, directly after the `<completion.contributor language="Antlers" .../>` line (line ~27), add:

```xml
        <codeInsight.parameterInfo language="Antlers" implementationClass="com.github.balotias.intellijantlers.completion.AntlersModifierParameterInfoHandler"/>
```

- [ ] **Step 7: Add a handler smoke test**

Append to `ModifierArgIndexTest.kt`? No — handler tests need a fixture. Create `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersModifierParameterInfoTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.catalog.ModifierDef
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext

class AntlersModifierParameterInfoTest : BasePlatformTestCase() {

    fun testResolvesModifierAndItsDef() {
        val text = "{{ title | replace('a', <caret>'b') }}"
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))

        val handler = AntlersModifierParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val element = handler.findElementForParameterInfo(context)

        assertNotNull("found the modifier element", element)
        val items = context.itemsToShow
        assertNotNull(items)
        assertEquals("replace", (items!![0] as ModifierDef).name)
    }
}
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew --rerun-tasks test --tests "*ModifierArgIndexTest" --tests "*AntlersModifierParameterInfoTest"`
Expected: PASS. (If `MockCreateParameterInfoContext` is not resolvable in this platform build, replace the smoke test with a direct unit check that `defFor`-equivalent logic resolves `replace` — but the mock class ships in `testFramework` and should resolve.)

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/ModifierArgIndex.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersModifierParameterInfoHandler.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/ModifierArgIndexTest.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersModifierParameterInfoTest.kt
git commit -m "feat(completion): Ctrl+P parameter info for modifier calls"
```

---

### Task 7: Full-suite gate

**Files:** none (verification only).

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml; echo "exit:$?"`
Expected: prints nothing, `exit:1` (no matches = clean).

- [ ] **Step 3: Sanity-check counts**

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END{print s}'`
Expected: ≈ 464 + the new tests (~17). No failures/errors.

If all green, the feature is complete; proceed to whole-feature review and `superpowers:finishing-a-development-branch`.

---

## Notes for the implementer

- **Out of scope (do not attempt):** modifier docs/completion/Ctrl+P inside string interpolation (`"{… | replace(…)}"`) — there is no PSI there; per-parameter type hints; a colon-form insertion template.
- **Insertion uses the `()` form** even though Antlers also accepts `:`. Ctrl+P (`ModifierArgIndex`) reads both.
- **Live-template testing** (Task 5) can be finicky; the fallback note in Step 5 explains how to stabilize the assertion if the platform leaves the template active.
- Run the **full suite with `--rerun-tasks`** and gate on the result XML, not just `compileKotlin` (Gradle caches; a green compile is not a green test run).
