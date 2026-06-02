# Antlers Parameter-Value Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete tag-parameter *values* — partial paths for `partial:src=`, collection/taxonomy handles for `from=`/`in=`/…, field names for `sort=`, and `true`/`false` for known boolean params.

**Architecture:** Extend `AntlersCompletionContext` with a `PARAMETER_VALUE` kind (the existing `T_EQUALS` branch, today `NONE`, now resolves the param name + tag). A new isolated `AntlersParamValueSource` maps `(tag, param)` to values, backed by reusable listers on `StatamicProject` (partials — extracted DRY from `AntlersPartialReference`, plus collection/taxonomy handle dir-scans) and `AntlersFieldContext` (sort fields). The provider routes `PARAMETER_VALUE` and fixes the in-quote prefix matcher.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`CompletionProvider`, `LookupElementBuilder`, `CompletionUtilCore`), `BasePlatformTestCase`. Tests run via `./gradlew test`; the build cache returns cached results, so use `--rerun-tasks` and gate on `build/test-results/test/*.xml`.

**Existing facts (verified):**
- `AntlersCompletionContext.classify` keys on the previous significant leaf; `T_EQUALS -> NONE` at `AntlersCompletionContext.kt:46`. `AntlersCompletionInfo(kind, tagHead=null, pathPrefix=emptyList())`.
- Completing inside `param="<caret>"` → `position` is the `T_STRING`, prev leaf is `T_EQUALS`. Unquoted `param=<caret>` → dummy `T_IDENT`, prev leaf `T_EQUALS`. Conditions use `==` (`T_OP`), never `T_EQUALS`.
- `AntlersCompletionContributor` extends `psiElement()` (fires everywhere; `classify` is the gate).
- `StatamicProject.viewsRoot(element)` → `resources/views`; its `.parent` is `resources`. Partial-listing logic is inline in `AntlersPartialReference.getVariants`/`collectPartials`/`getRelativePath`.
- `AntlersFieldContext.fieldsInScope(position, project): List<BlueprintField>?`; `BlueprintService.getInstance(project).fields()`.
- Test harness `AntlersCompletionContextTest` has `classifyAt(text)` (returns full `AntlersCompletionInfo`) and `kindAt(text)` (returns kind, inserts the dummy identifier). `testNoneInValue` (line 44) asserts `NONE` for `{{ collection limit="<caret>" }}` — **must be updated** (it becomes `PARAMETER_VALUE`).

---

### Task 1: `PARAMETER_VALUE` context classification

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContextTest.kt`

- [ ] **Step 1: Update/add tests**

In `AntlersCompletionContextTest`, REPLACE the `testNoneInValue` line with the following methods (keep everything else):

```kotlin
    fun testParameterValueInString() {
        val info = classifyAt("{{ collection from=\"<caret>\" }}")
        assertEquals(AntlersCompletionKind.PARAMETER_VALUE, info.kind)
        assertEquals("from", info.paramName)
        assertEquals("collection", info.tagHead)
    }

    fun testParameterValueUnquoted() =
        assertEquals(AntlersCompletionKind.PARAMETER_VALUE, kindAt("{{ collection from=<caret> }}"))

    fun testParameterValueAfterMethod() =
        assertEquals(AntlersCompletionKind.PARAMETER_VALUE, kindAt("{{ collection:blog sort=<caret> }}"))

    fun testBoundParamIsNone() =
        assertEquals(AntlersCompletionKind.NONE, kindAt("{{ collection :from=<caret> }}"))
```

- [ ] **Step 2: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersCompletionContextTest"`
Expected: FAIL — `PARAMETER_VALUE` / `paramName` don't exist; the value branch still returns `NONE`.

- [ ] **Step 3: Implement**

In `AntlersCompletionContext.kt`, change the enum and info type:

