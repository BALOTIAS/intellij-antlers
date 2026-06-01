# Antlers Blueprint Scoping E1 — Loop/Tag Scope + Namespace Foundation — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Sub-project **E1** of "precise blueprint scoping" — make `{{ variable }}` completion,
go-to-definition, and docs aware of the **enclosing iterating-tag scope**, and give every blueprint
field a **namespace** derived from its file path. E2 (template→page-blueprint mapping) and E3 (nested
fieldsets + dotted access) build on this foundation and are out of scope here.

## 1. Background & Goal

The blueprint-variables v1 feature offers **all** blueprint field handles everywhere in `{{ }}`,
flattened into one global list. That is imprecise: inside `{{ collection:blog }}…{{ /collection }}`
the variables in play are *blog entry* fields plus loop-meta variables, not the union of every
blueprint in the project.

E1 adds **scope awareness**. It derives a namespace for each field from its blueprint path, tracks the
iterating-tag scope around the caret, and narrows completion / nav / docs to that scope inside
recognized loops — while preserving today's "offer everything" behavior at the top level (page-level
narrowing is E2). It stays **platform-only** and requires **no grammar or lexer changes**: the PSI
already exposes closing tags, the namePath head/method, and `as="…"` parameters.

This is the shared foundation for E2 and E3: the namespaced blueprint index and the scope model.

## 2. Principles

1. **Platform-only, no grammar changes.** Reuse the existing PSI (`AntlersClosingTag`,
   `AntlersNamePathMixin.head`/`method`, `AntlersParameterMixin`).
2. **Additive to v1.** The flat `BlueprintService.fields()` and the global fallback stay; scoping is a
   new path layered on top. The v1 feature keeps working unchanged.
3. **Precise when certain, broad when not.** Restriction kicks in only for a *recognized* iterating tag
   with a *resolved* handle and an *existing* namespace. Any uncertainty falls back to the global list
   rather than hiding fields.
4. **Tolerant.** The scope resolver never throws; malformed/unbalanced templates yield an over-broad or
   empty scope, never a crash.
5. **One front door.** All three consumers branch on a single shared helper, not duplicated logic.

## 3. Components

### 3.1 Blueprint namespace (`blueprint/BlueprintNamespace.kt`)

```kotlin
data class BlueprintNamespace(val kind: Kind, val handle: String) {
    enum class Kind { COLLECTION, TAXONOMY, USER, FORM, ASSET, GLOBAL, FIELDSET, UNKNOWN }
}
```

Derived from a blueprint/fieldset file path during scanning:

| Path | Namespace |
|------|-----------|
| `…/resources/blueprints/collections/<h>/<file>.yaml` | `COLLECTION <h>` |
| `…/resources/blueprints/taxonomies/<h>/<file>.yaml` | `TAXONOMY <h>` |
| `…/resources/blueprints/forms/<h>.yaml` | `FORM <h>` |
| `…/resources/blueprints/assets/<h>.yaml` | `ASSET <h>` |
| `…/resources/blueprints/globals/<h>.yaml` | `GLOBAL <h>` |
| `…/resources/blueprints/user.yaml` | `USER user` |
| `…/resources/fieldsets/<h>.yaml` | `FIELDSET <h>` |
| anything else under blueprints | `UNKNOWN ""` |

A small pure function `BlueprintNamespace.fromPath(path: String): BlueprintNamespace`. Tolerant — an
unrecognized layout yields `UNKNOWN`.

### 3.2 Field model + service (`blueprint/BlueprintField.kt`, `BlueprintScanner.kt`, `BlueprintService.kt`)

- `BlueprintField` gains `val namespace: BlueprintNamespace`. The scanner sets it via
  `BlueprintNamespace.fromPath(file.path)` for every field it extracts.
- `BlueprintService` keeps `fields(): List<BlueprintField>` (flat, deduped by handle — unchanged, used
  by the global fallback) and adds:
  - `fieldsFor(ns: BlueprintNamespace): List<BlueprintField>` — all fields whose `namespace == ns`,
    union across the namespace's blueprints, deduped by handle (first wins). Cached off the same
    `PsiModificationTracker.MODIFICATION_COUNT` value as `fields()`.

### 3.3 Scope model + resolver (`scope/AntlersScope.kt`, `scope/AntlersScopeResolver.kt`)

```kotlin
sealed interface AntlersScope
data class BlueprintScope(val namespace: BlueprintNamespace) : AntlersScope
```

`AntlersScopeResolver` (object) — `scopesAt(caret: PsiElement): List<AntlersScope>` (innermost first).

Algorithm (the grammar is structural/flat — openers and closers are sibling top-level statements, the
same shape `AntlersFoldingBuilder` already walks with a stack):

