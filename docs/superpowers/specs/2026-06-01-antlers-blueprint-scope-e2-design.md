# Antlers Blueprint Scoping E2 — Template → Page-Blueprint Mapping — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Sub-project **E2** of "precise blueprint scoping". At the **top level** of a template (no
enclosing loop scope), narrow `{{ variable }}` completion / go-to-definition / docs to the blueprint of
the collection(s) whose `template:` points at the current file — instead of offering every blueprint
field. Builds on E1 (namespaces + scope resolver). E3 (nested fieldsets + dotted access) is out of scope.

## 1. Background & Goal

E1 made `{{ }}` aware of enclosing iterating-tag scope and tagged every field with a
`BlueprintNamespace`. At the **top level** (outside any loop) E1 still falls back to the global list of
all fields. E2 closes that gap for the common case: a collection configures the view that renders its
entries via the `template:` key in `content/collections/<handle>.yaml`. E2 reads those keys, maps the
current `.antlers.html` file back to its collection(s), and restricts top-level fields to that
collection's blueprint.

The mapping is a **confidence-graded heuristic** — a template may be referenced by several collections,
by none (shared layouts/partials), or be overridden per entry (invisible from the file). E2 therefore
restricts only when it has an explicit `template:` match and otherwise falls back to E1's global
behavior. Per the design decision, only the explicit `template:` signal is used in v1 (directory
convention and `layout:` are deferred).

## 2. Principles

1. **Explicit signal only.** Map solely via `content/collections/*.yaml` `template:` keys. No directory
   convention, no `layout:`, no taxonomy/global configs in v1.
2. **Loop scope always wins.** Page mapping applies only when E1's scope resolver finds no enclosing
   loop. Inside `{{ collection:… }}` the loop scope (E1) is authoritative.
3. **Restrict when matched, broad when not.** A confident match restricts top-level fields to the
   mapped collection(s); no match → E1's global fallback (all fields). Never offer nothing.
4. **Tolerant.** The config scanner and resolver never throw; missing `content/`, malformed YAML, or an
   unmatched file all degrade to the global fallback.
5. **DRY the wiring.** Completion, references, and docs route field resolution through one
   `AntlersFieldContext` so the loop-then-page-then-global precedence lives in a single place.

## 3. Components

### 3.1 Collection config model + scanner (`blueprint/CollectionConfig.kt`, `CollectionConfigScanner.kt`)

```kotlin
data class CollectionConfig(val handle: String, val template: String)
```

`CollectionConfigScanner` (object): `scan(project): List<CollectionConfig>` — mirrors
`BlueprintScanner` (no project-root walk; uses the file index + a path filter):
- `FilenameIndex.getAllFilesByExt(project, "yaml", GlobalSearchScope.projectScope(project))`.
- Keep only files that are a **direct child** of a `content/collections` directory — the path contains
  `/content/collections/` and the remainder after it has no further `/` (this is the collection
  *config*; the same-named entries subdirectory holds `*.md` and is excluded by the no-further-slash
  rule).
- For each: `handle` = filename without `.yaml`; `template` = the value of a top-level `template:` line,
  via a tolerant regex `^\s*template:\s*['"]?([^'"\n]+?)['"]?\s*$` with quotes stripped.
- Return only configs with a non-blank `template` (a config without `template:` contributes no mapping
  in v1). Tolerant: unreadable/malformed files are skipped; never throws (guard
  `ProcessCanceledException` like `BlueprintScanner`).

### 3.2 Collection config service (`blueprint/CollectionConfigService.kt`)

`@Service(Service.Level.PROJECT)` (NOT registered in plugin.xml). Caches `CollectionConfigScanner.scan`
on `PsiModificationTracker.MODIFICATION_COUNT` (same pattern as `BlueprintService`). Exposes:

```kotlin
fun configs(): List<CollectionConfig>
```

### 3.3 Page-blueprint resolver (`blueprint/PageBlueprintResolver.kt`)

