# Antlers Blueprint Variable Resolution (v1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Offer Statamic blueprint field handles + system variables as `{{ }}` completions, with go-to-definition to the blueprint field and hover docs.

**Architecture:** A hand-rolled YAML field extractor + project-cached `BlueprintService`, a curated system-variable list, and extensions to the existing completion provider, the C custom-leaf reference helper, and the documentation provider. Platform-only.

**Tech Stack:** Kotlin, IntelliJ Platform 2025.2, `BasePlatformTestCase`.

**Companion spec:** `docs/superpowers/specs/2026-06-01-antlers-blueprint-variables-design.md`

**Test note:** `./gradlew test` runs the full suite. Follow TDD: write test, run (red), implement, run (green). The full suite is currently 64/64.

---

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `blueprint/BlueprintField.kt` | Field model | Create |
| `blueprint/BlueprintScanner.kt` | Hand-roll YAML → fields | Create |
| `blueprint/BlueprintService.kt` | Cache fields (project service) | Create |
| `blueprint/SystemVariables.kt` | Curated Statamic globals | Create |
| `references/AntlersBlueprintFieldReference.kt` | Soft ref to a field | Create |
| `references/AntlersDefinitionReferenceHelper.kt` | Append blueprint-field ref to head ident | Modify |
| `completion/AntlersCompletionProvider.kt` | Offer fields + system vars in TAG_NAME branch | Modify |
| `documentation/AntlersDocumentationProvider.kt` | Field / system-var docs | Modify |
| tests under `src/test/.../blueprint/`, completion/docs/references | scanner, completion, go-to-def, docs | Create |

---

