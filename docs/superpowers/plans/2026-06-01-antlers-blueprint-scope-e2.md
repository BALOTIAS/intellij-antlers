# Antlers Blueprint Scoping E2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At the top level of a template (no enclosing loop scope), narrow `{{ }}` completion/nav/docs to the blueprint of the collection(s) whose `content/collections/<handle>.yaml` `template:` points at the current file.

**Architecture:** Scan `content/collections/*.yaml` for `template:` keys (FilenameIndex + path filter, like `BlueprintScanner`), cache them in a `@Service`. A `PageBlueprintResolver` maps the current file's view path (`blog/show`) to the matching collections' `COLLECTION` namespaces. A new `AntlersFieldContext` unifies E1 loop scope with E2 page mapping (precedence: loop scope → page mapping → global) and becomes the single field-resolution authority for completion, references, and docs — replacing E1's `AntlersScopeFields`.

**Tech Stack:** Kotlin, IntelliJ Platform (`FilenameIndex`, `VfsUtilCore`, `@Service(PROJECT)` + `CachedValuesManager`), JUnit `BasePlatformTestCase` (runs via `./gradlew test`).

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-blueprint-scope-e2-design.md`

**TDD note:** This environment runs `BasePlatformTestCase` via `./gradlew test`. Run one class with
`./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`.
The full suite is currently **94/94 green**. Keep it green.

**Note on test base class:** the spec mentions a "JUnit4 unit test" for the scanner, but
`CollectionConfigScanner.scan` uses `FilenameIndex`, which needs the platform — so its test (and the
resolver test) extend `BasePlatformTestCase`, exactly like `BlueprintScannerTest`.

---

## Task 1: Collection config scanner + service

Scan `content/collections/*.yaml` for `template:` and cache the result.

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfig.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfigScanner.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfigService.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfigScannerTest.kt` (new)

- [ ] **Step 1: Write the failing scanner test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfigScannerTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CollectionConfigScannerTest : BasePlatformTestCase() {

    fun testExtractsHandleAndTemplate() {
        myFixture.addFileToProject("content/collections/blog.yaml", "title: Blog\ntemplate: blog/show\n")
        val blog = CollectionConfigScanner.scan(project).firstOrNull { it.handle == "blog" }
        assertNotNull("blog config found", blog)
        assertEquals("blog/show", blog!!.template)
    }

    fun testIgnoresConfigWithoutTemplate() {
        myFixture.addFileToProject("content/collections/pages.yaml", "title: Pages\n")
        assertTrue("no entry for template-less config", CollectionConfigScanner.scan(project).none { it.handle == "pages" })
    }

    fun testIgnoresEntryFilesInSubdirectory() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        myFixture.addFileToProject("content/collections/blog/some-entry.yaml", "template: should/ignore\n")
        val configs = CollectionConfigScanner.scan(project)
        assertEquals("only the top-level config", 1, configs.count { it.handle == "blog" })
        assertTrue("entry file ignored", configs.none { it.template == "should/ignore" })
    }

    fun testStripsQuotes() {
        myFixture.addFileToProject("content/collections/news.yaml", "template: \"news/show\"\n")
        assertEquals("news/show", CollectionConfigScanner.scan(project).first { it.handle == "news" }.template)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.CollectionConfigScannerTest" --no-configuration-cache`
Expected: FAIL — unresolved references `CollectionConfigScanner` / `CollectionConfig`.

- [ ] **Step 3: Create the config model**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfig.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

/** A Statamic collection's config (`content/collections/<handle>.yaml`): handle + default template. */
data class CollectionConfig(val handle: String, val template: String)
```

- [ ] **Step 4: Create the scanner**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfigScanner.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/** Extracts `template:` from each `content/collections/*.yaml` config. Tolerant; never throws. */
object CollectionConfigScanner {

    private const val MARKER = "/content/collections/"
    private val TEMPLATE_RE = Regex("""^\s*template:\s*['"]?([^'"\n]+?)['"]?\s*$""")

