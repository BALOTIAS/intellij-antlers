# Antlers Blueprint Scoping E1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `{{ variable }}` completion, go-to-definition, and docs aware of the enclosing iterating-tag scope (e.g. inside `{{ collection:blog }}…{{ /collection }}`), and tag every blueprint field with a namespace derived from its file path.

**Architecture:** Add a `BlueprintNamespace` to every `BlueprintField` (derived from the blueprint path during scanning) and a `BlueprintService.fieldsFor(namespace)` lookup. A new `AntlersScopeResolver` reconstructs iterating-tag nesting by walking the flat top-level statements before the caret with a stack (the same shape `AntlersFoldingBuilder` already uses), handling `as="alias"`, condition transparency, and cascade. A single `AntlersScopeFields.fieldsInScope` helper returns `null` (top level → global fallback) or the restricted scoped fields; completion, references, and docs all branch on it.

**Tech Stack:** Kotlin, IntelliJ Platform PSI (`AntlersStatement`/`AntlersClosingTagMixin`/`AntlersNamePathMixin`/`AntlersParameterMixin`/`AntlersConditionMixin`), `@Service(PROJECT)` + `CachedValuesManager`, JUnit `BasePlatformTestCase` (runs via `./gradlew test`).

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-blueprint-scope-e1-design.md`

**TDD note:** This environment runs `BasePlatformTestCase` via `./gradlew test`. Run a single test class with:
`./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`
The full suite is currently **71/71 green**. Keep it green.

---

## Task 1: Blueprint namespace foundation

Add `BlueprintNamespace`, derive it from the path while scanning, attach it to every field, and expose `BlueprintService.fieldsFor(ns)`.

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespace.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintService.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespaceTest.kt` (new)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt` (extend)

- [ ] **Step 1: Write the failing `BlueprintNamespace.fromPath` test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespaceTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class BlueprintNamespaceTest {

    @Test fun collectionFromDir() {
        assertEquals(
            BlueprintNamespace(Kind.COLLECTION, "blog"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/collections/blog/article.yaml")
        )
    }

    @Test fun taxonomyFromDir() {
        assertEquals(
            BlueprintNamespace(Kind.TAXONOMY, "tags"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/taxonomies/tags/tags.yaml")
        )
    }

    @Test fun formFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.FORM, "contact"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/forms/contact.yaml")
        )
    }

    @Test fun assetFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.ASSET, "main"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/assets/main.yaml")
        )
    }

    @Test fun globalFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.GLOBAL, "site"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/globals/site.yaml")
        )
    }

    @Test fun userSingleton() {
        assertEquals(
            BlueprintNamespace(Kind.USER, "user"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/user.yaml")
        )
    }

    @Test fun fieldsetFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.FIELDSET, "seo"),
            BlueprintNamespace.fromPath("/proj/resources/fieldsets/seo.yaml")
        )
    }

    @Test fun unrecognizedIsUnknown() {
        assertEquals(
            BlueprintNamespace(Kind.UNKNOWN, ""),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/something_else/x.yaml")
        )
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.BlueprintNamespaceTest" --no-configuration-cache`
Expected: FAIL — compile error / unresolved reference `BlueprintNamespace`.

- [ ] **Step 3: Create `BlueprintNamespace.kt`**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespace.kt`:

```kotlin
package com.github.balotias.intellijantlers.blueprint

/**
 * Which Statamic data namespace a blueprint/fieldset belongs to, derived from its file path.
 * The handle is the collection/taxonomy/form/etc. handle (or "user" for the user singleton).
 */
data class BlueprintNamespace(val kind: Kind, val handle: String) {
    enum class Kind { COLLECTION, TAXONOMY, USER, FORM, ASSET, GLOBAL, FIELDSET, UNKNOWN }

