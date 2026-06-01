# Antlers Catalog Completeness J1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Grow the bundled catalog to the full Statamic 6 core set — ~48 tags (with sub-tags + full parameters) and ~190 modifiers — split into focused files, behind the unchanged `CatalogData.TAGS`/`MODIFIERS` surface.

**Architecture:** Pure data growth on the existing `TagDef`/`ParamDef`/`ModifierDef` schema. Move the current lists into `catalog/CatalogTags.kt` and `catalog/CatalogModifiers.kt`; `CatalogData` aliases them. Coverage tests (hard-coded expected name sets) are the contract.

**Tech Stack:** Kotlin data, JUnit4 unit tests (pure data — no platform fixture needed).

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-catalog-completeness-j1-design.md` (Appendix A = tags + sub-tags; Appendix B = full modifier table).

**TDD note:** `./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`. Full suite is currently **130/130 green**. Keep it green.

**Data sourcing:** Modifiers come from spec Appendix B — but **fetch `https://statamic.dev/reference/modifiers` once** to validate/correct names + arg-flags first. Tag parameters come from fetching `https://statamic.dev/tags/<handle>` per tag. Bias toward over-inclusion (a stale name is harmless; a missing real one isn't).

---

## Task 1: Split the catalog into focused files

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTags.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifiers.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogData.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogSplitTest.kt` (new)

- [ ] **Step 1: Write the failing split test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogSplitTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSplitTest {

    @Test fun catalogDataDelegatesToSplitObjects() {
        assertSame(CatalogTags.ALL, CatalogData.TAGS)
        assertSame(CatalogModifiers.ALL, CatalogData.MODIFIERS)
    }

    @Test fun existingEntriesPreserved() {
        assertTrue(CatalogData.TAGS.any { it.name == "collection" })
        assertTrue(CatalogData.TAGS.any { it.name == "partial" })
        assertTrue(CatalogData.MODIFIERS.any { it.name == "upper" })
        // no duplicate tag names
        assertEquals(CatalogData.TAGS.size, CatalogData.TAGS.map { it.name }.toSet().size)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.catalog.CatalogSplitTest" --no-configuration-cache`
Expected: FAIL — `CatalogTags` / `CatalogModifiers` don't exist.

- [ ] **Step 3: Move the lists into the new files**

Open `catalog/CatalogData.kt`. Cut the entire `val TAGS: List<TagDef> = listOf( … )` literal and paste it into a new file `catalog/CatalogTags.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

/** Bundled Statamic-6 tag catalog (Kotlin data — no runtime JSON parser). Extend here. */
object CatalogTags {
    val ALL: List<TagDef> = listOf(
        // … the existing TagDef(...) entries, moved verbatim from CatalogData.kt …
    )
}
```

Cut the `val MODIFIERS: List<ModifierDef> = listOf( … )` literal into `catalog/CatalogModifiers.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

/** Bundled Statamic-6 modifier catalog (Kotlin data — no runtime JSON parser). Extend here. */
object CatalogModifiers {
    val ALL: List<ModifierDef> = listOf(
        // … the existing ModifierDef(...) entries, moved verbatim from CatalogData.kt …
    )
}
```

Replace `catalog/CatalogData.kt` with the slim alias:

```kotlin
package com.github.balotias.intellijantlers.catalog

/** Backward-compatible aggregate surface; the data lives in CatalogTags / CatalogModifiers. */
object CatalogData {
    val TAGS: List<TagDef> = CatalogTags.ALL
    val MODIFIERS: List<ModifierDef> = CatalogModifiers.ALL
}
```

- [ ] **Step 4: Run the split test + catalog regression — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.catalog.*" --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableCompletionTest" --no-configuration-cache`
Expected: PASS — same data, new home; `CatalogData.TAGS`/`MODIFIERS` identical references.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTags.kt src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifiers.kt src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogData.kt src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogSplitTest.kt
git commit -m "Split catalog into CatalogTags + CatalogModifiers"
```

---