    fun scan(project: Project): List<CollectionConfig> {
        val out = mutableListOf<CollectionConfig>()
        try {
            val yamls = FilenameIndex.getAllFilesByExt(project, "yaml", GlobalSearchScope.projectScope(project))
            for (file in yamls) {
                val p = file.path.replace('\\', '/')
                val i = p.indexOf(MARKER)
                if (i < 0) continue
                val rest = p.substring(i + MARKER.length)
                if (rest.contains('/')) continue        // entries live in a subdir; configs are direct children
                if (!rest.endsWith(".yaml")) continue
                try {
                    val handle = rest.removeSuffix(".yaml")
                    val template = extractTemplate(file)
                    if (handle.isNotBlank() && template.isNotBlank()) out.add(CollectionConfig(handle, template))
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) { /* tolerant */ }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) { /* index not ready */ }
        return out
    }

    private fun extractTemplate(file: VirtualFile): String {
        val text = VfsUtilCore.loadText(file)
        for (line in text.split("\n")) {
            TEMPLATE_RE.find(line)?.let { return it.groupValues[1].trim() }
        }
        return ""
    }
}
```

- [ ] **Step 5: Create the service**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfigService.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/** Project-cached collection configs. Registered via @Service (NOT plugin.xml). */
@Service(Service.Level.PROJECT)
class CollectionConfigService(private val project: Project) {

    fun configs(): List<CollectionConfig> =
        CachedValuesManager.getManager(project).getCachedValue(project, KEY, {
            CachedValueProvider.Result.create(CollectionConfigScanner.scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    companion object {
        private val KEY = Key.create<CachedValue<List<CollectionConfig>>>("antlers.collectionConfigs")
        fun getInstance(project: Project): CollectionConfigService = project.service()
    }
}
```

- [ ] **Step 6: Run the scanner test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.CollectionConfigScannerTest" --no-configuration-cache`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfig.kt src/main/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfigScanner.kt src/main/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfigService.kt src/test/kotlin/com/github/balotias/intellijantlers/blueprint/CollectionConfigScannerTest.kt
git commit -m "Add collection-config scanner + service (template: keys)"
```

---

## Task 2: Page-blueprint resolver

Map the current file's view path to the matching collections' namespaces.

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/PageBlueprintResolver.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/PageBlueprintResolverTest.kt` (new)

- [ ] **Step 1: Write the failing resolver test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/PageBlueprintResolverTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace.Kind
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PageBlueprintResolverTest : BasePlatformTestCase() {

    /** Adds a blog config with [templateValue] + a blog/show view, returns namespaces at offset 0. */
    private fun namespacesFor(templateValue: String): List<BlueprintNamespace> {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: $templateValue\n")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", "{{ title }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(0)!!
        return PageBlueprintResolver.namespacesFor(el)
    }

    fun testSlashTemplateMatches() {
        assertEquals(listOf(BlueprintNamespace(Kind.COLLECTION, "blog")), namespacesFor("blog/show"))
    }

    fun testDotTemplateMatches() {
        assertEquals(listOf(BlueprintNamespace(Kind.COLLECTION, "blog")), namespacesFor("blog.show"))
    }

    fun testNoMatchIsEmpty() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: other/page\n")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", "{{ title }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(0)!!
        assertTrue(PageBlueprintResolver.namespacesFor(el).isEmpty())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.PageBlueprintResolverTest" --no-configuration-cache`
Expected: FAIL — unresolved reference `PageBlueprintResolver`.

- [ ] **Step 3: Create the resolver**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/PageBlueprintResolver.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.references.StatamicProject
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/** Maps the current template file to the COLLECTION namespaces whose `template:` points at it. */
object PageBlueprintResolver {

    /** COLLECTION namespaces for collections whose template renders [element]'s file. Empty if none. */
    fun namespacesFor(element: PsiElement): List<BlueprintNamespace> {
        val viewsRoot = StatamicProject.viewsRoot(element) ?: return emptyList()
        val vf = element.containingFile?.originalFile?.virtualFile ?: return emptyList()
        val viewPath = viewPathOf(vf, viewsRoot) ?: return emptyList()
        val target = normalize(viewPath)
        return CollectionConfigService.getInstance(element.project).configs()
            .filter { normalize(it.template) == target }
            .map { BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, it.handle) }
            .distinct()
    }

    /** "blog/show" from ".../resources/views/blog/show.antlers.html", or null if not under [viewsRoot]. */
    private fun viewPathOf(file: VirtualFile, viewsRoot: VirtualFile): String? {
        val rel = VfsUtilCore.getRelativePath(file, viewsRoot, '/') ?: return null
        return rel.removeSuffix(".antlers.html")
    }

    /** Statamic accepts both `blog/show` and `blog.show`; normalize separators to `/`. */
    private fun normalize(s: String): String = s.replace('.', '/')
}
```

- [ ] **Step 4: Run the resolver test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.PageBlueprintResolverTest" --no-configuration-cache`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint/PageBlueprintResolver.kt src/test/kotlin/com/github/balotias/intellijantlers/blueprint/PageBlueprintResolverTest.kt
git commit -m "Add page-blueprint resolver (view-path -> collection namespaces)"
```

---

## Task 3: Unified field context + consumer wiring

Introduce `AntlersFieldContext` (loop scope → page mapping → global), remove `AntlersScopeFields`, and route completion / references / docs through it.

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersFieldContext.kt`
- Delete: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeFields.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintFieldReference.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersPageCompletionTest.kt` (new)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersPageNavTest.kt` (new)

- [ ] **Step 1: Write the failing page-completion + page-nav tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersPageCompletionTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPageCompletionTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: Hero Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - handle: news_only\n    field:\n      type: text\n"
        )
    }

    private fun lookupsAt(path: String, textWithCaret: String): List<String> {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject(path, textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testTopLevelRestrictsToPageCollection() {
        setup()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ <caret> }}")
        assertTrue("page field hero_title: $l", l.contains("hero_title"))
        assertFalse("not another collection's field: $l", l.contains("news_only"))
        assertFalse("no loop vars on a page: $l", l.contains("index"))
        assertTrue("still offers tags: $l", l.contains("collection"))
    }

    fun testUnmappedFileFallsBackToGlobal() {
        setup()
        val l = lookupsAt("resources/views/blog/unmapped.antlers.html", "{{ <caret> }}")
        assertTrue("global includes hero_title: $l", l.contains("hero_title"))
        assertTrue("global includes news_only: $l", l.contains("news_only"))
    }
}
```

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersPageNavTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPageNavTest : BasePlatformTestCase() {

    private fun setupTwoCollectionsBothTitle() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: Blog Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: News Title\n"
        )
    }

    fun testTopLevelResolvesToPageCollection() {
        setupTwoCollectionsBothTitle()
        val text = "{{ ti<caret>tle }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertNotNull("title resolves via page mapping", target)
        assertEquals("blog.yaml", target!!.name)
    }

    fun testTopLevelDocNamesCollection() {
        setupTwoCollectionsBothTitle()
        val text = "{{ ti<caret>tle }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("names blog collection: $doc", doc!!.contains("blog"))
        assertTrue("blog title display: $doc", doc.contains("Blog Title"))
    }

    fun testLoopStillWinsOverPageMapping() {
        setupTwoCollectionsBothTitle()
        val text = "{{ collection:news }}{{ ti<caret>tle }}{{ /collection }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertEquals("loop scope (news) wins over page (blog)", "news.yaml", target!!.name)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersPageCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersPageNavTest" --no-configuration-cache`