    companion object {
        val UNKNOWN = BlueprintNamespace(Kind.UNKNOWN, "")

        /** Tolerant path → namespace mapping. Never throws; unrecognized layouts yield UNKNOWN. */
        fun fromPath(path: String): BlueprintNamespace {
            val p = path.replace('\\', '/')
            // collections/taxonomies: handle is the DIRECTORY name (a collection may have several files)
            dirAfter(p, "/resources/blueprints/collections/")?.let { return BlueprintNamespace(Kind.COLLECTION, it) }
            dirAfter(p, "/resources/blueprints/taxonomies/")?.let { return BlueprintNamespace(Kind.TAXONOMY, it) }
            // forms/assets/globals/fieldsets: handle is the FILE name (without .yaml)
            fileAfter(p, "/resources/blueprints/forms/")?.let { return BlueprintNamespace(Kind.FORM, it) }
            fileAfter(p, "/resources/blueprints/assets/")?.let { return BlueprintNamespace(Kind.ASSET, it) }
            fileAfter(p, "/resources/blueprints/globals/")?.let { return BlueprintNamespace(Kind.GLOBAL, it) }
            if (p.endsWith("/resources/blueprints/user.yaml")) return BlueprintNamespace(Kind.USER, "user")
            fileAfter(p, "/resources/fieldsets/")?.let { return BlueprintNamespace(Kind.FIELDSET, it) }
            return UNKNOWN
        }

        /** First path segment after [marker], e.g. "blog" in ".../collections/blog/article.yaml". */
        private fun dirAfter(path: String, marker: String): String? {
            val i = path.indexOf(marker)
            if (i < 0) return null
            return path.substring(i + marker.length).substringBefore('/').takeIf { it.isNotBlank() }
        }

        /** File handle after [marker]: the immediate filename without its .yaml extension. */
        private fun fileAfter(path: String, marker: String): String? {
            val i = path.indexOf(marker)
            if (i < 0) return null
            val rest = path.substring(i + marker.length)
            // must be a direct file under the marker dir (no further slash)
            if (rest.contains('/')) return null
            return rest.removeSuffix(".yaml").takeIf { it.isNotBlank() }
        }
    }
}
```

- [ ] **Step 4: Run the namespace test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.BlueprintNamespaceTest" --no-configuration-cache`
Expected: PASS (8 tests).

- [ ] **Step 5: Add `namespace` to `BlueprintField`**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt` — replace the whole file with:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.vfs.VirtualFile

/** A field declared in a Statamic blueprint/fieldset: the `handle` is the `{{ variable }}` name. */
data class BlueprintField(
    val handle: String,
    val display: String,
    val type: String,
    val file: VirtualFile,
    val offset: Int,
    val namespace: BlueprintNamespace
)
```

