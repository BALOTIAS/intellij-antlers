# Antlers Statamic 5 & 6 Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect the project's Statamic major version from `composer.json` (→ `composer.lock` → latest=6) and tailor the bundled catalog to it; make the catalog a 5∪6 superset (`relate` re-added) and prove both versions' syntax parses.

**Architecture:** New cached `StatamicVersionService` (regex composer parsing). Two nullable version fields on `TagDef`/`ModifierDef` with an `appliesTo(major)` predicate. `AntlersCatalogService` filters the bundled lists by the resolved version (scanned entries always kept). No parser/grammar/dependency change.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (project `@Service`, `FilenameIndex`, `CachedValuesManager`), `BasePlatformTestCase`, Gradle.

**Reference spec:** `docs/superpowers/specs/2026-06-03-antlers-statamic-5-6-compat-design.md`

**Branch:** `antlers-statamic-5-6-compat` (create from `main` before Task 1).

**Test gate (after every test run):** `./gradlew --rerun-tasks test` then
`grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` — must print nothing. `--rerun-tasks` is required (Gradle caches).

---

### Task 1: Version fields on the catalog models

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModels.kt`

- [ ] **Step 1: Add the fields + predicate to `TagDef` and `ModifierDef`**

In `CatalogModels.kt`, replace the `TagDef` and `ModifierDef` declarations with:

```kotlin
data class TagDef(
    val name: String,
    val description: String = "",
    val docUrl: String = "",
    val isPair: Boolean = false,
    val methods: List<String> = emptyList(),
    val parameters: List<ParamDef> = emptyList(),
    val introducedIn: Int? = null,   // first Statamic major that has it (null = always)
    val removedIn: Int? = null,      // first Statamic major that DROPPED it (null = never)
) {
    /** True when this entry exists in Statamic major version [major]. */
    fun appliesTo(major: Int): Boolean =
        (introducedIn == null || major >= introducedIn) && (removedIn == null || major < removedIn)
}
```

```kotlin
data class ModifierDef(
    val name: String,
    val description: String = "",
    val docUrl: String = "",
    val takesArguments: Boolean = false,
    val introducedIn: Int? = null,
    val removedIn: Int? = null,
) {
    /** True when this entry exists in Statamic major version [major]. */
    fun appliesTo(major: Int): Boolean =
        (introducedIn == null || major >= introducedIn) && (removedIn == null || major < removedIn)
}
```

(Leave `ParamDef` unchanged.)

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModels.kt
git commit -m "$(cat <<'EOF'
feat: add introducedIn/removedIn version scoping to catalog models

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Statamic version detection service

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/StatamicVersionService.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/StatamicVersionServiceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/catalog/StatamicVersionServiceTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StatamicVersionServiceTest : BasePlatformTestCase() {

    private fun version() = StatamicVersionService.getInstance(project).majorVersion()

    fun testDefaultsToLatestWhenNoComposer() {
        assertEquals(StatamicVersionService.LATEST_MAJOR, version())
    }

    fun testDetectsFive() {
        myFixture.addFileToProject("composer.json", """{ "require": { "statamic/cms": "^5.0" } }""")
        assertEquals(5, version())
    }

    fun testDetectsSix() {
        myFixture.addFileToProject("composer.json", """{ "require": { "statamic/cms": "~6.2" } }""")
        assertEquals(6, version())
    }

    fun testFallsBackToLock() {
        // composer.json without the statamic/cms entry → use composer.lock's exact version.
        myFixture.addFileToProject("composer.json", """{ "require": { "laravel/framework": "^12.0" } }""")
        myFixture.addFileToProject(
            "composer.lock",
            """{ "packages": [ { "name": "statamic/cms", "version": "v5.3.1" } ] }""",
        )
        assertEquals(5, version())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*StatamicVersionServiceTest"`
Expected: FAIL — `StatamicVersionService` does not exist (compile error).

- [ ] **Step 3: Create the service**

