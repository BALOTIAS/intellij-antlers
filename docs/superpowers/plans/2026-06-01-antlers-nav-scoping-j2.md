# Antlers Nav/Structure Scoping J2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `{{ nav:… }}…{{ /nav }}` an iterating scope that offers the nav/collection blueprint fields + nav-tree meta variables, with recursive `{{ children }}`.

**Architecture:** Add a `NAVIGATION` namespace kind, a `navMeta` flag on `BlueprintScope`, nav handle resolution + a `navMeta` frame + recursive `children` in `AntlersScopeResolver`, a `NavVariables` list, and one completion touch (offer nav-meta when an enclosing scope is `navMeta`). Fields flow through the existing `AntlersFieldContext` unchanged.

**Tech Stack:** Kotlin, IntelliJ Platform PSI, JUnit `BasePlatformTestCase`.

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-nav-scoping-j2-design.md`

**TDD note:** `./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`. Full suite is currently **140/140 green**. Keep it green.

**Gotcha:** `depth`, `is_current`, `is_parent`, `parent`, `url` are ALSO in `SystemVariables.ALL` (always offered), and `children` is also a catalog tag — so tests must use a nav-only name like **`is_external`** / `has_entries` to prove the nav-meta gate fires.

---

## Task 1: NAVIGATION namespace + nav scope in the resolver

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespace.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScope.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespaceTest.kt` (extend)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolverTest.kt` (extend)

- [ ] **Step 1: Write the failing namespace + resolver tests**

Add to `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespaceTest.kt`:

```kotlin
    @Test fun navigationFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.NAVIGATION, "main"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/navigation/main.yaml")
        )
    }