## Task 2: Expand modifiers to the full set (~190)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifiers.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifierCoverageTest.kt` (new)

- [ ] **Step 1: Validate the source list**

Fetch `https://statamic.dev/reference/modifiers` and reconcile against spec Appendix B — correct any
mis-transcribed name or arg-flag. Treat the reconciled list as authoritative for the test + data below.

- [ ] **Step 2: Write the failing coverage test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifierCoverageTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogModifierCoverageTest {

    // The Statamic 6 core modifier set (spec Appendix B). The catalog must cover all of these.
    private val expected = setOf(
        "add", "add_slashes", "ampersand_list", "antlers", "ascii", "as", "at", "attribute",
        "background_position", "backspace", "bard_html", "bard_items", "bard_text", "bool_string",
        "camelize", "cdata", "ceil", "chunk", "classes", "collapse", "collapse_whitespace", "compact",
        "contains", "contains_all", "contains_any", "console_log", "count", "count_substring", "dashify",
        "days_ago", "decode", "deslugify", "dl", "doesnt_overlap", "divide", "dump", "embed_url",
        "ensure_left", "ensure_right", "entities", "excerpt", "explode", "favicon", "filter_empty",
        "first", "flatten", "flip", "floor", "format", "format_number", "format_translated", "full_urls",
        "get", "gravatar", "group_by", "headline", "hex_to_rgb", "hours_ago", "image", "in_array",
        "insert", "is_after", "is_alpha", "is_alphanumeric", "is_array", "is_before", "is_between",
        "is_blank", "is_email", "is_embeddable", "is_external_url", "is_future", "is_json", "is_leap_year",
        "is_lowercase", "is_numberwang", "is_numeric", "is_past", "is_today", "is_tomorrow", "is_uppercase",
        "is_url", "is_weekday", "is_weekend", "is_yesterday", "iso_format", "join", "kebab", "key_by",
        "keys", "last", "lcfirst", "length", "limit", "link", "list", "lower", "macro", "mark", "markdown",
        "md5", "minutes_ago", "mod", "modify_date", "months_ago", "multiply", "nl2br", "obfuscate",
        "obfuscate_email", "offset", "ol", "option_list", "output", "overlaps", "pad", "parse_url",
        "partial", "pathinfo", "piped", "pluck", "plural", "random", "raw", "rawurlencode",
        "rawurlencode_except_slashes", "ray", "read_time", "regex_mark", "regex_replace", "relative",
        "remove_left", "remove_right", "repeat", "replace", "resolve", "reverse", "round", "safe_truncate",
        "sanitize", "scope", "seconds_ago", "segment", "select", "sentence_list", "shuffle", "singular",
        "slugify", "smartypants", "snake", "sort", "spaceless", "split", "str_pad_left", "strip_tags",
        "studly", "substr", "subtract", "sum", "surround", "swap_case", "table", "tidy", "timezone",
        "title", "to_json", "to_qs", "to_spaces", "to_tabs", "trim", "truncate", "ucfirst", "ul",
        "underscored", "unique", "upper", "url", "urldecode", "urlencode", "urlencode_except_slashes",
        "values", "weeks_ago", "where", "where-in", "widont", "word_count", "wrap", "years_ago"
    )

    @Test fun coversAllCoreModifiers() {
        val have = CatalogModifiers.ALL.map { it.name }.toSet()
        val missing = expected - have
        assertTrue("missing modifiers: $missing", missing.isEmpty())
    }

    @Test fun noDuplicatesAndDescribed() {
        val names = CatalogModifiers.ALL.map { it.name }
        assertTrue("duplicate modifier names", names.size == names.toSet().size)
        assertTrue("every modifier has a description",
            CatalogModifiers.ALL.all { it.description.isNotBlank() })
    }

    @Test fun docUrlsWellFormed() {
        assertTrue(CatalogModifiers.ALL.all { it.docUrl.startsWith("https://statamic.dev/") })
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.catalog.CatalogModifierCoverageTest" --no-configuration-cache`
Expected: FAIL — `coversAllCoreModifiers` reports ~160 missing.

- [ ] **Step 4: Expand `CatalogModifiers.ALL`**

In `catalog/CatalogModifiers.kt`, add a `ModifierDef` for every name in the reconciled Appendix B that
isn't already present, keeping the existing 30. **If Step 1's reconciliation added/renamed any name, update
the `expected` set in the test to match** so the catalog and the test stay in sync (they are independent
transcriptions of the same source). For each:
`ModifierDef(name = "<name>", description = "<from Appendix B>", takesArguments = <Y→true/N→false>, docUrl = "https://statamic.dev/modifiers/<name>")`.

Example additions (follow this exact shape for all of them):

```kotlin
        ModifierDef(name = "where", description = "Filters an array by matching criteria.", takesArguments = true, docUrl = "https://statamic.dev/modifiers/where"),
        ModifierDef(name = "pluck", description = "Extracts a single property from each item.", takesArguments = true, docUrl = "https://statamic.dev/modifiers/pluck"),
        ModifierDef(name = "ceil", description = "Rounds a number up to the next integer.", takesArguments = false, docUrl = "https://statamic.dev/modifiers/ceil"),
        ModifierDef(name = "format_number", description = "Formats a number with localization.", takesArguments = true, docUrl = "https://statamic.dev/modifiers/format_number"),
        // … one entry per remaining Appendix B name …
```

- [ ] **Step 5: Run the modifier coverage test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.catalog.CatalogModifierCoverageTest" --no-configuration-cache`
Expected: PASS (3 tests; 0 missing).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifiers.kt src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifierCoverageTest.kt
git commit -m "Expand modifier catalog to the full Statamic 6 core set"
```

---

## Task 3: Expand tags to the full set with sub-tags + parameters (~48)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTags.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTagCoverageTest.kt` (new)

- [ ] **Step 1: Source the per-tag parameters**

For each tag handle in spec Appendix A that needs parameters, fetch `https://statamic.dev/tags/<handle>`
and extract its parameter list (name, short description, type, required). These fetches are independent —
do them in parallel where possible. Preserve the existing param sets already authored for `collection`,
`nav`, `partial`, `asset`, `assets`, `glide`, `form`, `user`, `taxonomy`, `cache`, `section`, `search`.

- [ ] **Step 2: Write the failing coverage test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTagCoverageTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTagCoverageTest {

    // Statamic 6 core top-level tag handles (spec Appendix A).
    private val expected = setOf(
        "404", "asset", "assets", "cache", "children", "collection", "cookie", "dictionary", "dump",
        "foreach", "form", "get_content", "get_error", "get_errors", "get_files", "get_site", "glide",
        "increment", "installed", "link", "locales", "loop", "markdown", "mix", "mount_url", "nav",
        "nocache", "oauth", "obfuscate", "parent", "partial", "protect", "redirect", "route", "scope",
        "search", "section", "session", "svg", "switch", "taxonomy", "trans", "user", "user_groups",
        "user_roles", "users", "vite", "yield"
    )

    private fun tag(name: String) = CatalogTags.ALL.first { it.name == name }

    @Test fun coversAllCoreTags() {
        val have = CatalogTags.ALL.map { it.name }.toSet()
        val missing = expected - have
        assertTrue("missing tags: $missing", missing.isEmpty())
    }

    @Test fun noDuplicatesAndDocUrls() {
        val names = CatalogTags.ALL.map { it.name }
        assertTrue("duplicate tag names", names.size == names.toSet().size)
        assertTrue("docUrls well-formed",
            CatalogTags.ALL.all { it.docUrl.startsWith("https://statamic.dev/tags/") })
    }

    @Test fun pairFlagsForStructuralTags() {
        listOf("collection", "nav", "taxonomy", "form", "assets", "cache", "section", "loop", "foreach",
            "switch", "users", "nocache").forEach { assertTrue("$it should be a pair", tag(it).isPair) }
        listOf("partial", "glide", "link", "svg", "redirect", "yield", "404", "asset", "mix", "trans")
            .forEach { assertTrue("$it should be single", !tag(it).isPair) }
    }

    @Test fun subTagsPresent() {
        assertTrue(tag("session").methods.containsAll(listOf("set", "has", "forget")))
        assertTrue(tag("user").methods.containsAll(listOf("login_form", "can")))
        assertTrue(tag("collection").methods.contains("count"))
        assertTrue(tag("form").methods.containsAll(listOf("create", "errors", "fields")))
    }

    @Test fun keyParametersPresent() {
        fun params(t: String) = tag(t).parameters.map { it.name }.toSet()
        assertTrue(params("collection").containsAll(listOf("from", "limit", "sort")))
        assertTrue(params("glide").containsAll(listOf("width", "height")))
        assertTrue(params("nav").contains("handle"))
        assertTrue("every ParamDef has a name", CatalogTags.ALL.all { t -> t.parameters.all { it.name.isNotBlank() } })
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.catalog.CatalogTagCoverageTest" --no-configuration-cache`
Expected: FAIL — `coversAllCoreTags` reports the ~23 missing handles.

- [ ] **Step 4: Expand `CatalogTags.ALL`**

In `catalog/CatalogTags.kt`, add a `TagDef` for every Appendix A handle not already present (keep the
existing 25, with their params). For each new tag:
`TagDef(name, description, docUrl = "https://statamic.dev/tags/<handle>", isPair = <P→true/S→false from Appendix A>, methods = listOf(<sub-tags from Appendix A>), parameters = listOf(<ParamDef…from the per-tag fetch>))`.

Example shape (a pair tag with sub-tags + params, and a single tag):

```kotlin
        TagDef(
            name = "session",
            description = "Get, set, check, and forget data in the user's session.",
            docUrl = "https://statamic.dev/tags/session",
            isPair = false,
            methods = listOf("set", "get", "has", "forget", "flush", "flash", "dump")
        ),
        TagDef(
            name = "loop",
            description = "Loop a specified number of times.",
            docUrl = "https://statamic.dev/tags/loop",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "times", description = "How many times to loop.", type = "integer", required = true)
            )
        ),
        // … one entry per remaining Appendix A handle …
```

If a tag genuinely documents no parameters, `parameters` defaults to empty — that's fine.

- [ ] **Step 5: Run the tag coverage test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.catalog.CatalogTagCoverageTest" --no-configuration-cache`
Expected: PASS (5 tests; 0 missing).

- [ ] **Step 6: Run the catalog-consumer regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersScopedCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersContainerScopeTest" --no-configuration-cache`
Expected: PASS — completion/folding/docs consume the larger catalog transparently (tags are still offered with insert handlers, `isPair` drives blocks).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTags.kt src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTagCoverageTest.kt
git commit -m "Expand tag catalog to the full Statamic 6 core set with sub-tags + params"
```

---

## Task 4: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew test --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm no failures + sane catalog sizes**

Run: `grep -lo 'failures="[1-9]' build/test-results/test/*.xml | wc -l`
Expected: `0`.

Run: `grep -c 'TagDef(' src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTags.kt && grep -c 'ModifierDef(' src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifiers.kt`
Expected: ~48 and ~190 respectively.

- [ ] **Step 3: Verify production compilation**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

---

## Notes for the implementer

- **No schema / consumer / plugin.xml changes.** Only data + the file split.
- Modifiers: validate spec Appendix B against the live `reference/modifiers` page once, then transcribe;
  bias to over-inclusion. Tags: fetch each `tags/<handle>` page for parameters (parallelizable).
- Keep the existing 25 tags / 30 modifiers (and their authored params) — extend, don't replace.
- `isPair` accuracy matters (folding, brace matching, the block insert handler) — take it from Appendix A's
  P/S column; the test spot-checks structural tags.
- Hyphenated/odd names (`where-in`, `is_numberwang`) are stored as literal strings — no identifier rules apply.
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
```