Create `src/main/kotlin/com/github/balotias/intellijantlers/catalog/StatamicVersionService.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Resolves the project's Statamic MAJOR version for catalog tailoring: `composer.json`'s
 * `statamic/cms` constraint first, then `composer.lock`'s exact version, else [LATEST_MAJOR].
 * Cached on the PSI modification count (re-resolves after composer edits).
 */
@Service(Service.Level.PROJECT)
class StatamicVersionService(private val project: Project) {

    fun majorVersion(): Int =
        CachedValuesManager.getManager(project).getCachedValue(project, KEY, {
            CachedValueProvider.Result.create(resolve(), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    private fun resolve(): Int {
        shallowest("composer.json")?.let { f -> majorFromJson(load(f))?.let { return it } }
        shallowest("composer.lock")?.let { f -> majorFromLock(load(f))?.let { return it } }
        return LATEST_MAJOR
    }

    private fun shallowest(name: String): VirtualFile? =
        try {
            FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))
                .minByOrNull { it.path.length }
        } catch (e: Exception) {
            null
        }

    private fun load(file: VirtualFile): String =
        try { VfsUtilCore.loadText(file) } catch (e: Exception) { "" }

    private fun majorFromJson(text: String): Int? =
        JSON_CONSTRAINT.find(text)?.groupValues?.get(1)
            ?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

    private fun majorFromLock(text: String): Int? =
        LOCK_VERSION.find(text)?.groupValues?.get(1)?.toIntOrNull()

    companion object {
        const val LATEST_MAJOR = 6

        private val KEY = Key.create<CachedValue<Int>>("antlers.statamicVersion")
        private val JSON_CONSTRAINT = Regex("\"statamic/cms\"\\s*:\\s*\"([^\"]+)\"")
        // composer.lock: { "name": "statamic/cms", … "version": "v6.1.2" }
        private val LOCK_VERSION =
            Regex("\"name\"\\s*:\\s*\"statamic/cms\"[\\s\\S]*?\"version\"\\s*:\\s*\"v?(\\d+)")

        fun getInstance(project: Project): StatamicVersionService = project.service()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*StatamicVersionServiceTest"`
Expected: PASS (4 tests).
If `testDetectsFive` fails because `FilenameIndex` does not see the added file in the test, switch `shallowest` to also try `project.guessProjectDir()?.findChild(name)` as a fallback before returning null, and re-run. Report if you make this change.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/StatamicVersionService.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/catalog/StatamicVersionServiceTest.kt
git commit -m "$(cat <<'EOF'
feat: detect Statamic major version from composer.json/lock (default 6)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Catalog superset + version filtering

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTags.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifiers.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/AntlersCatalogService.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/AntlersCatalogVersionFilterTest.kt`

- [ ] **Step 1: Write the failing filtering test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/catalog/AntlersCatalogVersionFilterTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCatalogVersionFilterTest : BasePlatformTestCase() {

    private fun catalog() = AntlersCatalogService.getInstance(project)

    fun testFiveShowsRelateHidesV6Modifier() {
        myFixture.addFileToProject("composer.json", """{ "require": { "statamic/cms": "^5.0" } }""")
        assertNotNull("relate is a Statamic 5 tag", catalog().tag("relate"))
        assertFalse(
            "urlencode_except_slashes is v6-only",
            catalog().modifiers().any { it.name == "urlencode_except_slashes" },
        )
    }

    fun testSixHidesRelateShowsV6Modifier() {
        myFixture.addFileToProject("composer.json", """{ "require": { "statamic/cms": "^6.0" } }""")
        assertNull("relate was removed in Statamic 6", catalog().tag("relate"))
        assertTrue(
            "urlencode_except_slashes exists in v6",
            catalog().modifiers().any { it.name == "urlencode_except_slashes" },
        )
    }

    fun testDefaultIsLatest() {
        // No composer file → latest (6): relate hidden, v6 modifier shown.
        assertNull(catalog().tag("relate"))
        assertTrue(catalog().modifiers().any { it.name == "urlencode_except_slashes" })
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersCatalogVersionFilterTest"`
Expected: FAIL — `relate` is not in the catalog yet, and the catalog is not filtered by version.

- [ ] **Step 3: Add `relate` to `CatalogTags.kt`**

In `CatalogTags.kt`, add this entry inside the `listOf(` (e.g. right after the first `collection` `TagDef(` … `),` entry — placement within the list does not matter):

```kotlin
        TagDef(
            name = "relate",
            description = "Loop over a relationship field (Statamic 5; removed in 6 — use augmentation).",
            docUrl = "https://statamic.dev/tags/relate",
            isPair = true,
            removedIn = 6,
        ),
```

- [ ] **Step 4: Annotate the v6 modifiers in `CatalogModifiers.kt`**

Change line 147 (`rawurlencode_except_slashes`) and line 188 (`urlencode_except_slashes`) to add `introducedIn = 6`:

```kotlin
        ModifierDef(name = "rawurlencode_except_slashes", description = "Encode except slashes.", docUrl = "https://statamic.dev/modifiers/rawurlencode_except_slashes", introducedIn = 6),
```
```kotlin
        ModifierDef(name = "urlencode_except_slashes", description = "Encode except slashes.", docUrl = "https://statamic.dev/modifiers/urlencode_except_slashes", introducedIn = 6),
```

- [ ] **Step 5: Filter the bundled catalog in `AntlersCatalogService`**

In `AntlersCatalogService.kt`, replace the `tags()` and `modifiers()` methods with version-filtered versions (the bundled lists are filtered before merging scanned customs):

```kotlin
    fun tags(): List<TagDef> {
        val major = StatamicVersionService.getInstance(project).majorVersion()
        val bundled = bundledTags.filter { it.appliesTo(major) }
        val custom = scannedTagNames().filter { name -> bundled.none { it.name == name } }
            .map { TagDef(name = it, description = "Custom tag") }
        return bundled + custom
    }
```