Expected: FAIL — page mapping not wired (today top-level offers all fields / resolves first-wins globally).

- [ ] **Step 3: Create `AntlersFieldContext`**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersFieldContext.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.PageBlueprintResolver
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * The single authority for which blueprint namespaces/fields apply at an element.
 * Precedence: E1 loop scope (innermost first) → E2 page mapping → global.
 */
object AntlersFieldContext {

    /**
     * null → no loop scope and no page mapping: callers use the global fallback (all fields).
     * non-null → restrict to these namespaces, most-specific first.
     */
    fun namespacesFor(element: PsiElement): List<BlueprintNamespace>? {
        val scopes = AntlersScopeResolver.scopesAt(element)
        if (scopes.isNotEmpty()) return scopes.map { it.namespace }
        val page = PageBlueprintResolver.namespacesFor(element)
        if (page.isNotEmpty()) return page
        return null
    }

    /** Fields visible at [element]: scoped union (deduped by handle), or null for the global fallback. */
    fun fieldsInScope(element: PsiElement, project: Project): List<BlueprintField>? {
        val namespaces = namespacesFor(element) ?: return null
        val svc = BlueprintService.getInstance(project)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<BlueprintField>()
        for (ns in namespaces) {
            for (f in svc.fieldsFor(ns)) if (seen.add(f.handle)) out.add(f)
        }
        return out
    }

