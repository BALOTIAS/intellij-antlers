# Antlers Nav/Structure Scoping J2 — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Sub-project **J2** of "Antlers completeness". Make `{{ nav:… }}…{{ /nav }}` an iterating
**scope** that offers the underlying nav/collection blueprint fields plus nav-tree meta variables
(`depth`, `is_current`, `children`, …), with recursive `{{ children }}`. Extends the E1–E3b scope
machinery. J3 (deeper augmentation) is the next sub-project; not here.

## 1. Background & Goal

The scope resolver (E1) opens field scopes for a fixed set of iterating tags (`collection`, `taxonomy`,
`users`, `form`, `assets`) and, since E3a, container blueprint fields. The `nav` tag was left as a
transparent frame — so inside `{{ nav:main }}…{{ /nav }}` the editor offers nothing scope-aware, even
though each item exposes blueprint fields and a rich set of nav-tree variables.

J2 promotes `nav` to an active scope: it resolves the nav's namespace (a navigation's own blueprint, a
collection's blueprint, or the `pages` default), offers those fields plus nav-meta + loop variables, and
treats `{{ children }}` as a recursive re-entry into the same nav scope. Platform-only; no grammar
changes.

## 2. Principles

1. **Reuse E1.** A nav scope is a `BlueprintScope`; fields flow through `AntlersFieldContext` →
   completion/references/docs unchanged. Only completion gains the nav-meta offering.
2. **Deterministic namespace + meta when uncertain.** `nav:collection:<h>`/`nav:<h>` resolve a concrete
   namespace; if none resolves, the scope still offers nav-meta + loop vars (no fields) — never nothing.
3. **Nav-meta is gated.** A `navMeta` flag on the scope ensures nav variables appear only inside nav
   loops, not ordinary `collection`/container loops.
4. **Tolerant.** Unknown handles, missing nav blueprints, unbalanced `children` all degrade gracefully;
   nothing throws.

## 3. Components

### 3.1 NAVIGATION namespace (`blueprint/BlueprintNamespace.kt`)

Add `NAVIGATION` to `Kind`, and a `fromPath` mapping (file-based, like forms/assets):

```
…/resources/blueprints/navigation/<handle>.yaml  →  NAVIGATION <handle>
```

`fromPath` gains one `fileAfter(p, "/resources/blueprints/navigation/")?.let { NAVIGATION it }` clause.
A nav's link-field blueprint is then addressable like any other namespace, and
`BlueprintService.fieldsFor(NAVIGATION "main")` returns its fields. (`BlueprintScanner` needs no change —
it already scans everything under `/resources/blueprints/` and stamps the namespace from the path.)

### 3.2 Nav-flagged scope (`scope/AntlersScope.kt`)

```kotlin
data class BlueprintScope(val namespace: BlueprintNamespace, val navMeta: Boolean = false) : AntlersScope
```

`navMeta` defaults false, so every existing `BlueprintScope(ns)` construction stays valid. It is set true
only for scopes opened by the `nav` tag (and its `children` re-entry). `AntlersFieldContext.namespacesFor`
maps scopes to namespaces and ignores the flag — field resolution is unaffected.

### 3.3 Nav handling in the resolver (`scope/AntlersScopeResolver.kt`)

In the opener branch, handle a `nav` head before the generic iterating/container logic:
- Compute the nav namespace from the namePath segments (using the existing `AntlersNamePathMixin.segments`):
  - `[nav, "collection", <h>, …]` → `COLLECTION <h>`.
  - `[nav, <h>, …]` (h ≠ "collection") → `NAVIGATION <h>`.
  - `[nav]` only → if a `handle=`/`from=` parameter is present → `NAVIGATION <paramValue>`; else
    `COLLECTION "pages"` (Statamic default).
- Push an **active** frame with that namespace **and `navMeta=true`**. Honor `as="alias"` via the
  existing alias mechanism. If the namespace can't be resolved to a concrete handle, still push an active
  nav frame whose namespace is `BlueprintNamespace.UNKNOWN` with `navMeta=true` (fields empty, meta still
  offered).
- The `Frame` data class gains a `navMeta: Boolean` field threaded into the emitted `BlueprintScope`.

