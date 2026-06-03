# Antlers `views/partials/` Implementation Plan

> Resolve + list partials from `resources/views/partials/` (short name), `partials/` winning on clash. Execute INLINE. Single task.

**Spec:** `docs/superpowers/specs/2026-06-03-antlers-partials-folder-design.md`
**Branch:** `antlers-partials-folder` (from `main`).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty).

**Files:**
- Modify: `src/main/kotlin/.../references/StatamicProject.kt`
- Modify: `src/main/kotlin/.../references/AntlersPartialReference.kt`
- Test: `src/test/kotlin/.../references/AntlersPartialReferenceTest.kt`

---

### Step 1: Write the failing tests

In `AntlersPartialReferenceTest`, add a helper + tests:

```kotlin
    private fun resolvePartialAt(text: String): PsiFile? {
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return file.findReferenceAt(caret)?.resolve() as? PsiFile
    }

    fun testResolvesShortNameFromPartialsFolder() {
        myFixture.addFileToProject("resources/views/partials/btn.antlers.html", "<button></button>")
        val t = resolvePartialAt("{{ partial:b<caret>tn }}")
        assertNotNull("partials/ short name should resolve", t)
        assertEquals("btn.antlers.html", t!!.name)
        assertEquals("resolved from the partials folder", "partials", t.virtualFile.parent.name)
    }

    fun testPartialsFolderWinsOnNameClash() {
        myFixture.addFileToProject("resources/views/btn.antlers.html", "root")
        myFixture.addFileToProject("resources/views/partials/btn.antlers.html", "in-partials")
        val t = resolvePartialAt("{{ partial:b<caret>tn }}")
        assertNotNull(t)
        assertEquals("partials/ wins over views root", "partials", t!!.virtualFile.parent.name)
    }

    fun testRootFallbackStillResolves() {
        myFixture.addFileToProject("resources/views/btn.antlers.html", "root")
        val t = resolvePartialAt("{{ partial:b<caret>tn }}")
        assertNotNull(t)
        assertEquals("falls back to views root", "views", t!!.virtualFile.parent.name)
    }

    fun testCompletionOffersPartialsFolderShortName() {
        myFixture.addFileToProject("resources/views/partials/btn.antlers.html", "x")
        myFixture.addFileToProject("resources/views/partials/card.antlers.html", "x")  // 2nd, so the popup stays open
        myFixture.configureByText("page.antlers.html", "{{ partial:src=\"<caret>\" }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue("offers btn (short name): $variants", variants.contains("btn"))
        assertFalse("not the partials/ path form: $variants", variants.contains("partials/btn"))
    }
```
(`PsiFile` is already imported in this test.)

- [ ] Run `./gradlew --rerun-tasks test --tests "*AntlersPartialReferenceTest"` → expect the 4 new tests
  FAIL (resolver/list only see the views root today).

---

### Step 2: Add `resolvePartial` + adjust `listPartials` in `StatamicProject.kt`

Add the resolver (e.g. after `viewsRoot`):
```kotlin
    /** Resolves a partial [path] to its file: `views/partials/<path>` (preferred) then `views/<path>`. */
    fun resolvePartial(element: PsiElement, path: String): VirtualFile? {
        val root = viewsRoot(element) ?: return null
        val exts = listOf("antlers.html", "html")
        for (ext in exts) root.findFileByRelativePath("partials/$path.$ext")?.let { return it }
        for (ext in exts) root.findFileByRelativePath("$path.$ext")?.let { return it }
        return null
    }
```

Change `listPartials` to de-duplicate and offer `partials/` files by short name. Replace:
```kotlin
    fun listPartials(element: PsiElement): List<String> {
        val root = viewsRoot(element) ?: return emptyList()
        val out = mutableListOf<String>()
        collectPartials(root, root, out)
        return out
    }
```
with:
```kotlin
    fun listPartials(element: PsiElement): List<String> {
        val root = viewsRoot(element) ?: return emptyList()
        val out = LinkedHashSet<String>()
        collectPartials(root, root, out)
        return out.toList()
    }
```
and change `collectPartials`' signature + body to strip the `partials/` prefix:
```kotlin
    private fun collectPartials(root: VirtualFile, dir: VirtualFile, out: MutableSet<String>) {
        for (child in dir.children) {
            if (child.isDirectory) {
                collectPartials(root, child, out)
            } else if (child.name.endsWith(".antlers.html") || child.name.endsWith(".html")) {
                val rel = relativePath(root, child)?.removeSuffix(".antlers.html")?.removeSuffix(".html") ?: continue
                out.add(rel.removePrefix("partials/"))   // a `views/partials/btn` partial is offered as `btn`
            }
        }
    }
```

---

### Step 3: `AntlersPartialReference.resolve()` uses the helper

Replace the body of `resolve()`:
```kotlin
    override fun resolve(): PsiElement? {
        val root = StatamicProject.viewsRoot(element) ?: return null
        for (ext in PARTIAL_EXTENSIONS) {
            val vf = root.findFileByRelativePath("$path.$ext") ?: continue
            return PsiManager.getInstance(element.project).findFile(vf)
        }
        return null
    }
```
with:
```kotlin
    override fun resolve(): PsiElement? {
        val vf = StatamicProject.resolvePartial(element, path) ?: return null
        return PsiManager.getInstance(element.project).findFile(vf)
    }
```
(The top-level `PARTIAL_EXTENSIONS` val is now unused — remove it if nothing else references it; `grep -n PARTIAL_EXTENSIONS src/main` first.)

---

### Step 4: Run + reconcile

- [ ] `./gradlew --rerun-tasks test --tests "*AntlersPartialReferenceTest"` → PASS (new + existing).
- [ ] Check nothing else pinned the old `partials/<name>` listing:
  `grep -rn '"partials/' src/test` — if a test expected a `partials/`-prefixed completion, update it to
  the short name (report it).
- [ ] Full gate: `./gradlew --rerun-tasks test` + `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty).

---

### Step 5: Commit

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/StatamicProject.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReference.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceTest.kt
git commit -m "$(cat <<'EOF'
feat: resolve & list partials from views/partials/ (short name, partials/ wins)

`{{ partial:btn }}` now resolves views/partials/btn.antlers.html (preferred)
or views/btn.antlers.html, and the partials/ short name is offered in
completion — fixing partial navigation and listing for the partials-folder
convention.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Self-Review
- `resolvePartial` tries all `partials/` candidates before any root candidate → partials/ wins on clash.
- `listPartials` strips the `partials/` prefix and de-dups (LinkedHashSet) → short name shown once.
- `resolve()` delegates to the shared helper → nav (#8) and completion (#2) both follow the partials/ rule.
- Existing `views/`-root, `blog/card`, and `src=` cases unchanged (no `partials/` prefix to strip).