```kotlin
/** COLLECTION namespaces whose template: points at [element]'s file. Empty when none. */
fun namespacesFor(element: PsiElement): List<BlueprintNamespace>
```

Steps:
1. `viewsRoot = StatamicProject.viewsRoot(element)` and the file's virtual file; null/absent → empty.
2. Compute the file's **view path**: the path relative to `viewsRoot`, with the `.antlers.html` suffix
   removed and `/` separators (e.g. `…/resources/views/blog/show.antlers.html` → `blog/show`).
3. For each `CollectionConfigService.configs()` entry, normalize both the entry's `template` and the
   file's view path by replacing `.` with `/` (Statamic accepts both separators), and compare for
   equality.
4. Map every matching config's `handle` to `BlueprintNamespace(COLLECTION, handle)`; dedupe. Return the
   list (possibly several; possibly empty).

### 3.4 Unified field context (`scope/AntlersFieldContext.kt`)

The single authority for "which blueprint namespaces apply at this element", combining E1 loop scope
with E2 page mapping.

```kotlin
object AntlersFieldContext {
    /**
     * null → no loop scope and no page mapping: callers use the global fallback (all fields).
     * non-null → restrict to these namespaces, innermost/most-specific first.
     */
    fun namespacesFor(element: PsiElement): List<BlueprintNamespace>?

    /** Fields visible at [element]: scoped union (deduped), or null for the global fallback. */
    fun fieldsInScope(element: PsiElement, project: Project): List<BlueprintField>?

    /** Resolve [handle] within the applicable namespaces, else the global lookup; null if unknown. */
    fun resolveField(element: PsiElement, handle: String, project: Project): BlueprintField?
}
```

`namespacesFor` precedence:
1. `AntlersScopeResolver.scopesAt(element)` non-empty → its `BlueprintScope` namespaces (innermost first).
2. Else `PageBlueprintResolver.namespacesFor(element)` non-empty → those.
3. Else `null`.

`fieldsInScope` = `namespacesFor(...)?.flatMap { BlueprintService.getInstance(project).fieldsFor(it) }`
deduped by handle (first wins); `null` stays `null`.
`resolveField` = first non-null `fieldsFor(ns).firstOrNull { it.handle == handle }` over
`namespacesFor`, or `BlueprintService.field(handle)` when `namespacesFor` is `null`.

This **supersedes** `scope/AntlersScopeFields` from E1: `AntlersScopeFields.fieldsInScope` is replaced by
`AntlersFieldContext.fieldsInScope` (delegating to `namespacesFor`). The E1 `AntlersScopeFields` file is
removed and its single caller (completion) updated; `AntlersScopeResolver` is unchanged.

### 3.5 Consumer wiring