- [ ] **Step 6: Set the namespace in `BlueprintScanner.extract`**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`. In `private fun extract(file: VirtualFile, out: MutableList<BlueprintField>)`, compute the namespace once at the top and pass it to the `BlueprintField(...)` constructor.

Find the start of `extract`:

```kotlin
    private fun extract(file: VirtualFile, out: MutableList<BlueprintField>) {
        val text = VfsUtilCore.loadText(file)
        val lines = text.split("\n")
        var pos = 0
```

Replace with (add the `ns` line):

```kotlin
    private fun extract(file: VirtualFile, out: MutableList<BlueprintField>) {
        val text = VfsUtilCore.loadText(file)
        val ns = BlueprintNamespace.fromPath(file.path)
        val lines = text.split("\n")
        var pos = 0
```

Then find the constructor call:

```kotlin
                out.add(BlueprintField(handle, display, type, file, handleOffset))
```

Replace with:

```kotlin
                out.add(BlueprintField(handle, display, type, file, handleOffset, ns))
```

- [ ] **Step 7: Add `fieldsFor` to `BlueprintService`**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintService.kt`. After the existing `field(handle)` method (line with `fun field(handle: String)...`), add:

```kotlin
    /** All fields in a given namespace (union across its blueprints), deduped by handle (first wins). */
    fun fieldsFor(ns: BlueprintNamespace): List<BlueprintField> =
        scanned().filter { it.namespace == ns }.distinctBy { it.handle }
```

(Place it directly below `fun field(handle: String): BlueprintField? = ...` and above `private fun scanned()`.)

- [ ] **Step 8: Extend `BlueprintScannerTest` to assert the namespace**

Edit `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt`. Add these two methods inside the class (after `testServiceDedupesAndFinds`):

```kotlin
    fun testFieldsCarryCollectionNamespace() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        val hero = BlueprintScanner.scan(project).first { it.handle == "hero_title" }
        assertEquals(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"), hero.namespace)
    }

    fun testFieldsForFiltersByNamespace() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: News Hero\n"
        )
        val svc = BlueprintService.getInstance(project)
        val blog = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"))
        assertTrue("blog has hero_title", blog.any { it.handle == "hero_title" })
        assertTrue("blog has body", blog.any { it.handle == "body" })
        val news = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "news"))
        assertEquals("news hero_title display", "News Hero", news.first { it.handle == "hero_title" }.display)
        assertFalse("news does not include blog-only body", news.any { it.handle == "body" })
    }
```

- [ ] **Step 9: Run the blueprint package tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.*" --no-configuration-cache`
Expected: PASS — existing scanner/nav/completion tests plus the new namespace + fieldsFor assertions.

- [ ] **Step 10: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint/ src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespaceTest.kt src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt
git commit -m "Add blueprint namespace (path-derived) + fieldsFor lookup"
```

---

## Task 2: Scope model + resolver + loop vars + shared helper

Reconstruct the enclosing iterating-tag scope from the flat PSI and expose it through one helper.

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScope.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/LoopVariables.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeFields.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolverTest.kt` (new)

- [ ] **Step 1: Write the failing scope-resolver test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolverTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace.Kind
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersScopeResolverTest : BasePlatformTestCase() {

    /** Returns the BlueprintScope namespaces (innermost first) at the <caret> in [text]. */
    private fun scopesAt(text: String): List<BlueprintNamespace> {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("page.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret) ?: error("no element at caret")
        return AntlersScopeResolver.scopesAt(el).map { (it as BlueprintScope).namespace }
    }

    fun testInsideCollection() {
        assertEquals(
            listOf(BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection:blog }}{{ <caret> }}{{ /collection }}")
        )
    }

    fun testNestedCascadeInnermostFirst() {
        assertEquals(
            listOf(BlueprintNamespace(Kind.TAXONOMY, "tags"), BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection:blog }}{{ taxonomy:tags }}{{ <caret> }}{{ /taxonomy }}{{ /collection }}")
        )
    }

    fun testTopLevelEmpty() {
        assertEquals(emptyList<BlueprintNamespace>(), scopesAt("{{ <caret> }}"))
    }

    fun testConditionIsTransparent() {
        assertEquals(emptyList<BlueprintNamespace>(), scopesAt("{{ if foo }}{{ <caret> }}{{ /if }}"))
    }

    fun testFromParamHandle() {
        assertEquals(
            listOf(BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection from=\"blog\" }}{{ <caret> }}{{ /collection }}")
        )
    }

    fun testAliasBodyEmptyButAliasTagScoped() {
        // In the collection body (before the alias tag) there is no scope...
        assertEquals(
            emptyList<BlueprintNamespace>(),
            scopesAt("{{ collection:blog as=\"entries\" }}{{ <caret> }}{{ /collection }}")
        )
    }

    fun testInsideAliasTagScoped() {
        // ...but inside {{ entries }} the collection's fields apply.
        assertEquals(
            listOf(BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection:blog as=\"entries\" }}{{ entries }}{{ <caret> }}{{ /entries }}{{ /collection }}")
        )
    }

    fun testTransparentTagPreservesOuterScope() {
        assertEquals(
            listOf(BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection:blog }}{{ cache }}{{ <caret> }}{{ /cache }}{{ /collection }}")
        )
    }

    fun testUnresolvedHandleNoScope() {
        // collection with no handle and no from/in → cannot scope → empty (global fallback)
        assertEquals(emptyList<BlueprintNamespace>(), scopesAt("{{ collection }}{{ <caret> }}{{ /collection }}"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopeResolverTest" --no-configuration-cache`
Expected: FAIL — unresolved references `AntlersScopeResolver` / `BlueprintScope`.

- [ ] **Step 3: Create the scope model**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScope.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace

/** A variable scope around the caret. v1 only models blueprint-backed iterating scopes. */
sealed interface AntlersScope

/** The caret is inside an iterating tag whose entries/terms are described by [namespace]. */
data class BlueprintScope(val namespace: BlueprintNamespace) : AntlersScope
```

- [ ] **Step 4: Create the resolver**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Reconstructs the enclosing iterating-tag scope at a caret. The Antlers grammar is flat — an opener
 * `{{ collection:blog }}` and its `{{ /collection }}` are sibling top-level statements — so we replay
 * the statements that end before the caret through a stack, mirroring AntlersFoldingBuilder.
 * Never throws; unbalanced templates yield an over-broad or empty scope.
 */
object AntlersScopeResolver {

    private val ITERATING = setOf("collection", "taxonomy", "users", "user", "form", "assets")
    private val CONDITION_OPENERS = setOf("if", "unless")
    private val CONDITION_CLOSERS = mapOf("endif" to "if", "endunless" to "unless")

    /** A pushed open construct awaiting its closer. */
    private data class Frame(
        val name: String,                 // matched against the closer (tag head or alias)
        val namespace: BlueprintNamespace?,
        val active: Boolean,              // namespace contributes to this frame's body
        val alias: String?                // if set, namespace activates only inside a child {{ alias }}
    )

    /** BlueprintScopes enclosing [element], innermost first. Empty = top level (global fallback). */
    fun scopesAt(element: PsiElement): List<BlueprintScope> {
        val file = element.containingFile ?: return emptyList()
        val caret = element.textRange.startOffset
        val project = element.project
        val catalog = if (project.isDefault) null else AntlersCatalogService.getInstance(project)

        val statements = PsiTreeUtil.findChildrenOfType(file, AntlersStatement::class.java)
            .filter { it.textRange.endOffset <= caret }
            .sortedBy { it.textRange.startOffset }

        val stack = ArrayDeque<Frame>()
        for (stmt in statements) {
            // Closing tag: pop the nearest matching frame (and any unmatched inner frames).
            val closing = stmt.closingTag
            if (closing != null) {
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':')
                if (name != null) popTo(stack, name)
                continue
            }
            // Condition: if/unless open a transparent frame; endif/endunless close it; else/elseif ignored.
            val condition = stmt.condition
            if (condition != null) {
                val kw = (condition as? AntlersConditionMixin)?.keyword ?: continue
                CONDITION_CLOSERS[kw]?.let { popTo(stack, it) }
                if (kw in CONDITION_OPENERS) stack.addLast(Frame(kw, null, false, null))
                continue
            }
            // Opener with a name path.
            val namePath = stmt.namePath as? AntlersNamePathMixin ?: continue
            val head = namePath.head
            if (head.isBlank()) continue

            // Alias activation: this tag's name matches an enclosing aliased frame's alias.
            val aliased = stack.lastOrNull { it.alias == head }
            if (aliased != null) {
                stack.addLast(Frame(head, aliased.namespace, true, null))
                continue
            }

            // Recognized iterating tag with a resolvable handle.
            if (head in ITERATING) {
                val ns = resolveNamespace(head, namePath, stmt)
                if (ns != null) {
                    val alias = paramValue(stmt, setOf("as"))
                    stack.addLast(
                        if (alias != null) Frame(head, ns, false, alias)
                        else Frame(head, ns, true, null)
                    )
                    continue
                }
            }

            // Any other catalog pair tag (cache, section, or an iterating tag with no handle):
            // push a transparent frame so its closer balances. Non-pair tags / plain variables
            // have no closer, so we must NOT push them.
            if (catalog?.tag(head)?.isPair == true) stack.addLast(Frame(head, null, false, null))
        }

        return stack.filter { it.active && it.namespace != null }
            .reversed()
            .map { BlueprintScope(it.namespace!!) }
    }

    private fun popTo(stack: ArrayDeque<Frame>, name: String) {
        val idx = stack.indexOfLast { it.name == name }
        if (idx >= 0) while (stack.size > idx) stack.removeLast()
    }

    private fun resolveNamespace(head: String, namePath: AntlersNamePathMixin, stmt: AntlersStatement): BlueprintNamespace? {
        val kind = when (head) {
            "collection" -> BlueprintNamespace.Kind.COLLECTION
            "taxonomy" -> BlueprintNamespace.Kind.TAXONOMY
            "form" -> BlueprintNamespace.Kind.FORM
            "assets" -> BlueprintNamespace.Kind.ASSET
            "users", "user" -> return BlueprintNamespace(BlueprintNamespace.Kind.USER, "user")
            else -> return null
        }
        val handle = namePath.method ?: paramValue(stmt, setOf("from", "in")) ?: return null
        return BlueprintNamespace(kind, handle)
    }

    /** The unquoted value of the first parameter on [stmt] whose name is in [names], or null. */
    private fun paramValue(stmt: AntlersStatement, names: Set<String>): String? {
        for (param in PsiTreeUtil.getChildrenOfTypeAsList(stmt, AntlersParameterMixin::class.java)) {
            if (param.parameterName in names) {
                val raw = param.valueElement?.text ?: return null
                return stripQuotes(raw).takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun stripQuotes(s: String): String {
        if (s.length >= 2 && (s.first() == '"' || s.first() == '\'') && s.last() == s.first()) {
            return s.substring(1, s.length - 1)
        }
        return s
    }
}
```

- [ ] **Step 5: Run the resolver test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopeResolverTest" --no-configuration-cache`
Expected: PASS (9 tests). If `testFromParamHandle`/alias tests fail, the most likely cause is the parameter not being a direct child of the statement — verify with a quick PSI dump; the grammar promotes `parameter` to a statement child so `PsiTreeUtil.getChildrenOfTypeAsList(stmt, AntlersParameterMixin::class.java)` should find it.

- [ ] **Step 6: Create `LoopVariables`**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/LoopVariables.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

/** A loop-meta variable available inside any iterating Antlers tag. */
data class LoopVariable(val name: String, val description: String)

object LoopVariables {
    val ALL: List<LoopVariable> = listOf(
        LoopVariable("index", "1-based position of the current item in the loop"),
        LoopVariable("count", "1-based count of the current item (alias of index)"),
        LoopVariable("total_results", "Total number of items in the loop"),
        LoopVariable("first", "True on the first iteration"),
        LoopVariable("last", "True on the last iteration"),
        LoopVariable("no_results", "True when the loop produced no items")
    )
}
```

- [ ] **Step 7: Create the shared `AntlersScopeFields` helper**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeFields.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Single decision point for "which blueprint fields apply at this caret". */
object AntlersScopeFields {

    /**
     * null     → no iterating scope encloses the caret: caller uses the global fallback (all fields).
     * non-null → restrict to exactly these scoped fields (innermost-first, deduped by handle); do NOT
     *            fall back to global. May be empty when the scope is known but defines no fields.
     */
    fun fieldsInScope(element: PsiElement, project: Project): List<BlueprintField>? {
        val scopes = AntlersScopeResolver.scopesAt(element)
        if (scopes.isEmpty()) return null
        val svc = BlueprintService.getInstance(project)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<BlueprintField>()
        for (scope in scopes) { // innermost first
            for (f in svc.fieldsFor(scope.namespace)) {
                if (seen.add(f.handle)) out.add(f)
            }
        }
        return out
    }
}
```

- [ ] **Step 8: Run the scope package tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.*" --no-configuration-cache`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/scope/ src/test/kotlin/com/github/balotias/intellijantlers/scope/
git commit -m "Add Antlers scope resolver, loop vars, and scope-fields helper"
```

---

## Task 3: Scope-aware completion

Inside a recognized loop, offer only that scope's fields + loop-meta + system vars; at top level keep today's behavior.

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopedCompletionTest.kt` (new)

- [ ] **Step 1: Write the failing scoped-completion test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopedCompletionTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersScopedCompletionTest : BasePlatformTestCase() {

    private fun setupBlueprints() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: Hero Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/taxonomies/tags/tags.yaml",
            "fields:\n  - handle: tag_color\n    field:\n      type: text\n      display: Tag Color\n"
        )
    }

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testInsideCollectionOffersScopedFieldsLoopAndSystemVars() {
        setupBlueprints()
        val l = lookups("{{ collection:blog }}{{ <caret> }}{{ /collection }}")
        assertTrue("blog field: $l", l.contains("hero_title"))
        assertFalse("not the taxonomy-only field: $l", l.contains("tag_color"))
        assertTrue("loop var index: $l", l.contains("index"))
        assertTrue("system var title: $l", l.contains("title"))
        assertTrue("still offers tags: $l", l.contains("collection"))
    }

    fun testTopLevelStillOffersEverything() {
        setupBlueprints()
        val l = lookups("{{ <caret> }}")
        assertTrue("blog field: $l", l.contains("hero_title"))
        assertTrue("taxonomy field too: $l", l.contains("tag_color"))
        assertFalse("no loop vars at top level: $l", l.contains("index"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopedCompletionTest" --no-configuration-cache`
Expected: FAIL — `testInsideCollectionOffersScopedFieldsLoopAndSystemVars` fails (today `tag_color` is offered and `index` is not).

- [ ] **Step 3: Wire the TAG_NAME branch to the scope helper**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`.

Add imports near the existing blueprint imports:

```kotlin
import com.github.balotias.intellijantlers.scope.AntlersScopeFields
import com.github.balotias.intellijantlers.scope.LoopVariables
```

Replace the blueprint-fields + system-vars portion of the `TAG_NAME` branch. Find this block:

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

Replace it with:

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

- [ ] **Step 4: Run the scoped-completion test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopedCompletionTest" --no-configuration-cache`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the existing variable-completion test — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableCompletionTest" --no-configuration-cache`
Expected: PASS (top-level completion unchanged).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopedCompletionTest.kt
git commit -m "Scope-aware variable completion (scoped fields + loop vars)"
```

---

## Task 4: Scope-aware go-to-definition + docs

Resolve and document `{{ field }}` against the enclosing scope first, falling back to the global lookup only at top level.

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintFieldReference.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopedNavTest.kt` (new)

- [ ] **Step 1: Write the failing scoped-nav + scoped-docs test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopedNavTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersScopedNavTest : BasePlatformTestCase() {

    private fun setupTwoCollections() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: Blog Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: News Title\n"
        )
    }

    fun testScopedGoToDefPicksTheEnclosingCollection() {
        setupTwoCollections()
        val text = "{{ collection:news }}{{ ti<caret>tle }}{{ /collection }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertNotNull("title should resolve within scope", target)
        assertEquals("news.yaml", target!!.name)
    }

    fun testScopedDocNamesTheNamespace() {
        setupTwoCollections()
        val text = "{{ collection:news }}{{ ti<caret>tle }}{{ /collection }}"
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret)!!
        val doc = AntlersDocumentationProvider().generateDoc(el, el)
        assertNotNull(doc)
        assertTrue("doc names news collection: $doc", doc!!.contains("news"))
        assertTrue("doc shows the scoped display: $doc", doc.contains("News Title"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopedNavTest" --no-configuration-cache`
Expected: FAIL — today the reference resolves globally first-wins (may land on `blog.yaml`) and the doc has no namespace label.

- [ ] **Step 3: Make the reference resolve scope-first**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintFieldReference.kt` — replace the whole file with:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.scope.AntlersScopeResolver
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/** Soft reference from a `{{ }}` variable to its blueprint field declaration, scope-aware. */
class AntlersBlueprintFieldReference(
    element: PsiElement,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {
    override fun resolve(): PsiElement? {
        val field = scopedField() ?: BlueprintService.getInstance(element.project).field(handle) ?: return null
        val psiFile = PsiManager.getInstance(element.project).findFile(field.file) ?: return null
        return psiFile.findElementAt(field.offset) ?: psiFile
    }

    /** Look the handle up within the enclosing scopes (innermost first); null if not in scope. */
    private fun scopedField(): BlueprintField? {
        val scopes = AntlersScopeResolver.scopesAt(element)
        if (scopes.isEmpty()) return null
        val svc = BlueprintService.getInstance(element.project)
        return scopes.firstNotNullOfOrNull { s -> svc.fieldsFor(s.namespace).firstOrNull { it.handle == handle } }
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
```

- [ ] **Step 4: Make the docs provider resolve scope-first and label the namespace**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`.

Add imports near the top (after the existing imports):

```kotlin
import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.scope.AntlersScopeResolver
```

Replace the head-name branch. Find this block (inside `generateDoc`, the `if (path.head == name)` body):

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

Replace it with:

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

Then add these two private helpers (place them next to `enclosingTag`, before `tagDoc`):

```kotlin
    /** The field for [name] within the caret's enclosing scopes (innermost first), or null. */
    private fun scopedField(ident: PsiElement, name: String): BlueprintField? {
        val scopes = AntlersScopeResolver.scopesAt(ident)
        if (scopes.isEmpty()) return null
        val svc = BlueprintService.getInstance(ident.project)
        return scopes.firstNotNullOfOrNull { s -> svc.fieldsFor(s.namespace).firstOrNull { it.handle == name } }
    }

    /** " · collection: blog" style suffix; empty for the UNKNOWN namespace. */
    private fun namespaceLabel(ns: BlueprintNamespace): String {
        if (ns.kind == BlueprintNamespace.Kind.UNKNOWN) return ""
        val kind = ns.kind.name.lowercase()
        return " · ${esc(kind)}: ${esc(ns.handle)}"
    }
```

- [ ] **Step 5: Run the scoped-nav test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopedNavTest" --no-configuration-cache`
Expected: PASS (2 tests).

- [ ] **Step 6: Run the existing nav/docs test — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableNavTest" --no-configuration-cache`
Expected: PASS (top-level nav/docs unchanged — global fallback still applies).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintFieldReference.kt src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopedNavTest.kt
git commit -m "Scope-aware blueprint go-to-def + namespace-labelled docs"
```

---

## Task 5: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew test --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm the count**

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc`
Expected: **~94** total tests (71 prior + 23 new: 8 namespace + 2 scanner + 9 resolver + 2 scoped-completion + 2 scoped-nav). The exact number may differ slightly; **0 failures** is the gate, not the count. Confirm no failures:

Run: `grep -lo 'failures="[1-9]' build/test-results/test/*.xml | wc -l`
Expected: `0`.

- [ ] **Step 3: Verify production compilation explicitly**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL (no unresolved references in the wired consumers).

---

## Notes for the implementer

- **No `plugin.xml` changes** — no new extension points; `BlueprintService` stays a `@Service`.
- **No grammar/lexer changes** — everything reads the existing PSI.
- If `PsiTreeUtil.getChildrenOfTypeAsList(stmt, AntlersParameterMixin::class.java)` returns empty for `as=`/`from=` parameters, dump the PSI of `{{ collection from="blog" }}` in a scratch test (`DebugUtil.psiToString(file, true)`) to confirm where `AntlersParameter` sits, and adjust the traversal (it should be a direct statement child since `tail_`/`expr_` are private rules).
- The scope resolver mirrors `AntlersFoldingBuilder`'s stack walk — keep them conceptually in sync (closing-tag name uses `closedName?.substringBefore(':')`).
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