1. Resolve the containing file and the caret offset.
2. Iterate top-level `AntlersStatement`s in document order whose **text range ends before** the caret
   offset. (The statement that *contains* the caret is the completion position, owned by the existing
   classifier — it is not an enclosing scope.)
3. Maintain a stack of **frames**. A frame records: `name` (tag head or alias used to match the
   closer), an optional `namespace`, an `active` flag (namespace contributes to the body), and an
   optional `alias` (namespace activates only inside a child tag of that name).
   - **Opening statement** (body is a `namePath`, not an `AntlersClosingTag`):
     - head matches an enclosing **aliased** frame's `alias` → push a frame `{name=head,
       namespace=that frame's namespace, active=true}`.
     - else head ∈ {`if`,`unless`,`elseif`,`else`} → push a **transparent** frame
       `{name=head, namespace=null}`.
     - else head is a **recognized iterating tag** (`collection`,`taxonomy`,`users`/`user`,`form`,
       `assets`) with a **resolvable handle** mapping to a non-`UNKNOWN` namespace:
       - if it has an `as="<alias>"` parameter → push `{name=head, namespace=ns, active=false,
         alias=<alias>}` (fields appear only inside `{{ <alias> }}`).
       - else → push `{name=head, namespace=ns, active=true}` (fields appear in the body).
     - else (any other pair tag — `cache`, `section`, custom, or an iterating tag with no resolvable
       handle / `UNKNOWN` namespace) → push a transparent frame `{name=head, namespace=null}`.
   - **Closing statement** (body is `AntlersClosingTag`): pop the nearest frame whose `name` equals the
     closer's `closedName` (or the nearest frame if the closer has no name). Unmatched closer → ignore.
4. Return the `BlueprintScope(namespace)` of every frame on the stack with `active=true &&
   namespace!=null`, innermost first.

A non-empty result means the caret is inside ≥1 active iterating frame, which is exactly the condition
for offering loop-meta variables — consumers key off `fieldsInScope != null` (§3.5), no separate flag.

**Handle resolution** per recognized tag (`AntlersScopeResolver` private helper):
- `collection`/`taxonomy`/`form`/`asset`: namePath `method` (e.g. `collection:blog` → `blog`); else the
  `from=` or `in=` parameter value with surrounding quotes stripped; else unresolved.
- `users`/`user`: singleton → always `USER "user"`, no handle needed.
- An unresolved handle yields a transparent frame (no scoping guess).

### 3.4 Loop-meta variables (`scope/LoopVariables.kt`)

Curated list offered inside any active iterating scope: `index`, `count`, `total_results`, `first`,
`last`, `no_results` — each `LoopVariable(name, description)`, e.g. `index` → "1-based position in the
loop". (Nav-specific meta like `depth`/`children` is deferred with nav scoping.)

### 3.5 Shared scope-fields helper (`scope/AntlersScopeFields.kt`)

```kotlin
object AntlersScopeFields {
    /**
     * null     → no iterating scope encloses the caret: caller uses the global fallback (all fields).
     * non-null → restrict to exactly these scoped fields (innermost-first, deduped); do NOT fall back
     *            to global. May be empty when the scope is known but its blueprint defines no fields.
     */
    fun fieldsInScope(element: PsiElement, project: Project): List<BlueprintField>?
}
```

It calls `scopesAt`. **Empty** scope list → `null` (no iterating scope → global fallback — including
inside conditions, `cache`/`section`, or any unrecognized pair tag, honoring "broad when not certain").
**Non-empty** scopes → the union of `fieldsFor(ns)` over the scopes, innermost-first, deduped by handle.
A transparent (non-iterating) tag nested *inside* an iterating tag does not remove the outer scope — the
stack still carries the outer iterating frame, so `{{ collection:blog }}{{ cache }}‹›{{ /cache }}` still
reports `[COLLECTION blog]`.

### 3.6 Consumer wiring

- **Completion** (`completion/AntlersCompletionProvider.kt`, TAG_NAME branch): always offer catalog
  tags. Then `fieldsInScope`:
  - `null` → today's behavior: all `BlueprintService.fields()` + system vars.
  - non-null → exactly those scoped fields (typeText "Field") + loop-meta (typeText "Loop") + system
    vars. (An empty non-null list — scope known, blueprint defines no fields — correctly offers no
    blueprint fields, still tags/loop-meta/system vars.)
  Dedup against catalog tag names and across groups as today (shared `seen` set).
- **References** (`references/AntlersBlueprintFieldReference.kt`): `resolve()` first looks the handle up
  in `scopesAt` namespaces (innermost first) via `BlueprintService.fieldsFor`; only when there are no
  scopes does it fall back to the global `BlueprintService.field(handle)`. Stays soft.