```kotlin
enum class AntlersCompletionKind { TAG_NAME, TAG_METHOD, PARAMETER, PARAMETER_VALUE, MODIFIER, FIELD_PATH, NONE }

data class AntlersCompletionInfo(
    val kind: AntlersCompletionKind,
    val tagHead: String? = null,
    val pathPrefix: List<String> = emptyList(),
    val paramName: String? = null
)
```

Replace the `T_EQUALS -> AntlersCompletionInfo(AntlersCompletionKind.NONE)` branch with:

```kotlin
            AntlersTypes.T_EQUALS -> {
                val nameLeaf = prevSignificantLeaf(prev, statement)
                if (nameLeaf?.node?.elementType != AntlersTypes.T_IDENT) {
                    AntlersCompletionInfo(AntlersCompletionKind.NONE)
                } else if (prevSignificantLeaf(nameLeaf, statement)?.node?.elementType == AntlersTypes.T_COLON) {
                    // Bound param `:name="$var"` takes a variable expression, not a literal value.
                    AntlersCompletionInfo(AntlersCompletionKind.NONE)
                } else {
                    headOf(statement)?.let {
                        AntlersCompletionInfo(AntlersCompletionKind.PARAMETER_VALUE, it, paramName = nameLeaf.text)
                    } ?: AntlersCompletionInfo(AntlersCompletionKind.NONE)
                }
            }
```

- [ ] **Step 4: Run, confirm pass (all `AntlersCompletionContextTest` green)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersCompletionContextTest"`
Expected: PASS. (The provider doesn't yet handle the new kind — fine; the provider's `when` already returns for unknown kinds via the `NONE` early-return? No: add a temporary no-op. Actually `AntlersCompletionProvider`'s `when(info.kind)` is exhaustive over the enum, so adding `PARAMETER_VALUE` will make it NOT compile until Task 4. To keep Task 1 compiling, add a `AntlersCompletionKind.PARAMETER_VALUE -> {}` no-op case to the provider's `when` now, with a `// filled in Task 4` comment.)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContextTest.kt
git commit -m "Classify parameter-value position (PARAMETER_VALUE kind + paramName)"
```

---

### Task 2: `StatamicProject` listers (partials + collection/taxonomy handles)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/StatamicProject.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReference.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/StatamicProjectListersTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StatamicProjectListersTest : BasePlatformTestCase() {

    private fun template(): PsiFile {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "<div>card</div>")
        myFixture.addFileToProject("resources/views/shared/hero.antlers.html", "<div>hero</div>")
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", "fields: []\n")
        myFixture.addFileToProject("resources/blueprints/collections/news/news.yaml", "fields: []\n")
        myFixture.addFileToProject("resources/blueprints/taxonomies/topics/topics.yaml", "fields: []\n")
        return myFixture.addFileToProject("resources/views/page.antlers.html", "{{ }}")
    }

    fun testListPartials() {
        val el = template().firstChild!!
        val partials = StatamicProject.listPartials(el)
        assertTrue("blog/card: $partials", partials.contains("blog/card"))
        assertTrue("shared/hero: $partials", partials.contains("shared/hero"))
    }

    fun testListCollectionHandles() {
        val el = template().firstChild!!
        val handles = StatamicProject.listCollectionHandles(el)
        assertTrue("blog: $handles", handles.contains("blog"))
        assertTrue("news: $handles", handles.contains("news"))
    }

    fun testListTaxonomyHandles() {
        val el = template().firstChild!!
        assertTrue(StatamicProject.listTaxonomyHandles(el).contains("topics"))
    }
}
```

- [ ] **Step 2: Run, confirm failure (methods missing)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.StatamicProjectListersTest"`
Expected: FAIL — unresolved `listPartials`/`listCollectionHandles`/`listTaxonomyHandles`.

- [ ] **Step 3: Add the listers to `StatamicProject.kt`** (append inside the object)

