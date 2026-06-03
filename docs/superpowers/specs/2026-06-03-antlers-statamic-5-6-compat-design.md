# Antlers Statamic 5 & 6 Compatibility — Design

**Date:** 2026-06-03
**Branch:** `antlers-statamic-5-6-compat`
**Status:** Approved approach, pending spec review

## Goal

Make the plugin explicitly support both Statamic 5 and Statamic 6 Antlers templates, and tailor the
bundled catalog to the project's **detected** Statamic version (falling back to the latest by default).

Research (official 5→6 upgrade guide) shows the two versions share **identical Antlers syntax** — the
breaking changes are backend/Control-Panel (PHP 8.3, Laravel 12, Vue 3, Carbon 3, timezones, Bard
config, globals storage) plus a few template *behavior* changes (e.g. `*_ago` modifiers return floats)
that do not affect parsing, highlighting, or completion. So the parser already handles both versions and
needs no change. The IDE-visible deltas are confined to a handful of catalog entries, which we make a
5∪6 **superset** and then **filter by the detected version**.

## Background (current state)

- `AntlersCatalogService` merges the bundled catalog (`CatalogData.TAGS` = 48 `TagDef`,
  `CatalogData.MODIFIERS` = 189 `ModifierDef`) with project-scanned custom/vendor tags & modifiers
  (`TagScanner`/`ModifierScanner` read `class … extends Tags` etc. in project scope).
- The bundled catalog is already v6-current: `urlencode_except_slashes` and
  `rawurlencode_except_slashes` (the v6 modifier variants) are present.
- **`relate`** (the only Antlers tag v6 removed — a Statamic-2 leftover, replaced by augmentation) is
  **not** bundled.
- There is no version detection today.
- The parser/lexer is permissive and already accepts both wildcard idioms: `{{ session:foo }}` (v5) and
  `{{ session :handle="foo" }}` (v6).

## Antlers-relevant 5↔6 deltas (the complete IDE-affecting set)

| Delta | Versions valid | IDE impact | Action |
|---|---|---|---|
| `relate` tag | 5 only (removed in 6) | missing from catalog | add (`removedIn = 6`) |
| `urlencode_except_slashes`, `rawurlencode_except_slashes` | 6+ | present | tag `introducedIn = 6` |
| `urlencode`/`rawurlencode` encode slashes now | behavior | none | none |
| `*_ago` return floats; timezone/UTC | behavior | none | none |
| Bard strike `<s>`→`<strike>` | behavior (HTML) | none | none |
| wildcard `session:foo` → `session :handle="foo"` | idiom | both parse & complete | none (covered by test) |

No modifiers were removed in v6; `relate` is the only removed tag.

## Components

### 1. Version detection — `catalog/StatamicVersionService.kt` (new project service)

Resolves the project's Statamic **major** version (an `Int`), cached:

- **`composer.json` first** (per the request): find it via
  `FilenameIndex.getVirtualFilesByName("composer.json", GlobalSearchScope.projectScope(project))`,
  prefer the shallowest path (project root). Load text, regex
  `"statamic/cms"\s*:\s*"([^"]+)"`, take the first digit-run of the constraint as the major
  (`^6.0` → 6, `~5.2` → 5, `6.*` → 6).
- **`composer.lock` fallback** (more precise, when `composer.json` lacks the entry): find
  `composer.lock`, regex for the `statamic/cms` package block and its `"version": "v?(\d+)…"`.
- **Default: `LATEST_MAJOR = 6`** when neither yields a version ("fall back to the latest by default").

Public API: `fun majorVersion(): Int`. Caching: `CachedValuesManager.getCachedValue` keyed on the
resolved `composer.json`/`composer.lock` `VirtualFile` as the dependency (re-resolves when that file
changes); if no composer file is found, cache on a stable tracker so a later-added composer file is
still picked up on the next VFS change (acceptable: depend on `VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS`).
`LATEST_MAJOR` is a single top-level constant so bumping the default for Statamic 7 is one edit.

### 2. Version metadata on the catalog — `CatalogModels.kt`

Add two nullable fields to **both** `TagDef` and `ModifierDef` (defaults keep every existing entry
all-versions):

```kotlin
val introducedIn: Int? = null,   // first major version that has it (null = always)
val removedIn: Int? = null,      // first major version that DROPPED it (null = never)
```

An entry is valid for major `v` iff `(introducedIn == null || v >= introducedIn) && (removedIn == null || v < removedIn)`.

### 3. Catalog entries — `CatalogTags.kt`, `CatalogModifiers.kt`

- Add `relate` (pair) with `removedIn = 6`:
  ```kotlin
  TagDef(
      name = "relate",
      description = "Loop over a relationship field (Statamic 5; removed in 6 — use augmentation).",
      docUrl = "https://statamic.dev/tags/relate",
      isPair = true,
      removedIn = 6,
  ),
  ```
- Set `introducedIn = 6` on the two existing `*_except_slashes` `ModifierDef`s.
- If a wider audit surfaces any other 5-only / 6-only entry, annotate it the same way. (Per the guide,
  `relate` is the only removed tag and the `_except_slashes` pair the only new modifiers.)

### 4. Filter the bundled catalog by version — `AntlersCatalogService`

`tags()` and `modifiers()` filter the **bundled** lists through the validity predicate against
`StatamicVersionService.getInstance(project).majorVersion()`. **Project-scanned** custom/vendor entries
are always kept (they reflect what is actually installed). The cached `tagNameSet()` (used by the hot
`isTag` highlight path) is derived from the already-filtered `tags()`, so highlighting stays consistent
with completion.

### 5. README relabel

"backed by a bundled Statamic 6 catalog" → "backed by a bundled Statamic 5 & 6 catalog (tailored to the
version detected in your `composer.json`)". Adjust any other "Statamic 6"-only phrasing.

## Architecture

Additive: one new project service (regex composer parsing, cached), two nullable model fields, a few
catalog annotations, a filter in the existing merge, a README edit. No parser/grammar change, no JSON
dependency. The dynamic scanner continues to cover installed/custom tags; version filtering only refines
the **bundled** fallback so it matches the project's Statamic line, defaulting to the latest.

## Testing (`BasePlatformTestCase`)

- **Version detection** (`StatamicVersionServiceTest`): write a `composer.json` via
  `myFixture.addFileToProject` with `"statamic/cms": "^5.0"` → `majorVersion() == 5`; `"^6.0"` → 6;
  no composer file → `LATEST_MAJOR` (6); a `composer.lock`-only project with an exact
  `statamic/cms` `"v5.3.1"` → 5.
- **Catalog filtering** (`AntlersCatalogServiceTest` or new): with a v5 `composer.json`, `tag("relate")`
  is non-null and `modifiers()` does **not** contain `urlencode_except_slashes`; with a v6 (or no)
  composer.json, `tag("relate")` is null and `urlencode_except_slashes` **is** present.
- **Syntax compat** (`AntlersVersionCompatTest`): two representative templates parse with **zero**
  `PsiErrorElement`s — a v5-leaning one (`{{ relate:items }}…{{ /relate }}`, `{{ session:cart }}`) and a
  v6-leaning one (`{{ session :handle="cart" }}`, `{{ value | urlencode_except_slashes }}`,
  `{{ related_posts }}…{{ /related_posts }}`).
- Full-suite regression gate (the catalog/service touch shared completion paths).

## Out of scope

- Per-version *diagnostics* (e.g. warning on a v5 idiom in a v6 project) — a possible future inspection.
- Minor/patch version granularity — only the **major** drives catalog selection.
- Behavior-only changes (float `*_ago`, UTC dates, Bard HTML) — no IDE surface.