- **Docs** (`documentation/AntlersDocumentationProvider.kt`, head branch): precedence catalog tag →
  scoped field → global field → system var → null. Scoped field doc names its namespace
  (e.g. *Field `title` — Title (text) · collection: blog*).

## 4. File / Package Layout

```
blueprint/BlueprintNamespace.kt        (new)
blueprint/BlueprintField.kt            (add namespace)
blueprint/BlueprintScanner.kt          (set namespace from path)
blueprint/BlueprintService.kt          (add fieldsFor)
scope/AntlersScope.kt                  (new — sealed interface + BlueprintScope)
scope/AntlersScopeResolver.kt          (new — stack reconstruction)
scope/LoopVariables.kt                 (new — curated loop-meta)
scope/AntlersScopeFields.kt            (new — shared null/empty/non-empty helper)
completion/AntlersCompletionProvider.kt (wire TAG_NAME field branch to helper)
references/AntlersBlueprintFieldReference.kt (scope-first resolve)
documentation/AntlersDocumentationProvider.kt (scope-first field docs)
```

No `plugin.xml` changes (no new EPs; `BlueprintService` stays a `@Service`).

## 5. Testing

`BasePlatformTestCase` (runs via `./gradlew test`). Fixtures under
`src/test/testData/blueprints/collections/blog/`, `…/taxonomies/tags/`, plus `user.yaml`,
`forms/contact.yaml`, and a second collection `news/` whose blueprint also defines `title`.

- **Namespace derivation** (`BlueprintScannerTest` additions): field in `collections/blog/article.yaml`
  → `COLLECTION "blog"`; `user.yaml` → `USER "user"`; `fieldsets/seo.yaml` → `FIELDSET "seo"`;
  `BlueprintNamespace.fromPath` unit cases for each layout incl. `UNKNOWN`.
- **Scope resolver** (`AntlersScopeResolverTest`): caret inside `{{ collection:blog }}‹›{{ /collection }}`
  → `[COLLECTION blog]`; nested `{{ taxonomy:tags }}` inside it → `[TAXONOMY tags, COLLECTION blog]`;
  `{{ collection:blog as="entries" }}` → `[]` in the collection body, `[COLLECTION blog]` only inside
  `{{ entries }}‹›{{ /entries }}`; top-level caret → `[]`; inside top-level `{{ if }}` → `[]`
  (transparent); inside a top-level unrecognized pair tag → `[]` (→ global fallback);
  `{{ collection:blog }}{{ cache }}‹›{{ /cache }}{{ /collection }}` → `[COLLECTION blog]` (outer scope
  preserved through a transparent inner tag); `collection from="blog"` form resolves the handle.
- **Scoped completion** (`AntlersScopedCompletionTest`): inside `{{ collection:blog }}` the lookup
  includes `hero_title` (blog Field) + `index` (Loop) + `title` (system Variable) and **excludes** a
  taxonomy-only field; top-level completion still includes fields from every namespace.
- **Scoped nav** (`AntlersVariableNavTest` additions): `{{ ti‹›tle }}` inside `{{ collection:blog }}`
  resolves to `blog`'s `title` blueprint when both `blog` and `news` define `title`; top-level `title`
  still resolves (global fallback).
- **Scoped docs** (`AntlersVariableNavTest`/docs additions): hover `title` inside `{{ collection:blog }}`
  mentions the `blog` collection namespace.

## 6. Out of Scope (E2 / E3 follow-ups)

- **E2:** template → page-blueprint mapping (parse `content/collections/*.yaml` `template:` keys; narrow
  top-level `{{ }}` to the current file's page blueprint).
- **E3:** nested fieldset sub-fields (Bard / Replicator / Grid) and dotted access (`{{ author.name }}`).
- `nav` / `structure` scoping (handle → collection indirection).
- The `as` alias array's own meta variables; `scope`/`as` on non-iterating tags.

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Unbalanced/malformed templates corrupt the stack | Resolver never throws; unmatched closers ignored, unmatched openers stay open — worst case over-broad scope, never a crash |
| Restriction hides a real field (mapping wrong) | Restriction only for a recognized iterating tag with a resolved handle and an existing namespace; any uncertainty → global fallback (`null`), never a silent empty |
| Linear rescan per keystroke | Top-level statement scan only; small files; same cost class as the shipped folding builder; optimize later if measured |
| Unhandled `collection` handle forms | Cover `:handle`, `from=`, `in=`; anything else → unresolved → global fallback, not wrong scoping |
| Multiple entry blueprints per collection | Union deduped by handle — documented; matches "can't know which entry" reality |
| Two consumers drift on the null/empty/global semantics | Single `AntlersScopeFields.fieldsInScope` helper encodes the three-way decision; consumers never re-derive it |
