# Antlers Structural Completion (Sub-project B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the document-text completion with PSI-driven, context-aware completion for Antlers **tags, tag methods, parameters, and modifiers**, backed by a hybrid catalog (bundled Statamic-6 JSON + project PHP scan).

**Architecture:** A `AntlersCatalogService` (project service) loads a bundled JSON catalog and merges custom tags/modifiers discovered by scanning the project's PHP. A single `AntlersCompletionContributor` runs one provider that classifies the caret context (tag-name / tag-method / parameter / modifier / none) from the surrounding tokens, then delegates to one of four focused completion objects. Classification is token-based so it is robust against the completion dummy identifier and error-recovery trees.

**Tech Stack:** Kotlin, IntelliJ Platform 2025.2, Gson (bundled) for JSON, `BasePlatformTestCase` for tests.

**Builds on:** Sub-project A (branch `antlers-grammar-completion`). Actual PSI from A:
- `AntlersStatement` = one `{{ ... }}`. Its body is a `namePath` (the tag/variable head) plus `parameter`/`modifier`/expr children, OR a `closingTag`, OR a `condition`.
- `AntlersNamePath` mixin: `head: String`, `method: String?`, `pathText: String`.
- `AntlersParameter` mixin: `parameterName: String`, `isBound: Boolean`, `valueElement: PsiElement?`.
- `AntlersModifier` mixin: `modifierName: String`.
- `AntlersClosingTag` mixin: `closedName: String?`. `AntlersCondition` mixin: `keyword: String`.
- Token types on `AntlersTypes`: `T_LDOUBLE`, `T_RDOUBLE`, `T_SLASH`, `T_COLON`, `T_PIPE`, `T_EQUALS`, `T_IDENT`, `T_WS`, `T_STRING`, `T_NUMBER`, `T_RBRACE`, `T_RBRACKET`, `T_RPAREN`, etc.

**Note on tests:** The Claude sandbox cannot fetch the IntelliJ test runtime, so in-sandbox verification uses `./gradlew compileKotlin compileTestKotlin`. The `./gradlew test` commands run in the developer's environment.

---

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `catalog/CatalogModels.kt` | `TagDef`, `ParamDef`, `ModifierDef` data classes | Create |
| `catalog/AntlersCatalogService.kt` | Project service: load bundled JSON + merge project scan; lookups | Create |
| `catalog/scan/TagScanner.kt` | Scan `**/Tags/*.php` for custom tag classes | Create (from `AntlersCustomTagFinder`) |
| `catalog/scan/ModifierScanner.kt` | Scan `**/Modifiers/*.php` for custom modifier classes | Create |
| `resources/catalog/tags.json` | Bundled Statamic-6 core tag catalog (starter) | Create |
| `resources/catalog/modifiers.json` | Bundled Statamic-6 modifier catalog (starter) | Create |
| `completion/AntlersCompletionContext.kt` | Classify caret context from tokens | Create |
| `completion/AntlersCompletionProvider.kt` | Dispatcher provider | Rewrite (replaces document-text version) |
| `completion/kinds/TagNameCompletions.kt` etc. | The four completion bodies | Create |
| `completion/ParameterInsertHandler.kt`, `ModifierInsertHandler.kt` | Insert handlers | Create |
| `completion/AntlersTagInsertHandler.kt` | Tag insert handler | Keep (reuse) |
| `completion/AntlersCustomTagFinder.kt` | Old finder | Delete (moved to `scan/TagScanner.kt`) |
| `completion/StatamicNativeTags.kt` | Old hardcoded list | Delete (replaced by catalog) |
| tests under `src/test/.../completion/` | Catalog, context, completion tests | Create |

---

## Task 1: Catalog models + bundled JSON + loader

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModels.kt`
- Create: `src/main/resources/catalog/tags.json`, `src/main/resources/catalog/modifiers.json`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogLoader.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogLoaderTest.kt`