```kotlin
    /** All partial paths under `resources/views` (e.g. "blog/card"), extension-stripped. */
    fun listPartials(element: PsiElement): List<String> {
        val root = viewsRoot(element) ?: return emptyList()
        val out = mutableListOf<String>()
        collectPartials(root, root, out)
        return out
    }

    /** Collection handles = subdirectory names of `resources/blueprints/collections`. */
    fun listCollectionHandles(element: PsiElement): List<String> = blueprintSubdirs(element, "collections")

    /** Taxonomy handles = subdirectory names of `resources/blueprints/taxonomies`. */
    fun listTaxonomyHandles(element: PsiElement): List<String> = blueprintSubdirs(element, "taxonomies")

    private fun blueprintSubdirs(element: PsiElement, kind: String): List<String> {
        val resources = viewsRoot(element)?.parent ?: return emptyList()
        val dir = resources.findChild("blueprints")?.findChild(kind) ?: return emptyList()
        return dir.children.filter { it.isDirectory }.map { it.name }
    }

    private fun collectPartials(root: VirtualFile, dir: VirtualFile, out: MutableList<String>) {
        for (child in dir.children) {
            if (child.isDirectory) {
                collectPartials(root, child, out)
            } else if (child.name.endsWith(".antlers.html") || child.name.endsWith(".html")) {
                val rel = relativePath(root, child) ?: continue
                out.add(rel.removeSuffix(".antlers.html").removeSuffix(".html"))
            }
        }
    }

    private fun relativePath(root: VirtualFile, file: VirtualFile): String? {
        if (!file.path.startsWith(root.path)) return null
        return file.path.removePrefix("${root.path}/")
    }
```

(`VirtualFile` is already imported in this file.)

- [ ] **Step 4: Refactor `AntlersPartialReference.getVariants` to delegate (behavior-preserving)**

