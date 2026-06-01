# Antlers Blueprint Scoping E3a — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the blueprint scanner tree-aware (each field's namespace carries its container path, with fieldset `import:` inlining) and teach `AntlersScopeResolver` that a container-typed field (`grid`/`group`/`replicator`/`bard`) used as a pair tag — `{{ rows }}…{{ /rows }}` — opens a sub-field scope.

**Architecture:** `BlueprintNamespace` gains a `path` (the chain of container handles). The scanner is rebuilt as an indentation-stack tree builder that pushes only `handle:` lines (so structural keys like `tabs:`/`sections:`/`fields:` don't nest, but a container field's sub-fields do), and a two-phase pass inlines `import:`. The resolver, when an opener's head resolves (in the current scope or via E2 page mapping) to a container-typed field, pushes a sub-field scope — resolving fields directly against `BlueprintService`/`PageBlueprintResolver` (never `AntlersFieldContext`) to stay acyclic. Consumers already consume scopes, so sub-field completion/nav/docs work for free (only a docs path-label tweak).

**Tech Stack:** Kotlin, IntelliJ Platform (`FilenameIndex`, `VfsUtilCore`, PSI), JUnit `BasePlatformTestCase` (runs via `./gradlew test`).

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-blueprint-scope-e3a-design.md`

**TDD note:** This environment runs `BasePlatformTestCase` via `./gradlew test`. One class:
`./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`.
Full suite is currently **106/106 green**. Keep it green.

**Key insight (put this in your head before Task 1):** the indentation stack tracks ONLY `handle:` (and `import:`) lines. Statamic's structural keys (`tabs:`, `sections:`, `fields:`, `field:`, `sets:`) have no `handle:`, so they never push a frame — which is exactly why a collection's top-level fields (nested under `tabs/…/fields`) get `path=[]`, while a Grid sub-field (nested under a field that *does* have a `handle:`) gets `path=[gridHandle]`.

---

## Task 1: Path-bearing namespace + tree-aware scanner (no imports yet)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespace.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt` (extend)

- [ ] **Step 1: Write the failing tree tests**

Add to `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt` (inside the class). First add this fixture constant near the top of the class (next to the existing `blueprint` val):

```kotlin
    private val gridBlueprint = """
        tabs:
          main:
            sections:
              - fields:
                  - handle: rows
                    field:
                      type: grid
                      fields:
                        - handle: caption
                          field:
                            type: text
                            display: Caption
                        - handle: gallery
                          field:
                            type: grid
                            fields:
                              - handle: photo_alt
                                field:
                                  type: text
                  - handle: title
                    field: text
    """.trimIndent()
```

Then add these test methods:

```kotlin
    fun testNestedFieldCarriesContainerPath() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", gridBlueprint)
        val fields = BlueprintScanner.scan(project)
        assertEquals(emptyList<String>(), fields.first { it.handle == "rows" }.namespace.path)
        assertEquals(emptyList<String>(), fields.first { it.handle == "title" }.namespace.path)
        assertEquals(listOf("rows"), fields.first { it.handle == "caption" }.namespace.path)
        assertEquals(listOf("rows", "gallery"), fields.first { it.handle == "photo_alt" }.namespace.path)
    }

    fun testFlattenBugFixedTopLevelExcludesNested() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", gridBlueprint)
        val svc = BlueprintService.getInstance(project)
        val top = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"))
        assertTrue("top-level has rows", top.any { it.handle == "rows" })
        assertTrue("top-level has title", top.any { it.handle == "title" })
        assertFalse("caption is nested, not top-level", top.any { it.handle == "caption" })
        val grid = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog", listOf("rows")))
        assertTrue("rows scope has caption", grid.any { it.handle == "caption" })
        assertTrue("rows scope has gallery", grid.any { it.handle == "gallery" })
        assertFalse("rows scope excludes deeper photo_alt", grid.any { it.handle == "photo_alt" })
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.BlueprintScannerTest" --no-configuration-cache`
Expected: FAIL — `BlueprintNamespace` has no `path` param; nested fields currently flatten to `path=[]`.

