# Antlers Statamic 5 & 6 Compatibility — Design

**Date:** 2026-06-03
**Branch:** `antlers-statamic-5-6-compat`
**Status:** Approved approach, pending spec review

## Goal

Make the plugin explicitly support both Statamic 5 and Statamic 6 Antlers templates. Research
(official 5→6 upgrade guide) shows the two versions share **identical Antlers syntax** — the breaking
changes are backend/Control-Panel (PHP 8.3, Laravel 12, Vue 3, Carbon 3, timezones, Bard config,
globals storage) plus a few template *behavior* changes (e.g. `*_ago` modifiers return floats) that do
not affect parsing, highlighting, or completion. So the parser already handles both, and the dynamic
PHP scanner already reflects whatever version is installed. The only IDE-visible gap is the **bundled
fallback catalog**, which is v6-flavored and missing the one tag v6 removed.

This is therefore a small, additive change: make the bundled catalog a 5∪6 **superset**, add
verification tests proving both versions' syntax parses cleanly, and relabel the README. **No version
detection and no parser change** (YAGNI — the scanner + identical syntax already cover it).

## Background (current state)

- `AntlersCatalogService` merges the bundled catalog (`CatalogData.TAGS` = 48 `TagDef`, `CatalogData.MODIFIERS`
  = 189 `ModifierDef`) with project-scanned custom/vendor tags & modifiers (`TagScanner`/`ModifierScanner`
  read `class … extends Tags` etc. in project scope), so installed-version tag *names* already surface.
- The bundled catalog is already v6-current: `urlencode_except_slashes` and `rawurlencode_except_slashes`
  (the new v6 modifier variants) are present.
- **`relate`** (the only Antlers tag v6 removed — a Statamic-2 leftover, replaced by augmentation) is
  **not** bundled.
- The parser/lexer is permissive and already accepts both wildcard idioms: `{{ session:foo }}` (v5) and
  `{{ session :handle="foo" }}` (v6).

## Antlers-relevant 5↔6 deltas (the complete IDE-affecting set)

| Delta | Version | IDE impact | Action |
|---|---|---|---|
| `relate` tag removed | v5 only | missing from fallback catalog | **add to catalog** (note: removed in 6) |
| `urlencode_except_slashes`, `rawurlencode_except_slashes` | v6 only | — | already present, keep |
| `urlencode`/`rawurlencode` now encode slashes | behavior | none (still same modifier) | none |
| `*_ago` return floats; timezone/UTC | behavior | none (runtime) | none |
| Bard strike `<s>`→`<strike>` | behavior (HTML output) | none | none |
| wildcard `session:foo` → `session :handle="foo"` | idiom | both already parse & complete | none (covered by test) |

No modifiers were removed in v6; `relate` is the only removed tag.

## Components

### 1. Catalog superset — `CatalogTags.kt`

Add the `relate` tag (a pair tag) with a version-annotated description; version info lives in the
`description` string only (no `TagDef`/`ModifierDef` model change):

```kotlin
TagDef(
    name = "relate",
    description = "Loop over a relationship field (Statamic 5; removed in 6 — use augmentation).",
    docUrl = "https://statamic.dev/tags/relate",
    isPair = true,
),
```

If a wider audit surfaces any other tag/modifier that exists in 5 but not 6, add it the same way with a
"Statamic 5; removed in 6" note. (Per the official guide, `relate` is the only one.)

### 2. Verification tests — `AntlersVersionCompatTest.kt` (new)

Two representative templates, asserting **zero `PsiErrorElement`s** after parsing (the syntax-compat
guarantee), via `PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)`:

- **v5-leaning:** uses `{{ relate:items }}…{{ /relate }}`, the wildcard colon idiom
  `{{ session:cart_count }}`, a conditional, and a modifier chain.
- **v6-leaning:** uses the param idiom `{{ session :handle="cart_count" }}`,
  `{{ value | urlencode_except_slashes }}`, augmented relation access `{{ related_posts }}…{{ /related_posts }}`,
  and a conditional.

Plus a `AntlersCatalogService` assertion: `tag("relate")` is non-null, and `modifiers()` contains
`urlencode_except_slashes` and `rawurlencode_except_slashes` (guards the superset).

### 3. README relabel

Change the completion line from "backed by a bundled Statamic 6 catalog" to "backed by a bundled
Statamic 5 & 6 catalog", and (if present) any other "Statamic 6"-only phrasing to "Statamic 5 & 6".

## Architecture

Purely additive and data-level: one catalog entry, one new test file, one README edit. No new service,
no model field, no parser/grammar/dependency change. The existing dynamic scanner + permissive parser
do the version-adaptation work; this change just makes the *fallback* catalog and the *documentation*
explicitly cover both versions, with tests pinning the syntax-compat claim.

## Testing

- `AntlersVersionCompatTest`: the two zero-error parse tests + the catalog superset assertion above.
- Run the full suite as a regression gate (the catalog addition touches a shared list).

## Out of scope

- Version detection from `composer.json`/`composer.lock` and per-version gating of completions (the
  scanner already reflects the installed version; the syntax is identical — not worth the code).
- Migrating/flagging v5 idioms as deprecated (e.g. warning on `{{ session:foo }}` in a v6 project) — a
  possible future inspection, not part of compatibility.
- Behavior-only changes (float `*_ago`, UTC dates, Bard HTML) — no IDE surface.