Replace the `getVariants` body and DELETE the now-duplicate private `collectPartials` (keep the
reference's own `getRelativePath` — it is still used by `relativePathMinusExt` in `bindToElement`):

```kotlin
    override fun getVariants(): Array<Any> =
        StatamicProject.listPartials(element).toTypedArray()
```

Remove the `private fun collectPartials(...)` method from `AntlersPartialReference` (it now lives in
`StatamicProject`). Leave `getRelativePath` and `relativePathMinusExt` intact.

- [ ] **Step 5: Run, confirm pass + no partial regression**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.StatamicProjectListersTest" --tests "com.github.balotias.intellijantlers.references.AntlersPartialReferenceTest" --tests "com.github.balotias.intellijantlers.references.AntlersPartialRefactoringTest"`
Expected: PASS (listers green; partial reference/refactoring suites unchanged).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/StatamicProject.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReference.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/references/StatamicProjectListersTest.kt
git commit -m "Add StatamicProject partial + collection/taxonomy handle listers (DRY)"
```

---

### Task 3: `AntlersParamValueSource`

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamValueSource.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamValueSourceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersParamValueSourceTest : BasePlatformTestCase() {

    private fun setup(): PsiFile {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "<div>card</div>")
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n")
        myFixture.addFileToProject("resources/blueprints/taxonomies/topics/topics.yaml", "fields: []\n")
        return myFixture.addFileToProject("resources/views/page.antlers.html", "{{ }}")
    }

    private fun values(tag: String?, param: String?): List<String> {
        val el = setup().firstChild!!
        return AntlersParamValueSource.valuesFor(tag, param, el, project).map { it.text }
    }

    fun testPartialSrc() = assertTrue(values("partial", "src").contains("blog/card"))

    fun testCollectionFrom() {
        val v = values("collection", "from")
        assertTrue("blog: $v", v.contains("blog"))
        assertTrue("topics: $v", v.contains("topics"))
    }

    fun testSort() {
        val v = values("collection", "sort")
        assertTrue("title: $v", v.contains("title"))
        assertTrue("title:asc: $v", v.contains("title:asc"))
        assertTrue("date: $v", v.contains("date"))
    }

    fun testBoolean() = assertEquals(listOf("true", "false"), values("collection", "paginate"))

    fun testUnknownParamEmpty() = assertTrue(values("collection", "limit").isEmpty())
}
```

- [ ] **Step 2: Run, confirm failure (class missing)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersParamValueSourceTest"`
Expected: FAIL — `AntlersParamValueSource` does not exist.

- [ ] **Step 3: Implement**

```kotlin
package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.references.StatamicProject
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

data class ParamValue(val text: String, val typeText: String)

/** Maps a tag parameter to its candidate values. Pure routing; no PSI/UI concerns. */
object AntlersParamValueSource {

    private val HANDLE_PARAMS = setOf("from", "in", "folder", "collection", "use", "handle")
    private val SORT_PARAMS = setOf("sort", "order_by")
    private val BOOLEAN_PARAMS = setOf("paginate", "show_unpublished", "show_future", "show_past", "disable_paging")
    private val SORT_KEYS = listOf("title", "date", "random")

    fun valuesFor(tagHead: String?, paramName: String?, position: PsiElement, project: Project): List<ParamValue> {
        val param = paramName?.lowercase() ?: return emptyList()
        return when {
            param == "src" && tagHead == "partial" ->
                StatamicProject.listPartials(position).map { ParamValue(it, "Partial") }

            param in HANDLE_PARAMS ->
                StatamicProject.listCollectionHandles(position).map { ParamValue(it, "Collection") } +
                    StatamicProject.listTaxonomyHandles(position).map { ParamValue(it, "Taxonomy") }

            param in SORT_PARAMS -> sortValues(position, project)

            param in BOOLEAN_PARAMS -> listOf(ParamValue("true", "Boolean"), ParamValue("false", "Boolean"))

            else -> emptyList()
        }
    }

    private fun sortValues(position: PsiElement, project: Project): List<ParamValue> {
        val fields = (AntlersFieldContext.fieldsInScope(position, project)?.map { it.handle }
            ?: BlueprintService.getInstance(project).fields().map { it.handle })
        val keys = (fields + SORT_KEYS).distinct()
        val out = mutableListOf<ParamValue>()
        for (k in keys) {
            out.add(ParamValue(k, "Sort"))
            out.add(ParamValue("$k:asc", "Sort"))
            out.add(ParamValue("$k:desc", "Sort"))
        }
        return out
    }
}
```

- [ ] **Step 4: Run, confirm pass (5 tests)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersParamValueSourceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamValueSource.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamValueSourceTest.kt
git commit -m "Add AntlersParamValueSource (partials/handles/sort/boolean param values)"
```

---

### Task 4: Wire the provider + in-quote prefix

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamValueCompletionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersParamValueCompletionTest : BasePlatformTestCase() {

    private fun blueprints() {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "<div>card</div>")
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n")
    }

    private fun complete(text: String): List<String> {
        blueprints()
        myFixture.configureByText("page.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testPartialSrcValue() =
        assertTrue(complete("{{ partial:src=\"<caret>\" }}").contains("blog/card"))

    fun testCollectionFromValue() =
        assertTrue(complete("{{ collection from=\"<caret>\" }}").contains("blog"))

    fun testSortValue() {
        val v = complete("{{ collection:blog sort=\"<caret>\" }}")
        assertTrue("title: $v", v.contains("title"))
        assertTrue("title:asc: $v", v.contains("title:asc"))
    }

    fun testBooleanValue() {
        val v = complete("{{ collection paginate=\"<caret>\" }}")
        assertTrue("true: $v", v.contains("true"))
        assertTrue("false: $v", v.contains("false"))
    }

    fun testNoParamValuesAtTagHead() {
        // A plain tag-head position must still offer tags, not param values.
        val v = complete("{{ <caret> }}")
        assertTrue("collection tag offered: $v", v.contains("collection"))
        assertFalse("no stray partial path at tag head: $v", v.contains("blog/card"))
    }
}
```

- [ ] **Step 2: Run, confirm failure (provider no-op for PARAMETER_VALUE)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersParamValueCompletionTest"`
Expected: FAIL — the value cases offer nothing (provider still has the Task-1 no-op).

- [ ] **Step 3: Replace the `PARAMETER_VALUE` no-op case in `AntlersCompletionProvider.addCompletions`**

```kotlin
            AntlersCompletionKind.PARAMETER_VALUE -> {
                val matched = paramValueResultSet(parameters, result)
                for (v in AntlersParamValueSource.valuesFor(info.tagHead, info.paramName, parameters.position, project)) {
                    matched.addElement(
                        LookupElementBuilder.create(v.text)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(v.typeText)
                    )
                }
            }
```

Add this private helper to the class (handles the in-quote prefix):

```kotlin
    /** Inside a quoted value the default prefix includes the opening quote, matching nothing.
     *  Re-base the prefix matcher on the string's inner text up to the caret. */
    private fun paramValueResultSet(
        parameters: CompletionParameters,
        result: CompletionResultSet
    ): CompletionResultSet {
        val pos = parameters.position
        if (pos.node.elementType != com.github.balotias.intellijantlers.psi.AntlersTypes.T_STRING) return result
        val caretInStr = (parameters.offset - pos.textRange.startOffset).coerceIn(0, pos.text.length)
        val inner = pos.text.substring(0, caretInStr)
            .removePrefix("\"").removePrefix("'")
            .replace(com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "")
        return result.withPrefixMatcher(inner)
    }
```

(Ensure `import com.intellij.codeInsight.completion.CompletionParameters` and `CompletionResultSet` are present — they already are.)

- [ ] **Step 4: Run, confirm pass (5 tests)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersParamValueCompletionTest"`
Expected: PASS. If a value case offers nothing, debug the prefix matcher first (log `inner`); the source is already proven in Task 3, so a miss is the prefix/result wiring, not the values.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersParamValueCompletionTest.kt
git commit -m "Offer parameter-value completions (route PARAMETER_VALUE + in-quote prefix)"
```

---

### Task 5: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the entire suite, forcing re-execution**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on zero failures**

Run: `echo "failed_files=$(grep -lo 'failures=\"[1-9]\|errors=\"[1-9]' build/test-results/test/*.xml | wc -l | tr -d ' ')"; echo "total_tests=$(grep -ho 'tests=\"[0-9]*\"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc)"`
Expected: `failed_files=0`. Note the new total (prior 213 + the new ~17).

- [ ] **Step 3: Confirm compile clean**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL.

---

## Self-Review

**Spec coverage:**
- `PARAMETER_VALUE` kind + `paramName`, bound-param `NONE`, both quoted/unquoted positions → Task 1.
- DRY partial lister + collection/taxonomy handle dir-scans → Task 2 (`AntlersPartialReference.getVariants` refactored to delegate).
- Four value sources (partial src, handle params, sort + `:asc`/`:desc`, booleans) → Task 3.
- Provider routing + in-quote prefix matcher; over-fire guard test → Task 4.
- Full-suite gate → Task 5.
- Out-of-scope (bound params, per-tag tables) → not implemented, by design.

**Placeholder scan:** none — all code shown; commands have expected output.

**Type/name consistency:** `AntlersCompletionInfo.paramName` defined in Task 1, consumed in Task 4; `ParamValue(text, typeText)` defined in Task 3, consumed in Task 4; `AntlersParamValueSource.valuesFor(tagHead, paramName, position, project)` signature identical across Tasks 3-4; `StatamicProject.listPartials/listCollectionHandles/listTaxonomyHandles(element)` defined in Task 2, used in Task 3. The provider's `when` gains exactly one `PARAMETER_VALUE` arm (no-op in Task 1 → filled in Task 4), keeping the enum exhaustive at every step.

**Cross-task compile note flagged:** Task 1 must add the `PARAMETER_VALUE -> {}` no-op to the provider's exhaustive `when` so the project compiles before Task 4 fills it in (called out in Task 1 Step 4).