- **Completion** (`completion/AntlersCompletionProvider.kt`, TAG_NAME branch): replace
  `AntlersScopeFields.fieldsInScope(parameters.position, project)` with
  `AntlersFieldContext.fieldsInScope(parameters.position, project)`. Loop-meta vars remain gated on an
  **iterating** scope (E1's `AntlersScopeResolver.scopesAt(...).isNotEmpty()`), **not** on a page match —
  a page is not a loop. So compute `val scopes = AntlersScopeResolver.scopesAt(parameters.position)` for
  the loop-var gate, and `AntlersFieldContext.fieldsInScope` for the field list.
- **References** (`references/AntlersBlueprintFieldReference.kt`): resolve via
  `AntlersFieldContext.resolveField(element, handle, element.project)`. Stays soft.
- **Docs** (`documentation/AntlersDocumentationProvider.kt`, head branch): resolve the field via
  `AntlersFieldContext.resolveField(ident, name, ident.project)`. When the field came from a non-`UNKNOWN`
  namespace (i.e. a scoped/page match rather than the global fallback), append the namespace label as in
  E1. Determine "scoped vs global" by `AntlersFieldContext.namespacesFor(ident) != null`.

## 4. File / Package Layout

```
blueprint/CollectionConfig.kt              (new)
blueprint/CollectionConfigScanner.kt       (new)
blueprint/CollectionConfigService.kt       (new — @Service)
blueprint/PageBlueprintResolver.kt         (new)
scope/AntlersFieldContext.kt               (new — unifies loop scope + page mapping)
scope/AntlersScopeFields.kt                (removed — folded into AntlersFieldContext)
completion/AntlersCompletionProvider.kt    (use AntlersFieldContext + loop-var gate)
references/AntlersBlueprintFieldReference.kt (use AntlersFieldContext.resolveField)
documentation/AntlersDocumentationProvider.kt (use AntlersFieldContext.resolveField)
```

No `plugin.xml` changes (`CollectionConfigService` is a `@Service`).

## 5. Testing

`BasePlatformTestCase` (runs via `./gradlew test`), plus a JUnit4 unit test for the scanner. Fixtures:
- `content/collections/blog.yaml` → `template: blog/show`
- `content/collections/news.yaml` → `template: news/show`
- `resources/blueprints/collections/blog/blog.yaml` → field `hero_title` (display "Hero Title")
- `resources/blueprints/collections/news/news.yaml` → field `news_only`
- template file added at `resources/views/blog/show.antlers.html`

Tests:
- **Config scanner** (`CollectionConfigScannerTest`): extracts `handle`+`template`; a config with no
  `template:` yields no entry; malformed YAML does not throw.
- **View-path match** (`PageBlueprintResolverTest`): `blog/show` and `blog.show` template values both map
  the file `views/blog/show.antlers.html` to `COLLECTION "blog"`; a file with no matching template → empty.
- **Page completion** (`AntlersPageCompletionTest`): top-level `{{ <caret> }}` in
  `views/blog/show.antlers.html` offers `hero_title`, excludes `news_only`, and offers **no** loop-meta
  vars (`index` absent); a template referenced by no collection still offers everything (global fallback).
- **Page nav + docs** (`AntlersPageNavTest`): top-level `{{ hero_<caret>title }}` in
  `views/blog/show.antlers.html` resolves to `blog`'s blueprint; the doc names the `blog` collection.
- **Loop still wins** (`AntlersPageNavTest` or completion): inside `{{ collection:news }}…{{ /collection }}`
  within `blog/show.antlers.html`, completion/nav use `news` (E1 scope), not the page's `blog` mapping.
- **Regression**: the E1 scope tests and v1 variable tests stay green (file outside any views root → no
  page match → global fallback unchanged).

## 6. Out of Scope (E3 / follow-ups)

- Nested fieldset sub-fields (Bard / Replicator / Grid) and dotted access `{{ author.name }}` (E3).
- Convention-default templates (collections with no explicit `template:`).
- Per-entry `template` overrides (invisible from the template file).
- Cascade access to page fields *inside* a loop (parent scope).
- `layout:` / taxonomy / global-set page mapping; directory-convention mapping; non-default `content/`
  paths (Stache config).

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Per-entry `template` override invisible from the file | Documented limitation; map by collection config only — global fallback still covers genuinely unmapped files |
| One template shared by multiple collections | Union of namespaces — explicit in config, honest; noisier but not wrong |
| Non-default `content/` path (Stache config) | v1 assumes default `content/collections`; no match → global fallback, never a crash |
| Scanner/resolver cost per keystroke | Configs cached on `MODIFICATION_COUNT` (same as `BlueprintService`); view-path compare is a few string ops |
| Refactor of E1 references/docs wiring regresses scope behavior | `AntlersFieldContext` preserves E1 precedence exactly (loop scopes first); E1's existing tests guard it |
| `template:` quotes / dot-notation | Regex strips quotes; both `.` and `/` normalized on both sides before compare |
| `content/collections` discovery | `FilenameIndex` + path filter (like `BlueprintScanner`); absence → empty configs → global fallback, no project-root walk needed |