**Recursive `children`:** when an opening tag's head is `children` and an enclosing **active nav frame**
exists, push a new active frame with that frame's namespace and `navMeta=true` (so `{{ children }}…
{{ /children }}` re-exposes the nav item's fields + meta). This mirrors the alias activation already in
the resolver — keyed on the literal name `children` against any open nav frame.

`nav` is an existing catalog pair tag, so its `{{ /nav }}` closer already balances the stack via the
generic closing-tag logic.

### 3.4 Nav-meta variables (`scope/NavVariables.kt`)

A curated list mirroring `LoopVariables`:

```kotlin
data class NavVariable(val name: String, val description: String)
object NavVariables {
    val ALL = listOf(
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

### 3.5 Completion wiring (`completion/AntlersCompletionProvider.kt`)

The only consumer change. In the `TAG_NAME` branch, after the existing loop-var offering (gated on
`AntlersScopeResolver.scopesAt(position).isNotEmpty()`), add: when
`AntlersScopeResolver.scopesAt(position).any { it.navMeta }`, offer `NavVariables.ALL` (typeText "Nav",
deduped against catalog tags / fields / loop-vars / system-vars via the shared `seen` set).

Blueprint fields inside the nav scope, plus their go-to-def and docs, already flow through
`AntlersFieldContext` — no change. Nav-meta variables are completion-only (no nav/docs target), exactly
like `LoopVariables`.

## 4. File / Package Layout

```
blueprint/BlueprintNamespace.kt   (add NAVIGATION + fromPath clause)
scope/AntlersScope.kt             (BlueprintScope gains navMeta)
scope/AntlersScopeResolver.kt     (nav namespace resolution, navMeta frame, recursive children)
scope/NavVariables.kt             (new — curated nav-tree variables)
completion/AntlersCompletionProvider.kt (offer NavVariables when an enclosing scope is navMeta)
```

No `plugin.xml`, grammar, or lexer changes. `AntlersFieldContext`, references, and docs are untouched.

## 5. Testing

`BasePlatformTestCase`. Fixtures: `resources/blueprints/navigation/main.yaml` (field `link_text`),
`resources/blueprints/collections/blog/blog.yaml` (field `title`), plus the page/collection setup so
segment 0 resolves where needed.

- **Namespace derivation** (`BlueprintScannerTest`/`BlueprintNamespaceTest` additions): a field in
  `navigation/main.yaml` → `NAVIGATION "main"`; `fromPath` unit case for the navigation path.
- **Nav scope resolution** (`AntlersScopeResolverTest` additions): inside `{{ nav:main }}‹›{{ /nav }}` →
  `[BlueprintScope(NAVIGATION "main", navMeta=true)]`; `{{ nav:collection:blog }}` → `[COLLECTION "blog",
  navMeta=true]`; `{{ nav handle="main" }}` → `NAVIGATION "main"`; bare `{{ nav }}` → `COLLECTION
  "pages"`; `{{ nav:main }}{{ children }}‹›{{ /children }}{{ /nav }}` → the nav scope again (recursive).
- **Nav completion** (`AntlersNavScopeTest`): inside `{{ nav:main }}` completion includes `link_text`
  (Field), `depth`/`is_current`/`children` (Nav), `index` (Loop); inside `{{ nav:collection:blog }}`
  includes blog's `title` + nav-meta; a plain `{{ collection:blog }}` loop offers loop-vars but **not**
  nav-meta.
- **Nav field flow** (`AntlersNavScopeTest`): go-to-def on `{{ nav:collection:blog }}{{ ti‹›tle }}`
  resolves to blog's blueprint (fields flow through the nav scope unchanged).
- **Regression**: E1/E2/E3a/E3b scope/completion/nav/docs tests stay green — `navMeta` defaults false,
  only `nav`-headed tags change behavior.

## 6. Out of Scope (follow-ups)

- Per-item collection-field resolution when a navigation mixes multiple collections / manual links.
- `parent` as its own re-scope (offered as a variable, not a re-entry).
- `nav:breadcrumbs` meta; deeper augmentation (J3); structure-tag aliases (none exist in Statamic 6).
- Nav-meta go-to-def/docs (completion-only, like `LoopVariables`).

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Nav blueprint path layout differs (dir vs file) | `fromPath` file-based per Statamic convention; a differing project yields no fields → nav-meta + loop vars still offered (tolerant) |
| Navigation mixes collections / manual links (real ambiguity) | Offer the navigation's own link blueprint (deterministic) + meta; per-item collection fields are a documented follow-up |
| `navMeta` plumbing widens the scope surface | One boolean, defaults false; `AntlersFieldContext` ignores it; only completion reads it |
| Recursive `children` over-scopes if unbalanced | Same tolerant stack behavior as any pair — unmatched `{{ children }}` stays open, never crashes |
| Over-offering nav-meta in non-nav loops | Gated strictly on `scope.navMeta`; collection/taxonomy/container scopes never set it |
| `nav:collection:<h>` 3-segment parse | Uses the E3b `namePath.segments` (full ordered list) |
| Unresolved nav handle | Active nav frame with `UNKNOWN` namespace + `navMeta=true` — meta/loop vars offered, no fields |