    /** Resolve [handle] within the applicable namespaces, else the global lookup; null if unknown. */
    fun resolveField(element: PsiElement, handle: String, project: Project): BlueprintField? {
        val svc = BlueprintService.getInstance(project)
        val namespaces = namespacesFor(element)
            ?: return svc.field(handle)
        return namespaces.firstNotNullOfOrNull { ns -> svc.fieldsFor(ns).firstOrNull { it.handle == handle } }
    }
}
```

- [ ] **Step 4: Delete `AntlersScopeFields`**

```bash
git rm src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeFields.kt
```

- [ ] **Step 5: Wire completion to `AntlersFieldContext` (and gate loop vars on loop scope)**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`.

Update imports — replace the `AntlersScopeFields` import with these two:

```kotlin
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.github.balotias.intellijantlers.scope.AntlersScopeResolver
```

(Keep the existing `import com.github.balotias.intellijantlers.scope.LoopVariables`.)

Then replace this block in the `TAG_NAME` branch:

```kotlin
                val seen = catalog.tags().mapTo(mutableSetOf()) { it.name }
                val scoped = AntlersScopeFields.fieldsInScope(parameters.position, project)
                val fields = scoped ?: BlueprintService.getInstance(project).fields()
                for (field in fields) {
                    if (seen.add(field.handle)) {
                        result.addElement(
                            LookupElementBuilder.create(field.handle)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("Field")
                                .withTailText(if (field.display.isNotBlank()) "  ${field.display}" else null, true)
                        )
                    }
                }
                if (scoped != null) {
                    for (lv in LoopVariables.ALL) {
                        if (seen.add(lv.name)) {
                            result.addElement(
                                LookupElementBuilder.create(lv.name)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Loop")
                                    .withTailText("  ${lv.description}", true)
                            )
                        }
                    }
                }
```

with:

```kotlin
                val seen = catalog.tags().mapTo(mutableSetOf()) { it.name }
                val scopedFields = AntlersFieldContext.fieldsInScope(parameters.position, project)
                val fields = scopedFields ?: BlueprintService.getInstance(project).fields()
                for (field in fields) {
                    if (seen.add(field.handle)) {
                        result.addElement(
                            LookupElementBuilder.create(field.handle)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("Field")
                                .withTailText(if (field.display.isNotBlank()) "  ${field.display}" else null, true)
                        )
                    }
                }
                // Loop-meta vars only inside an actual iterating tag (E1) — NOT for a page match.
                if (AntlersScopeResolver.scopesAt(parameters.position).isNotEmpty()) {
                    for (lv in LoopVariables.ALL) {
                        if (seen.add(lv.name)) {
                            result.addElement(
                                LookupElementBuilder.create(lv.name)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Loop")
                                    .withTailText("  ${lv.description}", true)
                            )
                        }
                    }
                }
```

(The `for (sv in SystemVariables.ALL)` block below it is unchanged.)

- [ ] **Step 6: Wire the reference to `AntlersFieldContext.resolveField`**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintFieldReference.kt` — replace the whole file with:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/** Soft reference from a `{{ }}` variable to its blueprint field declaration, scope/page aware. */
class AntlersBlueprintFieldReference(
    element: PsiElement,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {
    override fun resolve(): PsiElement? {
        val field = AntlersFieldContext.resolveField(element, handle, element.project) ?: return null
        val psiFile = PsiManager.getInstance(element.project).findFile(field.file) ?: return null
        return psiFile.findElementAt(field.offset) ?: psiFile
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
```