```

Add to `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolverTest.kt` (the class already imports `BlueprintNamespace.Kind`; `BlueprintScope` is in the same package):

```kotlin
    private fun rawScopesAt(text: String): List<BlueprintScope> {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("page.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret) ?: error("no element at caret")
        return AntlersScopeResolver.scopesAt(el)
    }

    fun testNavShorthandScope() {
        val s = rawScopesAt("{{ nav:main }}{{ <caret> }}{{ /nav }}")
        assertEquals(1, s.size)
        assertEquals(BlueprintNamespace(Kind.NAVIGATION, "main"), s[0].namespace)
        assertTrue("navMeta flagged", s[0].navMeta)
    }

    fun testNavCollectionScope() {
        val s = rawScopesAt("{{ nav:collection:blog }}{{ <caret> }}{{ /nav }}")
        assertEquals(BlueprintNamespace(Kind.COLLECTION, "blog"), s[0].namespace)
        assertTrue(s[0].navMeta)
    }

    fun testNavHandleParam() {
        val s = rawScopesAt("{{ nav handle=\"main\" }}{{ <caret> }}{{ /nav }}")
        assertEquals(BlueprintNamespace(Kind.NAVIGATION, "main"), s[0].namespace)
    }

    fun testNavBareDefaultsToPages() {
        val s = rawScopesAt("{{ nav }}{{ <caret> }}{{ /nav }}")
        assertEquals(BlueprintNamespace(Kind.COLLECTION, "pages"), s[0].namespace)
        assertTrue(s[0].navMeta)
    }

    fun testNavChildrenRecursiveReentry() {
        val s = rawScopesAt("{{ nav:main }}{{ children }}{{ <caret> }}{{ /children }}{{ /nav }}")
        assertTrue(s.isNotEmpty())
        assertEquals(BlueprintNamespace(Kind.NAVIGATION, "main"), s[0].namespace)
        assertTrue("children re-entry keeps navMeta", s[0].navMeta)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopeResolverTest" --tests "com.github.balotias.intellijantlers.blueprint.BlueprintNamespaceTest" --no-configuration-cache`
Expected: FAIL — `NAVIGATION` kind missing; `BlueprintScope.navMeta` missing; nav not yet a scope.

- [ ] **Step 3: Add the `NAVIGATION` namespace**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespace.kt`.

Change the enum:

```kotlin
    enum class Kind { COLLECTION, TAXONOMY, USER, FORM, ASSET, GLOBAL, FIELDSET, UNKNOWN }
```

to:

```kotlin
    enum class Kind { COLLECTION, TAXONOMY, USER, FORM, ASSET, GLOBAL, FIELDSET, NAVIGATION, UNKNOWN }
```

In `fromPath`, add a navigation clause right after the `globals` line:

```kotlin
            fileAfter(p, "/resources/blueprints/globals/")?.let { return BlueprintNamespace(Kind.GLOBAL, it) }
```

becomes:

```kotlin
            fileAfter(p, "/resources/blueprints/globals/")?.let { return BlueprintNamespace(Kind.GLOBAL, it) }
            fileAfter(p, "/resources/blueprints/navigation/")?.let { return BlueprintNamespace(Kind.NAVIGATION, it) }
```

- [ ] **Step 4: Add the `navMeta` flag to `BlueprintScope`**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScope.kt` — change:

```kotlin
data class BlueprintScope(val namespace: BlueprintNamespace) : AntlersScope
```

to:

```kotlin
/** [navMeta] is true for nav scopes, which additionally offer nav-tree variables. */
data class BlueprintScope(val namespace: BlueprintNamespace, val navMeta: Boolean = false) : AntlersScope
```

- [ ] **Step 5: Add nav handling to the resolver**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`.

(a) Give `Frame` a `navMeta` field — change:

```kotlin
    private data class Frame(
        val name: String,                 // matched against the closer (tag head or alias)
        val namespace: BlueprintNamespace?,
        val active: Boolean,              // namespace contributes to this frame's body
        val alias: String?                // if set, namespace activates only inside a child {{ alias }}
    )
```

to:

```kotlin
    private data class Frame(
        val name: String,                 // matched against the closer (tag head or alias)
        val namespace: BlueprintNamespace?,
        val active: Boolean,              // namespace contributes to this frame's body
        val alias: String?,               // if set, namespace activates only inside a child {{ alias }}
        val navMeta: Boolean = false      // true for nav scopes (offer nav-tree variables)
    )
```

(b) Insert the nav + recursive-children handling. Change the alias block:

```kotlin
            // Alias activation: this tag's name matches an enclosing aliased frame's alias.
            val aliased = stack.lastOrNull { it.alias == head }
            if (aliased != null) {
                stack.addLast(Frame(head, aliased.namespace, true, null))
                continue
            }
```

to:

```kotlin
            // Alias activation: this tag's name matches an enclosing aliased frame's alias.
            val aliased = stack.lastOrNull { it.alias == head }
            if (aliased != null) {
                stack.addLast(Frame(head, aliased.namespace, true, null, aliased.navMeta))
                continue
            }

            // Recursive nav: {{ children }} re-enters the enclosing nav scope.
            if (head == "children") {
                val navFrame = stack.lastOrNull { it.navMeta && it.active && it.namespace != null }
                if (navFrame != null) {
                    stack.addLast(Frame("children", navFrame.namespace, true, null, navMeta = true))
                    continue
                }
            }

            // Nav tag: opens a scope over the nav/collection namespace, flagged for nav-meta variables.
            if (head == "nav") {
                val ns = navNamespace(namePath, stmt)
                val alias = paramValue(stmt, setOf("as"))
                stack.addLast(
                    if (alias != null) Frame("nav", ns, false, alias, navMeta = true)
                    else Frame("nav", ns, true, null, navMeta = true)
                )
                continue
            }
```

(c) Thread `navMeta` into the emitted scopes — change:

```kotlin
        return stack.filter { it.active && it.namespace != null }
            .reversed()
            .map { BlueprintScope(it.namespace!!) }
```

to:

```kotlin
        return stack.filter { it.active && it.namespace != null }
            .reversed()
            .map { BlueprintScope(it.namespace!!, it.navMeta) }
```

(d) Add the `navNamespace` helper next to `resolveNamespace`:

```kotlin
    /** The namespace a `{{ nav … }}` tag iterates. Never null (defaults to the `pages` collection). */
    private fun navNamespace(namePath: AntlersNamePathMixin, stmt: AntlersStatement): BlueprintNamespace {
        val segs = namePath.segments  // segs[0] == "nav"
        if (segs.size >= 3 && segs[1] == "collection") {
            return BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, segs[2])
        }
        if (segs.size >= 2) {
            return BlueprintNamespace(BlueprintNamespace.Kind.NAVIGATION, segs[1])
        }
        val param = paramValue(stmt, setOf("handle", "from"))
        return if (param != null) BlueprintNamespace(BlueprintNamespace.Kind.NAVIGATION, param)
        else BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "pages")
    }
```

(`namePath.segments` is the full ordered identifier list added in E3b.)

- [ ] **Step 6: Run the namespace + resolver tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopeResolverTest" --tests "com.github.balotias.intellijantlers.blueprint.BlueprintNamespaceTest" --no-configuration-cache`
Expected: PASS — including the existing resolver/namespace cases (non-nav scopes have `navMeta=false`, so `BlueprintScope(ns)` still equals `BlueprintScope(ns, false)`).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespace.kt src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScope.kt src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintNamespaceTest.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolverTest.kt
git commit -m "Open a nav scope (navigation/collection namespace + navMeta) in the resolver"
```

---

## Task 2: Nav-meta variables + completion

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/NavVariables.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersNavScopeTest.kt` (new)