- [ ] **Step 3: Add `path` to `BlueprintNamespace`**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespace.kt` — change the class header line:

```kotlin
data class BlueprintNamespace(val kind: Kind, val handle: String) {
```

to:

```kotlin
data class BlueprintNamespace(val kind: Kind, val handle: String, val path: List<String> = emptyList()) {
```

(Everything else in the file — `UNKNOWN`, `fromPath`, `dirAfter`, `fileAfter` — is unchanged; they construct with two args, so `path` defaults to empty.)

- [ ] **Step 4: Rewrite `BlueprintScanner.extract` as an indentation tree**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`. Replace the entire `extract` function with the version below, and add the `leadingWs` helper. (The `scan` function, the three regexes, and the imports stay as they are.)

```kotlin
    private fun extract(file: VirtualFile, out: MutableList<BlueprintField>) {
        val text = VfsUtilCore.loadText(file)
        val base = BlueprintNamespace.fromPath(file.path)
        val lines = text.split("\n")
        val stack = ArrayDeque<Pair<Int, String>>()   // (indent, handle), deepest last
        var pos = 0
        for (i in lines.indices) {
            val line = lines[i]
            val m = HANDLE_RE.find(line)
            if (m != null) {
                val indent = leadingWs(line)
                while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
                val handle = m.groupValues[1]
                val path = stack.map { it.second }
                val handleOffset = pos + (m.groups[1]?.range?.first ?: 0)
                var display = ""
                var type = ""
                var j = i + 1
                while (j < lines.size && j < i + 8) {
                    if (HANDLE_RE.find(lines[j]) != null) break
                    if (display.isEmpty()) DISPLAY_RE.find(lines[j])?.let { display = it.groupValues[1] }
                    if (type.isEmpty()) TYPE_RE.find(lines[j])?.let { type = it.groupValues[1] }
                    j++
                }
                out.add(BlueprintField(handle, display, type, file, handleOffset, base.copy(path = path)))
                stack.addLast(indent to handle)
            }
            pos += line.length + 1
        }
    }

    /** Number of leading space/tab characters on [line] (the indentation column). */
    private fun leadingWs(line: String): Int {
        var n = 0
        while (n < line.length && (line[n] == ' ' || line[n] == '\t')) n++
        return n
    }
```

- [ ] **Step 5: Run the scanner tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.BlueprintScannerTest" --no-configuration-cache`
Expected: PASS — including the existing flat-blueprint tests (their fields nest only under structural keys, so `path=[]`).

- [ ] **Step 6: Run E1/E2 regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.*" --tests "com.github.balotias.intellijantlers.blueprint.*" --no-configuration-cache`
Expected: PASS — flat blueprints keep every field at `path=[]`, so scope/page/variable behavior is unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespace.kt src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt
git commit -m "Make blueprint scanner tree-aware (namespace path per field)"
```

---

## Task 2: Fieldset `import:` inlining

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt` (extend)

- [ ] **Step 1: Write the failing import tests**

Add to `BlueprintScannerTest`:

```kotlin
    fun testImportInlinesFieldsetFields() {
        myFixture.addFileToProject(
            "resources/fieldsets/seo.yaml",
            "fields:\n  - handle: meta_title\n    field:\n      type: text\n      display: Meta Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "tabs:\n  main:\n    sections:\n      - fields:\n          - import: seo\n          - handle: body\n            field: text\n"
        )
        val svc = BlueprintService.getInstance(project)
        val top = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"))
        assertTrue("imported meta_title at top level", top.any { it.handle == "meta_title" })
        assertTrue("own body field", top.any { it.handle == "body" })
        assertEquals("go-to-def lands in the fieldset", "seo.yaml", top.first { it.handle == "meta_title" }.file.name)
    }

    fun testNestedImportUnderContainer() {
        myFixture.addFileToProject(
            "resources/fieldsets/seo.yaml",
            "fields:\n  - handle: meta_title\n    field:\n      type: text\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "tabs:\n  main:\n    sections:\n      - fields:\n          - handle: rows\n            field:\n              type: grid\n              fields:\n                - import: seo\n"
        )
        val svc = BlueprintService.getInstance(project)
        val grid = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog", listOf("rows")))
        assertTrue("imported field nested under the container", grid.any { it.handle == "meta_title" })
    }

    fun testCyclicImportDoesNotHang() {
        myFixture.addFileToProject("resources/fieldsets/a.yaml", "fields:\n  - import: b\n  - handle: a_field\n    field: text\n")
        myFixture.addFileToProject("resources/fieldsets/b.yaml", "fields:\n  - import: a\n  - handle: b_field\n    field: text\n")
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "tabs:\n  main:\n    sections:\n      - fields:\n          - import: a\n"
        )
        val svc = BlueprintService.getInstance(project)
        val top = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"))
        assertTrue("a's own field inlined", top.any { it.handle == "a_field" })
        assertTrue("b's field inlined via a", top.any { it.handle == "b_field" })
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.BlueprintScannerTest" --no-configuration-cache`
Expected: FAIL — `import:` lines are ignored today, so `meta_title`/`a_field`/`b_field` are absent.

- [ ] **Step 3: Add the import regex and marker model**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`. Add the regex next to the existing ones:

```kotlin
    private val IMPORT_RE = Regex("""^\s*(?:-\s*)?import:\s*['"]?([A-Za-z0-9_-]+)['"]?\s*$""")
```

Add this marker type inside the `object BlueprintScanner` (e.g. just below the regexes):

```kotlin
    /** An `import: <fieldset>` directive at [atPath] within the blueprint/fieldset of [baseNs]. */
    private data class ImportMarker(val baseNs: BlueprintNamespace, val atPath: List<String>, val imported: String)
```

- [ ] **Step 4: Collect markers in `scan`/`extract`**

Replace the `scan` function with this version (it now collects markers and expands them):

```kotlin
    fun scan(project: Project): List<BlueprintField> {
        val raw = mutableListOf<BlueprintField>()
        val markers = mutableListOf<ImportMarker>()
        try {
            val yamls = FilenameIndex.getAllFilesByExt(project, "yaml", GlobalSearchScope.projectScope(project))
            for (file in yamls) {
                val p = file.path
                if (!p.contains("/resources/blueprints/") && !p.contains("/resources/fieldsets/")) continue
                try {
                    extract(file, raw, markers)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) { /* tolerant */ }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) { /* index not ready */ }
        return expandImports(raw, markers)
    }
```

Change the `extract` signature and add import handling. Replace the `extract` function header line and the `if (m != null) { … }` block's surroundings so it reads:

```kotlin
    private fun extract(file: VirtualFile, out: MutableList<BlueprintField>, markers: MutableList<ImportMarker>) {
        val text = VfsUtilCore.loadText(file)
        val base = BlueprintNamespace.fromPath(file.path)
        val lines = text.split("\n")
        val stack = ArrayDeque<Pair<Int, String>>()   // (indent, handle), deepest last
        var pos = 0
        for (i in lines.indices) {
            val line = lines[i]
            val m = HANDLE_RE.find(line)
            if (m != null) {
                val indent = leadingWs(line)
                while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
                val handle = m.groupValues[1]
                val path = stack.map { it.second }
                val handleOffset = pos + (m.groups[1]?.range?.first ?: 0)
                var display = ""
                var type = ""
                var j = i + 1
                while (j < lines.size && j < i + 8) {
                    if (HANDLE_RE.find(lines[j]) != null) break
                    if (display.isEmpty()) DISPLAY_RE.find(lines[j])?.let { display = it.groupValues[1] }
                    if (type.isEmpty()) TYPE_RE.find(lines[j])?.let { type = it.groupValues[1] }
                    j++
                }
                out.add(BlueprintField(handle, display, type, file, handleOffset, base.copy(path = path)))
                stack.addLast(indent to handle)
            } else {
                val im = IMPORT_RE.find(line)
                if (im != null) {
                    val indent = leadingWs(line)
                    while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
                    markers.add(ImportMarker(base, stack.map { it.second }, im.groupValues[1]))
                }
            }
            pos += line.length + 1
        }
    }
```

- [ ] **Step 5: Add the two-phase expansion**

Add these functions to `object BlueprintScanner` (e.g. after `extract`):

```kotlin
    /** Inline every blueprint-level import marker by grafting the imported fieldset's expanded fields. */
    private fun expandImports(raw: List<BlueprintField>, markers: List<ImportMarker>): List<BlueprintField> {
        val out = raw.toMutableList()
        for (m in markers) {
            if (m.baseNs.kind == BlueprintNamespace.Kind.FIELDSET) continue  // fieldset→fieldset handled by recursion
            for (field in expandFieldset(m.imported, raw, markers, mutableSetOf())) {
                out.add(field.copy(namespace = m.baseNs.copy(path = m.atPath + field.namespace.path)))
            }
        }
        return out
    }

    /**
     * The fully-expanded fields of fieldset [handle], with namespace paths RELATIVE to the fieldset root
     * (only `.namespace.path` matters to callers; kind/handle are re-stamped at the graft site).
     * Cycle-guarded via [visited].
     */
    private fun expandFieldset(
        handle: String,
        raw: List<BlueprintField>,
        markers: List<ImportMarker>,
        visited: MutableSet<String>
    ): List<BlueprintField> {
        if (!visited.add(handle)) return emptyList()       // cycle: stop
        val result = mutableListOf<BlueprintField>()
        raw.filterTo(result) {
            it.namespace.kind == BlueprintNamespace.Kind.FIELDSET && it.namespace.handle == handle
        }
        for (m in markers) {
            if (m.baseNs.kind == BlueprintNamespace.Kind.FIELDSET && m.baseNs.handle == handle) {
                for (field in expandFieldset(m.imported, raw, markers, visited)) {
                    result.add(field.copy(namespace = field.namespace.copy(path = m.atPath + field.namespace.path)))
                }
            }
        }
        visited.remove(handle)
        return result
    }
```

- [ ] **Step 6: Run the import tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.BlueprintScannerTest" --no-configuration-cache`
Expected: PASS (import inlines, nested import, cyclic terminates).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt
git commit -m "Inline fieldset import: references in the blueprint scanner"
```

---

## Task 3: Container-field scope in the resolver + docs path label

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersContainerScopeTest.kt` (new)

- [ ] **Step 1: Write the failing container-scope tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersContainerScopeTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersContainerScopeTest : BasePlatformTestCase() {

    private val gridBlueprint = """
        tabs:
          main:
            sections:
              - fields:
                  - handle: rows
                    field:
                      type: grid
                      fields:
                        - handle: caption
                          field:
                            type: text
                            display: Caption
                        - handle: gallery
                          field:
                            type: grid
                            fields:
                              - handle: photo_alt
                                field:
                                  type: text
                  - handle: title
                    field: text
    """.trimIndent()

    private fun addBlueprint() =
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", gridBlueprint)

    private fun addPageConfig() =
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")

    /** Completion lookups at the caret in a file added at [path]. */
    private fun lookupsAt(path: String, textWithCaret: String): List<String> {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject(path, textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testGridScopeInsideCollectionLoop() {
        addBlueprint()
        // collection:blog gives the namespace; rows (grid) opens its sub-field scope.
        val l = lookupsAt(
            "page.antlers.html",
            "{{ collection:blog }}{{ rows }}{{ <caret> }}{{ /rows }}{{ /collection }}"
        )
        assertTrue("grid sub-field caption: $l", l.contains("caption"))
        assertTrue("loop var inside a grid: $l", l.contains("index"))
    }

    fun testTopLevelGridViaPageMappingExcludesSiblings() {
        addBlueprint()
        addPageConfig()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ rows }}{{ <caret> }}{{ /rows }}")
        assertTrue("grid sub-field caption: $l", l.contains("caption"))
        assertFalse("grid scope excludes the page sibling title: $l", l.contains("title"))
    }

    fun testNestedGridScope() {
        addBlueprint()
        addPageConfig()
        val l = lookupsAt(
            "resources/views/blog/show.antlers.html",
            "{{ rows }}{{ gallery }}{{ <caret> }}{{ /gallery }}{{ /rows }}"
        )
        assertTrue("deeper sub-field photo_alt: $l", l.contains("photo_alt"))
    }

    fun testNavOnGridSubField() {
        addBlueprint()
        addPageConfig()
        val text = "{{ rows }}{{ cap<caret>tion }}{{ /rows }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertNotNull("caption resolves", target)
        assertEquals("blog.yaml", target!!.name)
    }

    fun testDocLabelShowsContainerPath() {
        addBlueprint()
        addPageConfig()
        val text = "{{ rows }}{{ cap<caret>tion }}{{ /rows }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("doc names the rows container: $doc", doc!!.contains("rows"))
        assertTrue("doc shows the display: $doc", doc.contains("Caption"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersContainerScopeTest" --no-configuration-cache`
Expected: FAIL — the resolver doesn't open a scope for `rows` today, so `caption`/`photo_alt` aren't offered/resolved and the doc lacks the path.

- [ ] **Step 3: Extend `AntlersScopeResolver` with container detection**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`.

Add imports (next to the existing ones):

```kotlin
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.PageBlueprintResolver
```

Add the container-type set next to `ITERATING`:

```kotlin
    private val CONTAINER_TYPES = setOf("grid", "group", "replicator", "bard")
```

In `scopesAt`, insert the container branch **between** the `if (head in ITERATING) { … }` block and the final catalog-pair-tag line. Find:

```kotlin
            // Any other catalog pair tag (cache, section, or an iterating tag with no handle):
            // push a transparent frame so its closer balances. Non-pair tags / plain variables
            // have no closer, so we must NOT push them.
            if (catalog?.tag(head)?.isPair == true) stack.addLast(Frame(head, null, false, null))
```

and insert immediately before it:

```kotlin
            // Container-typed blueprint field (grid/group/replicator/bard) used as a pair tag
            // → open a sub-field scope. Resolve the field against the namespaces active so far
            // (or the E2 page mapping at top level), NEVER via AntlersFieldContext (which calls us).
            val containerNs = containerScope(head, stack, stmt, project)
            if (containerNs != null) {
                val alias = paramValue(stmt, setOf("as"))
                stack.addLast(
                    if (alias != null) Frame(head, containerNs, false, alias)
                    else Frame(head, containerNs, true, null)
                )
                continue
            }

```

Then add these two private helpers (e.g. after `popTo`):

```kotlin
    /** The namespace a container-typed field [head] opens, or null if [head] isn't a container here. */
    private fun containerScope(
        head: String,
        stack: ArrayDeque<Frame>,
        stmt: AntlersStatement,
        project: com.intellij.openapi.project.Project
    ): BlueprintNamespace? {
        val current = currentNamespaces(stack, stmt)
        if (current.isEmpty()) return null
        val svc = BlueprintService.getInstance(project)
        val field = current.firstNotNullOfOrNull { ns -> svc.fieldsFor(ns).firstOrNull { it.handle == head } }
            ?: return null
        if (field.type.lowercase() !in CONTAINER_TYPES) return null
        return field.namespace.copy(path = field.namespace.path + head)
    }

    /** Namespaces valid at this point in the walk: active frames (innermost first), else E2 page mapping. */
    private fun currentNamespaces(stack: ArrayDeque<Frame>, stmt: AntlersStatement): List<BlueprintNamespace> {
        val active = stack.filter { it.active && it.namespace != null }.reversed().map { it.namespace!! }
        if (active.isNotEmpty()) return active
        return PageBlueprintResolver.namespacesFor(stmt)
    }
```

- [ ] **Step 4: Make the docs label render the namespace path**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`. Replace the `namespaceLabel` helper:

```kotlin
    /** " · collection: blog" style suffix; empty for the UNKNOWN namespace. */
    private fun namespaceLabel(ns: BlueprintNamespace): String {
        if (ns.kind == BlueprintNamespace.Kind.UNKNOWN) return ""
        val kind = ns.kind.name.lowercase()
        return " · ${esc(kind)}: ${esc(ns.handle)}"
    }
```

with:

```kotlin
    /** " · collection: blog › rows" style suffix; empty for the UNKNOWN namespace. */
    private fun namespaceLabel(ns: BlueprintNamespace): String {
        if (ns.kind == BlueprintNamespace.Kind.UNKNOWN) return ""
        val kind = ns.kind.name.lowercase()
        val pathSuffix = if (ns.path.isEmpty()) "" else " › " + ns.path.joinToString(" › ") { esc(it) }
        return " · ${esc(kind)}: ${esc(ns.handle)}$pathSuffix"
    }
```

- [ ] **Step 5: Run the container-scope tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersContainerScopeTest" --no-configuration-cache`
Expected: PASS (5 tests).

- [ ] **Step 6: Run the E1/E2 scope regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopeResolverTest" --tests "com.github.balotias.intellijantlers.scope.AntlersScopedCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersScopedNavTest" --tests "com.github.balotias.intellijantlers.scope.AntlersPageCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersPageNavTest" --no-configuration-cache`
Expected: PASS — those fixtures define no container fields (or no blueprints at all), so `containerScope` returns null and behavior is unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersContainerScopeTest.kt
git commit -m "Open sub-field scope for container blueprint fields; docs show path"
```

---

## Task 4: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew test --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm no failures**

Run: `grep -lo 'failures="[1-9]' build/test-results/test/*.xml | wc -l`
Expected: `0`.

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc`
Expected: **~116** (106 prior + ~10 new: 2 tree + 3 import + 5 container). Exact count may differ; **0 failures** is the gate.

- [ ] **Step 3: Verify production compilation**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

---

## Notes for the implementer

- **No `plugin.xml`, grammar, or lexer changes**, and **no consumer rewrites** — completion/references already consume `BlueprintScope`s through `AntlersFieldContext`.
- The indentation stack tracks ONLY `handle:`/`import:` lines, so structural keys (`tabs:`/`sections:`/`fields:`/`field:`/`sets:`) never nest. This is why a collection's top-level fields stay `path=[]` while a Grid sub-field gets `path=[gridHandle]`. Replicator/Bard sub-fields live under `sets: › <set> › fields:`; since every set's `fields:` is more-indented than the container handle, all sets' sub-fields share the container path (the documented union).
- `currentNamespaces` calls `PageBlueprintResolver` only when no frame is active; for files not under a `resources/views` root (e.g. `configureByText`/`page.antlers.html`), that returns empty fast — so container detection there only works when an explicit loop (e.g. `{{ collection:blog }}`) supplies the namespace, which is what `testGridScopeInsideCollectionLoop` exercises.
- **Acyclic dependency:** `AntlersScopeResolver` calls `BlueprintService`/`PageBlueprintResolver` directly with its partial stack — never `AntlersFieldContext`.
- A container field used as a bare `{{ rows }}` with no closer will over-scope subsequent content (the documented "unbalanced template" tolerance) — acceptable for v1.
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