## Task 1: Blueprint model + scanner + service

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintService.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BlueprintScannerTest : BasePlatformTestCase() {

    private val blueprint = """
        title: Blog
        tabs:
          main:
            sections:
              - fields:
                  - handle: hero_title
                    field:
                      type: text
                      display: Hero Title
                  - handle: body
                    field: markdown
    """.trimIndent()

    fun testScanExtractsFields() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        val fields = BlueprintScanner.scan(project)
        val handles = fields.map { it.handle }
        assertTrue("has hero_title: $handles", handles.contains("hero_title"))
        assertTrue("has body: $handles", handles.contains("body"))
        val hero = fields.first { it.handle == "hero_title" }
        assertEquals("Hero Title", hero.display)
        assertEquals("text", hero.type)
    }

    fun testServiceDedupesAndFinds() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        myFixture.addFileToProject("resources/fieldsets/seo.yaml", "fields:\n  - handle: hero_title\n    field:\n      type: text\n")
        val svc = BlueprintService.getInstance(project)
        assertEquals("deduped by handle", 1, svc.fields().count { it.handle == "hero_title" })
        assertNotNull(svc.field("body"))
        assertNull(svc.field("does_not_exist"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*BlueprintScannerTest" --no-configuration-cache` → FAIL.

- [ ] **Step 3: Create the model**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.vfs.VirtualFile

/** A field declared in a Statamic blueprint/fieldset: the `handle` is the `{{ variable }}` name. */
data class BlueprintField(
    val handle: String,
    val display: String,
    val type: String,
    val file: VirtualFile,
    val offset: Int
)
```

- [ ] **Step 4: Create the scanner**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/** Hand-rolled extractor of Statamic blueprint/fieldset fields. Tolerant; never throws. */
object BlueprintScanner {

    private val HANDLE_RE = Regex("""^\s*(?:-\s*)?handle:\s*['"]?([A-Za-z_][A-Za-z0-9_]*)['"]?\s*$""")
    private val DISPLAY_RE = Regex("""^\s*display:\s*['"]?(.+?)['"]?\s*$""")
    private val TYPE_RE = Regex("""^\s*(?:type|field):\s*['"]?([A-Za-z_][A-Za-z0-9_]*)['"]?\s*$""")

    fun scan(project: Project): List<BlueprintField> {
        val out = mutableListOf<BlueprintField>()
        try {
            val yamls = FilenameIndex.getAllFilesByExt(project, "yaml", GlobalSearchScope.projectScope(project))
            for (file in yamls) {
                val p = file.path
                if (!p.contains("/resources/blueprints/") && !p.contains("/resources/fieldsets/")) continue
                try {
                    extract(file, out)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) { /* tolerant */ }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) { /* index not ready */ }
        return out
    }

    private fun extract(file: VirtualFile, out: MutableList<BlueprintField>) {
        val text = VfsUtilCore.loadText(file)
        val lines = text.split("\n")
        var pos = 0
        for (i in lines.indices) {
            val line = lines[i]
            val m = HANDLE_RE.find(line)
            if (m != null) {
                val handle = m.groupValues[1]
                val handleOffset = pos + line.indexOf(handle, line.indexOf("handle"))
                var display = ""
                var type = ""
                var j = i + 1
                while (j < lines.size && j < i + 8) {
                    if (HANDLE_RE.find(lines[j]) != null) break
                    if (display.isEmpty()) DISPLAY_RE.find(lines[j])?.let { display = it.groupValues[1] }
                    if (type.isEmpty()) TYPE_RE.find(lines[j])?.let { type = it.groupValues[1] }
                    j++
                }
                out.add(BlueprintField(handle, display, type, file, handleOffset))
            }
            pos += line.length + 1
        }
    }
}
```

- [ ] **Step 5: Create the service**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintService.kt`:

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

/** Project-cached blueprint fields. Registered via @Service (NOT plugin.xml). */
@Service(Service.Level.PROJECT)
class BlueprintService(private val project: Project) {

    fun fields(): List<BlueprintField> = scanned().distinctBy { it.handle }

    fun field(handle: String): BlueprintField? = scanned().firstOrNull { it.handle == handle }

    private fun scanned(): List<BlueprintField> =
        CachedValuesManager.getManager(project).getCachedValue(project, KEY, {
            CachedValueProvider.Result.create(BlueprintScanner.scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    companion object {
        private val KEY = Key.create<CachedValue<List<BlueprintField>>>("antlers.blueprintFields")
        fun getInstance(project: Project): BlueprintService = project.service()
    }
}
```

- [ ] **Step 6: Verify + commit**

Run: `./gradlew test --tests "*BlueprintScannerTest" --no-configuration-cache` → PASS (2/2). If the offset assertion or display/type parsing is off, adjust the regexes/look-ahead so both tests pass.

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint src/test/kotlin/com/github/balotias/intellijantlers/blueprint
git commit -m "Add blueprint field scanner + service (hand-rolled YAML)"
```

---

## Task 2: System variables + variable completion

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/SystemVariables.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersVariableCompletionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersVariableCompletionTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersVariableCompletionTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testOffersBlueprintFieldsAndSystemVars() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: Hero Title\n"
        )
        val l = lookups("{{ <caret> }}")
        assertTrue("offers blueprint field: $l", l.contains("hero_title"))
        assertTrue("offers system var title: $l", l.contains("title"))
        assertTrue("still offers tags: $l", l.contains("collection"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersVariableCompletionTest" --no-configuration-cache` → FAIL.

- [ ] **Step 3: Create the system variable list**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/SystemVariables.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

data class SystemVariable(val name: String, val description: String)

/** Common Statamic variables available in most template contexts. */
object SystemVariables {
    val ALL: List<SystemVariable> = listOf(
        SystemVariable("title", "The title of the entry or page."),
        SystemVariable("url", "The URL of the entry."),
        SystemVariable("permalink", "The absolute URL of the entry."),
        SystemVariable("slug", "The URL slug of the entry."),
        SystemVariable("id", "The unique id of the entry."),
        SystemVariable("date", "The entry's date."),
        SystemVariable("last_modified", "When the entry was last modified."),
        SystemVariable("status", "draft, published, or scheduled."),
        SystemVariable("published", "Whether the entry is published."),
        SystemVariable("content", "The rendered main content."),
        SystemVariable("author", "The entry's author."),
        SystemVariable("template", "The template used to render."),
        SystemVariable("layout", "The layout used to render."),
        SystemVariable("collection", "The entry's collection handle."),
        SystemVariable("mount", "The entry the collection is mounted to."),
        SystemVariable("parent", "The parent entry in a structure."),
        SystemVariable("depth", "The depth in a structure tree."),
        SystemVariable("is_current", "Whether this is the current URL."),
        SystemVariable("is_parent", "Whether this is an ancestor of the current URL."),
        SystemVariable("now", "The current date/time."),
        SystemVariable("site", "The current site handle."),
        SystemVariable("locale", "The current locale."),
        SystemVariable("current_url", "The current request URL."),
        SystemVariable("current_uri", "The current request URI."),
        SystemVariable("csrf_token", "The CSRF token value."),
        SystemVariable("environment", "The application environment.")
    )
}
```

- [ ] **Step 4: Extend the completion provider's TAG_NAME branch**

In `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`, add imports:

```kotlin
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.SystemVariables
```

Then, inside the `AntlersCompletionKind.TAG_NAME ->` branch, AFTER the existing `for (tag in catalog.tags()) { ... }` loop, append:

```kotlin
                val seen = catalog.tags().mapTo(mutableSetOf()) { it.name }
                for (field in BlueprintService.getInstance(project).fields()) {
                    if (seen.add(field.handle)) {
                        result.addElement(
                            LookupElementBuilder.create(field.handle)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("Field")
                                .withTailText(if (field.display.isNotBlank()) "  ${field.display}" else null, true)
                        )
                    }
                }
                for (sv in SystemVariables.ALL) {
                    if (seen.add(sv.name)) {
                        result.addElement(
                            LookupElementBuilder.create(sv.name)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("Variable")
                                .withTailText("  ${sv.description}", true)
                        )
                    }
                }
```

(Keep the existing tag loop and the rest of the `when` unchanged.)

- [ ] **Step 5: Verify + commit**

Run: `./gradlew test --tests "*AntlersVariableCompletionTest" --no-configuration-cache` → PASS. Also run `./gradlew test --tests "*AntlersCompletionTest" --no-configuration-cache` to confirm the existing completion tests still pass.

```bash
git add -A
git commit -m "Offer blueprint fields and system variables in {{ }} completion"
```

---

## Task 3: Variable go-to-definition + docs

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintFieldReference.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersVariableNavTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersVariableNavTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersVariableNavTest : BasePlatformTestCase() {

    private fun setupBlueprint() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: Hero Title\n"
        )
    }

    fun testFieldGoToDef() {
        setupBlueprint()
        val text = "{{ hero_<caret>title }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertNotNull("blueprint field should resolve", target)
        assertEquals("blog.yaml", target!!.name)
    }

    fun testUnknownVariableNoResolve() {
        setupBlueprint()
        val text = "{{ totally_unkno<caret>wn }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        assertNull(file.findReferenceAt(caret)?.resolve())
    }

    fun testFieldDoc() {
        setupBlueprint()
        val text = "{{ hero_<caret>title }}"
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("mentions display: $doc", doc!!.contains("Hero Title"))
    }

    fun testSystemVarDoc() {
        val text = "{{ ur<caret>l }}"
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("mentions url description: $doc", doc!!.contains("URL"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersVariableNavTest" --no-configuration-cache` → FAIL.

- [ ] **Step 3: Create the blueprint-field reference**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintFieldReference.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/** Soft reference from a `{{ }}` variable to its blueprint field declaration. */
class AntlersBlueprintFieldReference(
    element: PsiElement,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {
    override fun resolve(): PsiElement? {
        val field = BlueprintService.getInstance(element.project).field(handle) ?: return null
        val psiFile = PsiManager.getInstance(element.project).findFile(field.file) ?: return null
        return psiFile.findElementAt(field.offset) ?: psiFile
    }
    override fun getVariants(): Array<Any> = emptyArray()
}
```

- [ ] **Step 4: Wire into the definition helper**

In `references/AntlersDefinitionReferenceHelper.kt`, the `refsForIdent` currently returns the custom-tag reference for the head identifier. Change the tag-head branch so it ALSO appends a blueprint-field reference (both soft). Find the block that returns the tag reference for the head ident and replace it so it returns BOTH:

```kotlin
        // Tag head (first identifier of the name path)
        if (path.head == name && /* existing first-ident guard */ ...) {
            val refs = mutableListOf<PsiReference>()
            refs.add(AntlersPhpClassReference(element, name, isModifier = false))
            refs.add(AntlersBlueprintFieldReference(element, name))
            return refs.toTypedArray()
        }
```

(Keep the existing parameter-value/closing-tag guards and the modifier branch unchanged. The exact surrounding code is in the file — preserve its guards; only change the head-ident return to include both references.)

- [ ] **Step 5: Extend the documentation provider for variables**

In `documentation/AntlersDocumentationProvider.kt`, in the tag-head case (where `path.head == name`), when `catalog.tag(name)` is null, fall back to blueprint field / system variable docs. Replace the tag-head block:

```kotlin
            if (path.head == name) {
                catalog.tag(name)?.let { return tagDoc(it) }
                com.github.balotias.intellijantlers.blueprint.BlueprintService.getInstance(ident.project).field(name)?.let { f ->
                    val type = if (f.type.isNotBlank()) " (${esc(f.type)})" else ""
                    val title = "Field <b>${esc(name)}</b>$type" + if (f.display.isNotBlank()) " — ${esc(f.display)}" else ""
                    return section(title, f.display, "")
                }
                com.github.balotias.intellijantlers.blueprint.SystemVariables.ALL.firstOrNull { it.name == name }?.let { sv ->
                    return section("Variable <b>${esc(name)}</b>", sv.description, "")
                }
                return null
            }
```

(`section(title, description, docUrl)` and `esc()` already exist in the provider from sub-project C.)

- [ ] **Step 6: Verify + commit**

Run: `./gradlew test --tests "*AntlersVariableNavTest" --no-configuration-cache` → PASS (4/4). Also run `./gradlew test --tests "*AntlersDefinitionReferenceTest" --no-configuration-cache` and `*AntlersDocumentationProviderTest` to confirm C's tests still pass.

```bash
git add -A
git commit -m "Variable go-to-definition (to blueprint field) and field/system-var docs"
```

---

## Task 4: Full build, regression, review

- [ ] **Step 1: Full suite**

Run: `./gradlew test --no-configuration-cache` → all green (the 64 from A–D plus the new blueprint tests). Sandbox: `./gradlew compileKotlin compileTestKotlin`.

- [ ] **Step 2: Manual smoke (developer env)**

`./gradlew runIde` in a Statamic project: in `{{ <caret> }}`, blueprint fields + system vars appear; Ctrl-click a `{{ field }}` navigates to its blueprint; hover shows the field's display/type.

- [ ] **Step 3: Commit touch-ups**

```bash
git add -A
git commit -m "Antlers blueprint variable resolution v1 complete" || echo "nothing to commit"
```

---

## Self-Review (against the spec)

- §3.1 model + scanner (hand-rolled YAML, blueprints + fieldsets, tolerant) + service (cached, deduped) → Task 1 ✓
- §3.2 system variables curated list → Task 2 ✓
- §3.3 variable completion in TAG_NAME branch (fields + system vars, deduped vs tags) → Task 2 ✓
- §3.4 blueprint-field go-to-def (soft ref, wired into the C leaf helper) → Task 3 ✓
- §3.5 field / system-var docs (extend provider) → Task 3 ✓
- §5 tests for scanner, completion, go-to-def, docs → Tasks 1–3 ✓
- Platform-only (FilenameIndex/VFS, hand-rolled YAML, @Service) → all ✓

**Placeholder scan:** complete code in each step except the Task 3 Step 4 wire-in, which intentionally references "existing guards" because the implementer must preserve `AntlersDefinitionReferenceHelper`'s current guards while adding the blueprint ref — the change is precisely specified (head-ident return includes both references). **Type consistency:** `BlueprintField(handle, display, type, file, offset)`, `BlueprintService.getInstance(project).fields()/field(handle)`, `SystemVariables.ALL`, `AntlersBlueprintFieldReference(element, handle)`; reuses `section`/`esc` from the C doc provider and the `AntlersIdentLeaf.getReferences()` → `AntlersDefinitionReferenceHelper.refsForIdent` path from C.
```