- [ ] **Step 1: Write the failing loader test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/catalog/CatalogLoaderTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLoaderTest {

    @Test
    fun loadsBundledTags() {
        val tags = CatalogLoader.loadTags()
        assertTrue("expected core tags", tags.size >= 10)
        val collection = tags.firstOrNull { it.name == "collection" }
        assertNotNull("collection tag present", collection)
        assertTrue("collection is a pair tag", collection!!.isPair)
        assertTrue("collection has a 'from' or 'limit' param",
            collection.parameters.any { it.name == "limit" || it.name == "from" })
    }

    @Test
    fun loadsBundledModifiers() {
        val mods = CatalogLoader.loadModifiers()
        assertTrue("expected modifiers", mods.size >= 10)
        assertNotNull("upper modifier present", mods.firstOrNull { it.name == "upper" })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.catalog.CatalogLoaderTest"`
Expected: FAIL (`CatalogLoader`, `CatalogModels` don't exist). Sandbox: `./gradlew compileTestKotlin` fails to compile — that is the "red".

- [ ] **Step 3: Create the models**

Create `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModels.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

/** A Statamic tag and its completion metadata. */
data class TagDef(
    val name: String,
    val description: String = "",
    val docUrl: String = "",
    val isPair: Boolean = false,
    val methods: List<String> = emptyList(),
    val parameters: List<ParamDef> = emptyList()
)

/** A tag/modifier parameter. */
data class ParamDef(
    val name: String,
    val description: String = "",
    val type: String = "",
    val required: Boolean = false
)

/** A Statamic modifier and its completion metadata. */
data class ModifierDef(
    val name: String,
    val description: String = "",
    val docUrl: String = "",
    val takesArguments: Boolean = false
)
```

- [ ] **Step 4: Create the bundled JSON (starter catalog)**

Create `src/main/resources/catalog/tags.json`:

```json
[
  { "name": "collection", "description": "Fetch and loop over entries in a collection.", "docUrl": "https://statamic.dev/tags/collection", "isPair": true,
    "methods": ["count", "next", "previous", "older", "newer"],
    "parameters": [
      { "name": "from", "description": "Collection handle(s) to fetch from." },
      { "name": "limit", "description": "Maximum number of entries.", "type": "integer" },
      { "name": "sort", "description": "Field and direction, e.g. date:desc." },
      { "name": "filter", "description": "A custom query filter." },
      { "name": "paginate", "description": "Entries per page.", "type": "integer" },
      { "name": "as", "description": "Alias the results into a named loop." },
      { "name": "scope", "description": "Scope each item under a variable." }
    ] },
  { "name": "nav", "description": "Build navigation / structure trees.", "docUrl": "https://statamic.dev/tags/nav", "isPair": true,
    "methods": ["breadcrumbs"],
    "parameters": [
      { "name": "handle", "description": "The navigation handle." },
      { "name": "from", "description": "Start the tree from a URI." },
      { "name": "include_home", "description": "Include the home page.", "type": "boolean" },
      { "name": "max_depth", "description": "Maximum nesting depth.", "type": "integer" }
    ] },
  { "name": "partial", "description": "Include another template.", "docUrl": "https://statamic.dev/tags/partial", "isPair": false,
    "methods": ["if_exists", "exists"],
    "parameters": [
      { "name": "src", "description": "Path to the partial.", "required": true }
    ] },
  { "name": "asset", "description": "Fetch a single asset.", "docUrl": "https://statamic.dev/tags/asset", "isPair": false,
    "parameters": [
      { "name": "id", "description": "The asset id (container::path)." },
      { "name": "src", "description": "The asset path." }
    ] },
  { "name": "assets", "description": "Loop over multiple assets.", "docUrl": "https://statamic.dev/tags/assets", "isPair": true,
    "parameters": [
      { "name": "handle", "description": "The field/variable holding assets." },
      { "name": "limit", "description": "Maximum number of assets.", "type": "integer" }
    ] },
  { "name": "glide", "description": "Manipulate images.", "docUrl": "https://statamic.dev/tags/glide", "isPair": false,
    "parameters": [
      { "name": "src", "description": "Source image path/url." },
      { "name": "width", "description": "Target width.", "type": "integer" },
      { "name": "height", "description": "Target height.", "type": "integer" },
      { "name": "fit", "description": "Fit mode (crop, contain, max...)." },
      { "name": "format", "description": "Output format." }
    ] },
  { "name": "form", "description": "Render and handle forms.", "docUrl": "https://statamic.dev/tags/form", "isPair": true,
    "methods": ["create", "errors", "success", "set", "submission", "submissions"],
    "parameters": [
      { "name": "in", "description": "The form handle." },
      { "name": "redirect", "description": "Where to redirect on success." }
    ] },
  { "name": "user", "description": "Authenticated user / user data.", "docUrl": "https://statamic.dev/tags/user", "isPair": true,
    "methods": ["profile", "logout", "can", "is", "in"] },
  { "name": "taxonomy", "description": "Loop over taxonomy terms.", "docUrl": "https://statamic.dev/tags/taxonomy", "isPair": true,
    "parameters": [ { "name": "from", "description": "Taxonomy handle." }, { "name": "sort", "description": "Sort field/direction." } ] },
  { "name": "cache", "description": "Cache a portion of a template.", "docUrl": "https://statamic.dev/tags/cache", "isPair": true,
    "parameters": [ { "name": "for", "description": "Duration, e.g. 60 minutes." }, { "name": "key", "description": "A custom cache key." } ] },
  { "name": "section", "description": "Define a template section.", "isPair": true },
  { "name": "yield", "description": "Output a named section.", "isPair": false },
  { "name": "redirect", "description": "Redirect the response.", "isPair": false },
  { "name": "increment", "description": "An auto-incrementing counter.", "isPair": false },
  { "name": "markdown", "description": "Render markdown.", "isPair": true },
  { "name": "obfuscate", "description": "Obfuscate text from bots.", "isPair": true },
  { "name": "session", "description": "Read/flash session data.", "isPair": true },
  { "name": "mix", "description": "Laravel Mix asset url.", "isPair": false },
  { "name": "vite", "description": "Vite asset tags.", "isPair": false },
  { "name": "svg", "description": "Inline an SVG.", "isPair": false },
  { "name": "trans", "description": "Translate a string.", "isPair": false },
  { "name": "link", "description": "Generate a URL.", "isPair": false },
  { "name": "dump", "description": "Dump variables for debugging.", "isPair": false },
  { "name": "get_content", "description": "Fetch content by id/url.", "isPair": true },
  { "name": "search", "description": "Query a search index.", "isPair": true,
    "parameters": [ { "name": "index", "description": "The search index handle." }, { "name": "query", "description": "The search query variable." } ] }
]
```

Create `src/main/resources/catalog/modifiers.json`:

```json
[
  { "name": "upper", "description": "Uppercase the value." },
  { "name": "lower", "description": "Lowercase the value." },
  { "name": "title", "description": "Title-case the value." },
  { "name": "ucfirst", "description": "Capitalise the first letter." },
  { "name": "truncate", "description": "Truncate to a length.", "takesArguments": true },
  { "name": "limit", "description": "Limit to N items/characters.", "takesArguments": true },
  { "name": "raw", "description": "Output without escaping." },
  { "name": "markdown", "description": "Render markdown." },
  { "name": "nl2br", "description": "Convert newlines to <br>." },
  { "name": "strip_tags", "description": "Remove HTML tags.", "takesArguments": true },
  { "name": "format", "description": "Format a date.", "takesArguments": true },
  { "name": "format_localized", "description": "Locale-aware date format.", "takesArguments": true },
  { "name": "relative", "description": "Relative date, e.g. 3 days ago." },
  { "name": "count", "description": "Count items." },
  { "name": "length", "description": "Length of a string/array." },
  { "name": "first", "description": "First item." },
  { "name": "last", "description": "Last item." },
  { "name": "reverse", "description": "Reverse a string/array." },
  { "name": "sort", "description": "Sort an array.", "takesArguments": true },
  { "name": "where", "description": "Filter an array by key/value.", "takesArguments": true },
  { "name": "pluck", "description": "Pluck a field from each item.", "takesArguments": true },
  { "name": "join", "description": "Join an array with a glue string.", "takesArguments": true },
  { "name": "explode", "description": "Split a string into an array.", "takesArguments": true },
  { "name": "replace", "description": "Replace a substring.", "takesArguments": true },
  { "name": "url", "description": "The URL of an entry/asset." },
  { "name": "slugify", "description": "Make a URL slug." },
  { "name": "default", "description": "Fallback when empty.", "takesArguments": true },
  { "name": "ensure_right", "description": "Ensure the string ends with a suffix.", "takesArguments": true },
  { "name": "embed_url", "description": "Make an embeddable URL." },
  { "name": "to_json", "description": "Encode as JSON." }
]
```

The catalog is a starter set — more entries can be added later as pure data. Validate the JSON parses (the loader test does this) before committing.

- [ ] **Step 5: Create the loader**

Create `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogLoader.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Loads the bundled JSON catalog from plugin resources. Results are small; callers cache. */
object CatalogLoader {
    private val gson = Gson()

    fun loadTags(): List<TagDef> = read("/catalog/tags.json", object : TypeToken<List<TagDef>>() {}.type)

    fun loadModifiers(): List<ModifierDef> = read("/catalog/modifiers.json", object : TypeToken<List<ModifierDef>>() {}.type)

    private fun <T> read(resource: String, type: java.lang.reflect.Type): T {
        val stream = CatalogLoader::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled catalog resource: $resource")
        return stream.bufferedReader().use { gson.fromJson(it, type) }
    }
}
```

If `com.google.gson` is not resolvable on the classpath, STOP and report — the fallback is `com.fasterxml.jackson.databind.ObjectMapper` (also bundled); switch the loader to Jackson with `readValue` + `jacksonTypeRef`. Verify availability with a quick compile before continuing.

- [ ] **Step 6: Verify**

Run: `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL. (Dev env: `./gradlew test --tests "*CatalogLoaderTest"` → PASS.)

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog src/main/resources/catalog src/test/kotlin/com/github/balotias/intellijantlers/catalog
git commit -m "Add bundled Antlers catalog (models, JSON, loader)"
```

---

## Task 2: Project scanners (custom tags + modifiers)

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/scan/TagScanner.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/scan/ModifierScanner.kt`
- Delete: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCustomTagFinder.kt`

- [ ] **Step 1: Create `TagScanner` (port of `AntlersCustomTagFinder`)**

Create `src/main/kotlin/com/github/balotias/intellijantlers/catalog/scan/TagScanner.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog.scan

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.regex.Pattern

/** Finds project-defined Statamic tags by scanning PHP files under a `Tags/` directory. */
object TagScanner {
    private val CLASS_PATTERN =
        Pattern.compile("class\\s+([a-zA-Z0-9_]+)\\s+extends\\s+(?:\\\\?Statamic\\\\Tags\\\\)?Tags")

    fun scan(project: Project): List<String> = scanDir(project, "/Tags/", CLASS_PATTERN)

    internal fun scanDir(project: Project, dirMarker: String, pattern: Pattern): List<String> {
        val names = mutableListOf<String>()
        try {
            val phpFiles = FilenameIndex.getAllFilesByExt(project, "php", GlobalSearchScope.projectScope(project))
            for (file in phpFiles) {
                if (!file.path.contains(dirMarker)) continue
                try {
                    val matcher = pattern.matcher(VfsUtilCore.loadText(file))
                    if (matcher.find()) names.add(camelToSnake(matcher.group(1)))
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    // ignore unreadable files
                }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            // index not ready
        }
        return names.distinct()
    }

    internal fun camelToSnake(str: String): String =
        str.replace("([a-z])([A-Z]+)".toRegex(), "$1_$2").lowercase()
}
```

- [ ] **Step 2: Create `ModifierScanner`**

Create `src/main/kotlin/com/github/balotias/intellijantlers/catalog/scan/ModifierScanner.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog.scan

import com.intellij.openapi.project.Project
import java.util.regex.Pattern

/** Finds project-defined Statamic modifiers by scanning PHP files under a `Modifiers/` directory. */
object ModifierScanner {
    private val CLASS_PATTERN =
        Pattern.compile("class\\s+([a-zA-Z0-9_]+)\\s+extends\\s+(?:\\\\?Statamic\\\\Modifiers\\\\)?Modifier")

    fun scan(project: Project): List<String> = TagScanner.scanDir(project, "/Modifiers/", CLASS_PATTERN)
}
```

- [ ] **Step 3: Delete the old finder**

```bash
git rm src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCustomTagFinder.kt
```

(If `AntlersCompletionProvider.kt` still references `AntlersCustomTagFinder`, that file is rewritten in Task 5 — but it must still compile now. If deleting breaks compilation, comment out the custom-tag block in the old `AntlersCompletionProvider` with a `// replaced in Task 5` note, or temporarily change it to call `TagScanner.scan(project)`.)

- [ ] **Step 4: Verify + commit**

Run: `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL.

```bash
git add -A
git commit -m "Add catalog project scanners for custom tags and modifiers"
```

---

## Task 3: `AntlersCatalogService`

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/AntlersCatalogService.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/AntlersCatalogServiceTest.kt`

- [ ] **Step 1: Write the failing service test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/catalog/AntlersCatalogServiceTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCatalogServiceTest : BasePlatformTestCase() {

    fun testBundledTagsAndModifiers() {
        val svc = AntlersCatalogService.getInstance(project)
        assertNotNull("collection tag", svc.tag("collection"))
        assertTrue("has modifiers", svc.modifiers().any { it.name == "upper" })
        assertTrue("tag names include nav", svc.tagNames().contains("nav"))
        // A bundled tag's parameters are reachable
        assertTrue(svc.tag("collection")!!.parameters.any { it.name == "limit" })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersCatalogServiceTest"` → FAIL (no service). Sandbox: `./gradlew compileTestKotlin` fails.

- [ ] **Step 3: Implement the service**

Create `src/main/kotlin/com/github/balotias/intellijantlers/catalog/AntlersCatalogService.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import com.github.balotias.intellijantlers.catalog.scan.ModifierScanner
import com.github.balotias.intellijantlers.catalog.scan.TagScanner
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/** Source of truth for tag/modifier completion: bundled JSON merged with project-scanned customs. */
@Service(Service.Level.PROJECT)
class AntlersCatalogService(private val project: Project) {

    private val bundledTags: List<TagDef> by lazy { CatalogLoader.loadTags() }
    private val bundledModifiers: List<ModifierDef> by lazy { CatalogLoader.loadModifiers() }

    /** All tags: bundled, plus custom tag names found in the project (deduped by name). */
    fun tags(): List<TagDef> {
        val custom = scannedTagNames().filter { name -> bundledTags.none { it.name == name } }
            .map { TagDef(name = it, description = "Custom tag") }
        return bundledTags + custom
    }

    fun tagNames(): List<String> = tags().map { it.name }

    fun tag(name: String): TagDef? = tags().firstOrNull { it.name == name }

    /** All modifiers: bundled, plus custom modifier names found in the project. */
    fun modifiers(): List<ModifierDef> {
        val custom = scannedModifierNames().filter { name -> bundledModifiers.none { it.name == name } }
            .map { ModifierDef(name = it, description = "Custom modifier") }
        return bundledModifiers + custom
    }

    private fun scannedTagNames(): List<String> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(TagScanner.scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }

    private fun scannedModifierNames(): List<String> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(ModifierScanner.scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }

    companion object {
        fun getInstance(project: Project): AntlersCatalogService = project.service()
    }
}
```

NOTE: the two `getCachedValue(project) { ... }` calls share the same project key, which collides. Use distinct keys via `CachedValuesManager.getProjectPsiDependentCache` per function, OR store each in its own `Key`. Implement with two `com.intellij.openapi.util.Key<CachedValue<List<String>>>` fields and `CachedValuesManager.getManager(project).getCachedValue(project, KEY, provider, false)`. The implementer should use the keyed overload to avoid the collision:

```kotlin
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue

private val tagKey = Key.create<CachedValue<List<String>>>("antlers.scannedTags")
private val modKey = Key.create<CachedValue<List<String>>>("antlers.scannedModifiers")

private fun scannedTagNames(): List<String> =
    CachedValuesManager.getManager(project).getCachedValue(project, tagKey, {
        CachedValueProvider.Result.create(TagScanner.scan(project), PsiModificationTracker.MODIFICATION_COUNT)
    }, false)
```

- [ ] **Step 4: Verify + commit**

Run: `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL.

```bash
git add -A
git commit -m "Add AntlersCatalogService merging bundled catalog with project scan"
```

---

## Task 4: Completion context classifier

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContextTest.kt`

- [ ] **Step 1: Write the failing classifier test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContextTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionContextTest : BasePlatformTestCase() {

    private fun kindAt(text: String): AntlersCompletionKind {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("t.antlers.html", text)
        val element = myFixture.file.findElementAt(caret) ?: myFixture.file.findElementAt(caret - 1)!!
        return AntlersCompletionContext.classify(element).kind
    }

    fun testTagNameAtStart() = assertEquals(AntlersCompletionKind.TAG_NAME, kindAt("{{ <caret> }}"))
    fun testTagNameTyping() = assertEquals(AntlersCompletionKind.TAG_NAME, kindAt("{{ coll<caret> }}"))
    fun testClosingTagName() = assertEquals(AntlersCompletionKind.TAG_NAME, kindAt("{{ /<caret> }}"))
    fun testTagMethod() = assertEquals(AntlersCompletionKind.TAG_METHOD, kindAt("{{ collection:<caret> }}"))
    fun testParameter() = assertEquals(AntlersCompletionKind.PARAMETER, kindAt("{{ collection <caret> }}"))
    fun testParameterAfterMethod() = assertEquals(AntlersCompletionKind.PARAMETER, kindAt("{{ collection:blog <caret> }}"))
    fun testModifier() = assertEquals(AntlersCompletionKind.MODIFIER, kindAt("{{ title | <caret> }}"))
    fun testNoneInHtml() = assertEquals(AntlersCompletionKind.NONE, kindAt("<div <caret>></div>"))
    fun testNoneInValue() = assertEquals(AntlersCompletionKind.NONE, kindAt("{{ collection limit=\"<caret>\" }}"))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersCompletionContextTest"` → FAIL. Sandbox: `./gradlew compileTestKotlin` fails.

- [ ] **Step 3: Implement the classifier**

Create `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil

enum class AntlersCompletionKind { TAG_NAME, TAG_METHOD, PARAMETER, MODIFIER, NONE }

/** Result of classifying the caret: the kind, and (for method/parameter) the enclosing tag head. */
data class AntlersCompletionInfo(val kind: AntlersCompletionKind, val tagHead: String? = null)

object AntlersCompletionContext {

    /** Significant tokens we look back over (whitespace and the completion dummy are skipped). */
    private val SKIP = setOf(AntlersTypes.T_WS)

    fun classify(position: PsiElement): AntlersCompletionInfo {
        val statement = PsiTreeUtil.getParentOfType(position, AntlersStatement::class.java)
            ?: return AntlersCompletionInfo(AntlersCompletionKind.NONE)

        val prev = prevSignificantLeaf(position) ?: return AntlersCompletionInfo(AntlersCompletionKind.NONE)
        val prevType = prev.node.elementType

        return when (prevType) {
            AntlersTypes.T_PIPE -> AntlersCompletionInfo(AntlersCompletionKind.MODIFIER)

            AntlersTypes.T_LDOUBLE ->
                AntlersCompletionInfo(AntlersCompletionKind.TAG_NAME)

            AntlersTypes.T_SLASH ->
                if (prevSignificantLeaf(prev)?.node?.elementType == AntlersTypes.T_LDOUBLE)
                    AntlersCompletionInfo(AntlersCompletionKind.TAG_NAME) else AntlersCompletionInfo(AntlersCompletionKind.NONE)

            AntlersTypes.T_COLON -> {
                val head = headOf(statement)
                if (head != null) AntlersCompletionInfo(AntlersCompletionKind.TAG_METHOD, head)
                else AntlersCompletionInfo(AntlersCompletionKind.NONE)
            }

            AntlersTypes.T_EQUALS -> AntlersCompletionInfo(AntlersCompletionKind.NONE) // value position

            AntlersTypes.T_IDENT, AntlersTypes.T_STRING, AntlersTypes.T_NUMBER,
            AntlersTypes.T_RBRACE, AntlersTypes.T_RBRACKET, AntlersTypes.T_RPAREN -> {
                val head = headOf(statement)
                if (head != null) AntlersCompletionInfo(AntlersCompletionKind.PARAMETER, head)
                else AntlersCompletionInfo(AntlersCompletionKind.NONE)
            }

            else -> AntlersCompletionInfo(AntlersCompletionKind.NONE)
        }
    }

    /** The tag head (first name-path's head) of the statement, or null if the caret IS that head. */
    private fun headOf(statement: AntlersStatement): String? {
        val namePath = PsiTreeUtil.findChildOfType(statement, AntlersNamePathMixin::class.java) ?: return null
        val head = namePath.head
        return head.ifBlank { null }
    }

    /** Nearest preceding leaf that is not whitespace and not the completion dummy. */
    private fun prevSignificantLeaf(from: PsiElement): PsiElement? {
        var e: PsiElement? = PsiTreeUtil.prevLeaf(from)
        while (e != null && (e.node.elementType in SKIP || isInsideOtherStatement(e, from))) {
            e = PsiTreeUtil.prevLeaf(e)
        }
        return e
    }

    /** Guard: don't look past the opening of the current statement into a previous one. */
    private fun isInsideOtherStatement(candidate: PsiElement, origin: PsiElement): Boolean {
        val s1 = PsiTreeUtil.getParentOfType(candidate, AntlersStatement::class.java)
        val s2 = PsiTreeUtil.getParentOfType(origin, AntlersStatement::class.java)
        return s1 != null && s1 != s2
    }

    private fun IElementType.unused() = Unit
}
```

NOTE on the `headOf` "caret IS the head" case: when the caret is the head (e.g. `{{ coll<caret> }}`), `prevSignificantLeaf` returns `T_LDOUBLE` and we already returned TAG_NAME before calling `headOf`, so `headOf` is only consulted for METHOD/PARAMETER where a real head precedes the caret. Remove the unused `IElementType.unused()` helper if the implementer's linter flags it.

- [ ] **Step 4: Verify + commit**

Run: `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL. (Dev env runs the 9 classifier tests.)

```bash
git add -A
git commit -m "Add Antlers completion context classifier"
```

---

## Task 5: Completion providers + contributor rewrite

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/completion/ParameterInsertHandler.kt`, `ModifierInsertHandler.kt`
- Rewrite: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Delete: `src/main/kotlin/com/github/balotias/intellijantlers/completion/StatamicNativeTags.kt`
- Keep: `AntlersCompletionContributor.kt` (verify it registers the provider), `AntlersTagInsertHandler.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionTest.kt` (extend the existing one)

- [ ] **Step 1: Create the parameter insert handler**

Create `src/main/kotlin/com/github/balotias/intellijantlers/completion/ParameterInsertHandler.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/** Inserts a tag parameter as `name="<caret>"`. */
object ParameterInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val tail = "=\"\""
        context.document.insertString(context.tailOffset, tail)
        // caret between the quotes
        context.editor.caretModel.moveToOffset(context.tailOffset + tail.length - 1)
        context.commitDocument()
    }
}
```

- [ ] **Step 2: Create the modifier insert handler**

Create `src/main/kotlin/com/github/balotias/intellijantlers/completion/ModifierInsertHandler.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/** Inserts a modifier; if it takes arguments, adds `()` and puts the caret inside. */
class ModifierInsertHandler(private val takesArguments: Boolean) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        if (!takesArguments) return
        val tail = "()"
        context.document.insertString(context.tailOffset, tail)
        context.editor.caretModel.moveToOffset(context.tailOffset + 1) // between parens
        context.commitDocument()
    }
}
```

- [ ] **Step 3: Rewrite `AntlersCompletionProvider` as the dispatcher**

Replace `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt` entirely:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.AntlersIcons
import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext

/** Classifies the caret context and offers the matching Antlers completions. */
class AntlersCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val info = AntlersCompletionContext.classify(parameters.position)
        if (info.kind == AntlersCompletionKind.NONE) return

        val project = parameters.editor.project ?: return
        val catalog = AntlersCatalogService.getInstance(project)

        when (info.kind) {
            AntlersCompletionKind.TAG_NAME ->
                for (tag in catalog.tags()) {
                    result.addElement(
                        LookupElementBuilder.create(tag.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(if (tag.isPair) "Tag (block)" else "Tag")
                            .withTailText(if (tag.description.isNotBlank()) "  ${tag.description}" else null, true)
                            .withInsertHandler(AntlersTagInsertHandler(tag.isPair))
                    )
                }

            AntlersCompletionKind.TAG_METHOD ->
                catalog.tag(info.tagHead ?: "")?.methods?.forEach { m ->
                    result.addElement(
                        LookupElementBuilder.create(m).withIcon(AntlersIcons.FILE).withTypeText("Method")
                    )
                }

            AntlersCompletionKind.PARAMETER ->
                catalog.tag(info.tagHead ?: "")?.parameters?.forEach { p ->
                    result.addElement(
                        LookupElementBuilder.create(p.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(if (p.required) "Param*" else "Param")
                            .withTailText(if (p.description.isNotBlank()) "  ${p.description}" else null, true)
                            .withInsertHandler(ParameterInsertHandler)
                    )
                }

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

            AntlersCompletionKind.NONE -> {}
        }
    }
}
```

- [ ] **Step 4: Verify the contributor registration**

Open `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContributor.kt`. It must register the provider on a broad pattern (the provider self-gates via the classifier):

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns.psiElement

class AntlersCompletionContributor : CompletionContributor(), DumbAware {
    init {
        extend(CompletionType.BASIC, psiElement(), AntlersCompletionProvider())
    }
}
```

If it already looks like this, leave it.

- [ ] **Step 5: Delete the old hardcoded tag list**

```bash
git rm src/main/kotlin/com/github/balotias/intellijantlers/completion/StatamicNativeTags.kt
```

`AntlersTagInsertHandler` previously imported nothing from `StatamicNativeTags` (it takes `isPair` as a constructor arg), so deletion is safe. If anything still references `StatamicNativeTags`, update it to use the catalog.

- [ ] **Step 6: Extend the completion tests**

Replace `src/test/kotlin/com/github/balotias/intellijantlers/AntlersCompletionTest.kt` with:

```kotlin
package com.github.balotias.intellijantlers

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("t.antlers.html", text)
        val elements = myFixture.completeBasic()
        return elements?.map { it.lookupString } ?: emptyList()
    }

    fun testTagNameCompletionInsideBraces() {
        assertTrue(lookups("{{ <caret> }}").contains("collection"))
    }

    fun testTagNamesNotOfferedInPlainHtml() {
        assertFalse(lookups("<p><caret></p>").contains("collection"))
    }

    fun testHtmlTagCompletionStillWorks() {
        assertTrue(lookups("<d<caret>").contains("div"))
    }

    fun testParameterCompletion() {
        assertTrue(lookups("{{ collection <caret> }}").contains("limit"))
    }

    fun testParameterNotOfferedForUnknownTag() {
        assertFalse(lookups("{{ somethingcustom <caret> }}").contains("limit"))
    }

    fun testModifierCompletion() {
        assertTrue(lookups("{{ title | <caret> }}").contains("upper"))
    }

    fun testTagMethodCompletion() {
        assertTrue(lookups("{{ collection:<caret> }}").contains("count"))
    }

    fun testCollectionInsertsClosingTag() {
        myFixture.configureByText("test.antlers.html", "{{ collec<caret>")
        myFixture.completeBasic()
        myFixture.checkResult("{{ collection }}\n    <caret>\n{{ /collection }}")
    }
}
```

- [ ] **Step 7: Verify + commit**

Run: `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL. (Dev env runs the suite.)

```bash
git add -A
git commit -m "PSI-driven Antlers completion: tags, methods, params, modifiers from catalog"
```

---

## Task 6: Final build, regression sweep, review

- [ ] **Step 1: Confirm no dangling references**

Run: `grep -rn "StatamicNativeTags\|AntlersCustomTagFinder" src/main src/test` → expect no matches (both removed).

- [ ] **Step 2: Clean full build**

Run: `./gradlew clean generateParser generateLexer compileKotlin compileTestKotlin` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit any final touch-ups**

```bash
git add -A
git commit -m "Antlers structural completion complete (sub-project B)" || echo "nothing to commit"
```

- [ ] **Step 4: Developer environment full test run**

Run (in dev env): `./gradlew test` → all green (`CatalogLoaderTest`, `AntlersCatalogServiceTest`, `AntlersCompletionContextTest`, `AntlersCompletionTest`, plus Plan-1 lexer/PSI/parsing tests). `./gradlew runIde` to smoke-test completion live.

---

## Self-Review (against the spec, §4 Sub-project B)

- §4.1 four completion surfaces (tag name, tag method, parameter, modifier) → Task 5 (one dispatcher provider + four branches, each with its insert behaviour) ✓
- §4.1 only inside `{{ }}` / not in plain HTML → classifier returns NONE outside a statement; HTML regression test in Task 5 ✓
- §4.2 catalog models + bundled JSON with description/docUrl → Task 1 ✓
- §4.2 `AntlersCatalogService` merges bundled + project scan, cached with modification tracker → Tasks 2–3 ✓
- §4.2 names-complete-first; rich params for high-value tags → starter `tags.json`/`modifiers.json` ✓ (extensible data)
- Retire old document-text `AntlersCompletionProvider`, `StatamicNativeTags`, `AntlersCustomTagFinder` → Tasks 2, 5 ✓
- Reuse `AntlersTagInsertHandler` for pair/single → Task 5 ✓

**Placeholder scan:** every step has complete code. The starter catalog is intentionally a curated subset (documented as extensible) — not a placeholder.

**Type consistency:** classifier returns `AntlersCompletionInfo(kind, tagHead)`; provider reads `info.kind`/`info.tagHead`; catalog exposes `tags()`/`tag(name)`/`tagNames()`/`modifiers()`; `TagDef.isPair/methods/parameters`, `ParamDef.name/description/required`, `ModifierDef.name/description/takesArguments` — all used consistently. `AntlersTagInsertHandler(isPair)` matches Plan-1's constructor.
```