- [ ] **Step 7: Wire the docs provider to `AntlersFieldContext`**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`.

Replace the import line `import com.github.balotias.intellijantlers.scope.AntlersScopeResolver` with:

```kotlin
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
```

Then also remove these two now-unused imports (the head branch no longer calls `BlueprintService` directly and no longer needs the `BlueprintField` type):

```kotlin
import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintService
```

Replace the head-name branch body. Find:

```kotlin
            if (path.head == name) {
                catalog.tag(name)?.let { return tagDoc(it) }
                val scoped = scopedField(ident, name)
                val field = scoped ?: BlueprintService.getInstance(ident.project).field(name)
                field?.let { f ->
                    val type = if (f.type.isNotBlank()) " (${esc(f.type)})" else ""
                    val ns = if (scoped != null) namespaceLabel(f.namespace) else ""
                    val title = "Field <b>${esc(name)}</b>$type" +
                        (if (f.display.isNotBlank()) " — ${esc(f.display)}" else "") + ns
                    return section(title, f.display, "")
                }
                com.github.balotias.intellijantlers.blueprint.SystemVariables.ALL.firstOrNull { it.name == name }?.let { sv ->
                    return section("Variable <b>${esc(name)}</b>", sv.description, "")
                }
                return null
            }
```

Replace with:

```kotlin
            if (path.head == name) {
                catalog.tag(name)?.let { return tagDoc(it) }
                val field = AntlersFieldContext.resolveField(ident, name, ident.project)
                val scoped = AntlersFieldContext.namespacesFor(ident) != null
                field?.let { f ->
                    val type = if (f.type.isNotBlank()) " (${esc(f.type)})" else ""
                    val ns = if (scoped) namespaceLabel(f.namespace) else ""
                    val title = "Field <b>${esc(name)}</b>$type" +
                        (if (f.display.isNotBlank()) " — ${esc(f.display)}" else "") + ns
                    return section(title, f.display, "")
                }
                com.github.balotias.intellijantlers.blueprint.SystemVariables.ALL.firstOrNull { it.name == name }?.let { sv ->
                    return section("Variable <b>${esc(name)}</b>", sv.description, "")
                }
                return null
            }
```

Then delete the now-unused `scopedField` private helper (the `namespaceLabel` helper stays — it is still called above). Find and remove:

```kotlin
    /** The field for [name] within the caret's enclosing scopes (innermost first), or null. */
    private fun scopedField(ident: PsiElement, name: String): BlueprintField? {
        val scopes = AntlersScopeResolver.scopesAt(ident)
        if (scopes.isEmpty()) return null
        val svc = BlueprintService.getInstance(ident.project)
        return scopes.firstNotNullOfOrNull { s -> svc.fieldsFor(s.namespace).firstOrNull { it.handle == name } }
    }

```

- [ ] **Step 8: Run the page tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersPageCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersPageNavTest" --no-configuration-cache`
Expected: PASS (2 + 3 tests).

- [ ] **Step 9: Run the E1 regression tests — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopeResolverTest" --tests "com.github.balotias.intellijantlers.scope.AntlersScopedCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersScopedNavTest" --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableCompletionTest" --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableNavTest" --no-configuration-cache`
Expected: PASS — E1 loop scoping and v1 globals unchanged (loop scopes still take precedence; files outside a views root still hit the global fallback).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "Unify loop scope + page mapping in AntlersFieldContext; wire consumers"
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
Expected: **~106** (94 prior + 12 new: 4 scanner + 3 resolver + 2 page-completion + 3 page-nav). Exact count may differ slightly; **0 failures** is the gate.

- [ ] **Step 3: Verify production compilation**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL (no unresolved references after the `AntlersScopeFields` removal and import changes).

---

## Notes for the implementer

- **No `plugin.xml` changes** — `CollectionConfigService` is a `@Service`.
- **No grammar/lexer changes.**
- The page tests place the template at a real `resources/views/blog/show.antlers.html` path via
  `addFileToProject` (so `StatamicProject.viewsRoot` resolves), then `configureFromExistingVirtualFile`
  and move the caret. `configureByText` would put the file at the temp root with no `views` ancestor, so
  the page mapping would not engage.
- After removing `AntlersScopeFields`, the only thing that referenced it was the completion provider
  (Step 5). `AntlersScopeResolver` stays (still used by completion's loop-var gate and `AntlersFieldContext`).
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
```