```kotlin
    fun modifiers(): List<ModifierDef> {
        val major = StatamicVersionService.getInstance(project).majorVersion()
        val bundled = bundledModifiers.filter { it.appliesTo(major) }
        val custom = scannedModifierNames().filter { name -> bundled.none { it.name == name } }
            .map { ModifierDef(name = it, description = "Custom modifier") }
        return bundled + custom
    }
```

(`tagNameSet()` already derives from `tags()`, so `isTag` stays consistent with the filtered set; no change needed there.)

- [ ] **Step 6: Run the filtering test**

Run: `./gradlew --rerun-tasks test --tests "*AntlersCatalogVersionFilterTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Full suite gate (the catalog/service is shared)**

Run: `./gradlew --rerun-tasks test`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: prints nothing. (Most existing catalog tests run with no composer file → default v6, where the only changes are `relate` hidden + the v6 modifiers shown, both already true before this change for those tests.)

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogTags.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/catalog/CatalogModifiers.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/catalog/AntlersCatalogService.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/catalog/AntlersCatalogVersionFilterTest.kt
git commit -m "$(cat <<'EOF'
feat: superset catalog (re-add relate) filtered by detected Statamic version

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Syntax-compat parse tests + README

**Files:**
- Create: `src/test/kotlin/com/github/balotias/intellijantlers/AntlersVersionCompatTest.kt`
- Modify: `README.md`

- [ ] **Step 1: Write the syntax-compat parse test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/AntlersVersionCompatTest.kt`:

```kotlin
package com.github.balotias.intellijantlers

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersVersionCompatTest : BasePlatformTestCase() {

    private fun parseErrors(text: String): List<String> {
        val file = myFixture.configureByText("p.antlers.html", text)
        return PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).map { it.errorDescription }
    }

    fun testStatamicFiveIdiomsParseCleanly() {
        val v5 = """
            {{ relate:items }}{{ title }}{{ /relate }}
            {{ session:cart_count }}
            {{ if status == 'published' }}{{ title | upper }}{{ /if }}
        """.trimIndent()
        assertEquals("v5 template should have no parse errors: ${parseErrors(v5)}", emptyList<String>(), parseErrors(v5))
    }

    fun testStatamicSixIdiomsParseCleanly() {
        val v6 = """
            {{ session :handle="cart_count" }}
            {{ value | urlencode_except_slashes }}
            {{ related_posts }}{{ title }}{{ /related_posts }}
            {{ unless hidden }}{{ content }}{{ /unless }}
        """.trimIndent()
        assertEquals("v6 template should have no parse errors: ${parseErrors(v6)}", emptyList<String>(), parseErrors(v6))
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew --rerun-tasks test --tests "*AntlersVersionCompatTest"`
Expected: PASS (2 tests). If either reports parse errors, print them — they indicate a genuine grammar gap for that idiom; STOP and report rather than weakening the assertion (the spec's premise is that both idioms already parse).

- [ ] **Step 3: Relabel the README**

In `README.md` line 23, change:
```
**Completion** (backed by a bundled Statamic 6 catalog + custom tags/modifiers discovered in your project)
```
to:
```
**Completion** (backed by a bundled Statamic 5 & 6 catalog — tailored to the version detected in your `composer.json` — plus custom tags/modifiers discovered in your project)
```
If any other line in `README.md` says "Statamic 6" as the sole supported version, update it to "Statamic 5 & 6". (Run `grep -n "Statamic 6" README.md` and reconcile.)

- [ ] **Step 4: Full suite gate**

Run: `./gradlew --rerun-tasks test`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: prints nothing.

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/AntlersVersionCompatTest.kt README.md
git commit -m "$(cat <<'EOF'
test+docs: prove v5/v6 idioms parse; relabel README to Statamic 5 & 6

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (completed during planning)

- **Spec coverage:** version fields + predicate (Task 1) ✓; `StatamicVersionService` composer.json→lock→latest with detection tests (Task 2) ✓; `relate` re-added + v6 modifier annotation + version filtering with filter tests (Task 3) ✓; zero-error v5/v6 parse tests + README relabel (Task 4) ✓.
- **Predicate consistency:** `appliesTo` defined identically on both models; filter call sites use `it.appliesTo(major)`; traced v5 (relate in, variants out), v6/default (relate out, variants in).
- **Type consistency:** `StatamicVersionService.getInstance(project).majorVersion()`, `LATEST_MAJOR`, `introducedIn`/`removedIn` referenced identically across service, models, catalog, tests.
- **No placeholders:** every code step complete; run steps have commands + expected output; Task 2/4 include reconcile/STOP guidance rather than weakening assertions.
- **Shared-state check:** `tagNameSet()` derives from the now-filtered `tags()`, keeping `isTag` (highlighting) consistent with completion.
```