- [ ] **Step 1: Write the failing nav-completion tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersNavScopeTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersNavScopeTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject(
            "resources/blueprints/navigation/main.yaml",
            "fields:\n  - handle: link_text\n    field:\n      type: text\n      display: Link Text\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n      display: Blog Title\n"
        )
    }

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testNavOffersFieldsMetaAndLoop() {
        setup()
        val l = lookups("{{ nav:main }}{{ <caret> }}{{ /nav }}")
        assertTrue("nav blueprint field: $l", l.contains("link_text"))
        assertTrue("nav-only meta is_external: $l", l.contains("is_external"))
        assertTrue("loop var index: $l", l.contains("index"))
    }

    fun testNavCollectionOffersCollectionFields() {
        setup()
        val l = lookups("{{ nav:collection:blog }}{{ <caret> }}{{ /nav }}")
        assertTrue("collection field via nav: $l", l.contains("title"))
        assertTrue("nav-meta still offered: $l", l.contains("has_entries"))
    }

    fun testCollectionLoopHasNoNavMeta() {
        setup()
        val l = lookups("{{ collection:blog }}{{ <caret> }}{{ /collection }}")
        assertTrue("loop var present: $l", l.contains("index"))
        assertFalse("no nav-only meta in a plain collection loop: $l", l.contains("is_external"))
    }

    fun testNavFieldGoToDef() {
        setup()
        val text = "{{ nav:collection:blog }}{{ ti<caret>tle }}{{ /nav }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertEquals("blog.yaml", target!!.name)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersNavScopeTest" --no-configuration-cache`
Expected: FAIL — `NavVariables` missing / nav-meta not offered (`is_external` absent).

- [ ] **Step 3: Create `NavVariables`**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/NavVariables.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

/** A nav-tree variable available inside a `{{ nav … }}` loop. */
data class NavVariable(val name: String, val description: String)

object NavVariables {
    val ALL: List<NavVariable> = listOf(
        NavVariable("depth", "The item's depth in the nav tree (1-based)."),
        NavVariable("is_current", "True when this item is the current URL."),
        NavVariable("is_parent", "True when this item is an ancestor of the current URL."),
        NavVariable("is_published", "Whether the item's entry is published."),
        NavVariable("is_page", "Whether the item is a page (vs a manual link)."),
        NavVariable("is_entry", "Whether the item references an entry."),
        NavVariable("is_external", "Whether the item is an external link."),
        NavVariable("has_entries", "Whether the item has child entries."),
        NavVariable("children", "The item's child nav items."),
        NavVariable("parent", "The item's parent nav item."),
        NavVariable("url", "The item's URL.")
    )
}
```

- [ ] **Step 4: Offer nav-meta in completion**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`.

Add the import (next to the existing `LoopVariables` import):

```kotlin
import com.github.balotias.intellijantlers.scope.NavVariables
```

Replace the loop-var block:

```kotlin
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

with:

```kotlin
                // Loop-meta vars only inside an actual iterating tag (E1) — NOT for a page match.
                val scopes = AntlersScopeResolver.scopesAt(parameters.position)
                if (scopes.isNotEmpty()) {
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
                // Nav-tree vars only inside a {{ nav … }} scope.
                if (scopes.any { it.navMeta }) {
                    for (nv in NavVariables.ALL) {
                        if (seen.add(nv.name)) {
                            result.addElement(
                                LookupElementBuilder.create(nv.name)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Nav")
                                    .withTailText("  ${nv.description}", true)
                            )
                        }
                    }
                }
```

- [ ] **Step 5: Run the nav-completion tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersNavScopeTest" --no-configuration-cache`
Expected: PASS (4 tests).

- [ ] **Step 6: Run the completion/scope regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopedCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersPageCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersContainerScopeTest" --tests "com.github.balotias.intellijantlers.scope.AntlersMemberCompletionTest" --no-configuration-cache`
Expected: PASS — non-nav scopes set `navMeta=false`, so the nav block never fires for them.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/scope/NavVariables.kt src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersNavScopeTest.kt
git commit -m "Offer nav-tree variables inside a nav scope"
```

---

## Task 3: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew test --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm no failures**

Run: `grep -lo 'failures="[1-9]' build/test-results/test/*.xml | wc -l`
Expected: `0`.

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc`
Expected: **~150** (140 prior + ~10 new). Exact count may differ; **0 failures** is the gate.

- [ ] **Step 3: Verify production compilation**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

---

## Notes for the implementer

- **No `plugin.xml`, grammar, or lexer changes.** `AntlersFieldContext`, references, and docs are untouched — nav fields flow through the existing scope→`AntlersFieldContext` path automatically.
- `navMeta` defaults false on `Frame` and `BlueprintScope`, so every existing construction and equality still holds; only `nav`-headed tags and `{{ children }}`-inside-nav change behavior.
- The nav-meta gate uses a nav-ONLY name in tests (`is_external`/`has_entries`) because `depth`/`is_current`/`is_parent`/`parent`/`url` are also `SystemVariables` (always offered) and `children` is also a catalog tag.
- `navNamespace` never returns null (bare `nav` → `COLLECTION "pages"`); an unknown nav handle yields a real namespace with no scanned fields → nav-meta + loop vars still offered.
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
```
