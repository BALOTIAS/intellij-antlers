# Antlers Blueprint Scoping E3a — Nested-Field Tree + Container Scope — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Sub-project **E3a** of "precise blueprint scoping". Make the blueprint scanner **tree-aware**
(parent → sub-fields, with fieldset `import:` inlining) and teach the scope resolver that a
**container-typed blueprint field** used as a pair tag — `{{ rows }}…{{ /rows }}` (Grid / Group /
Replicator / Bard) — opens a **sub-field scope**. E3b (dotted/colon single-statement access
`{{ group.sub }}`, augmentation properties) is a separate follow-up and out of scope here.

## 1. Background & Goal

E1 added loop/tag scope + per-field `BlueprintNamespace`; E2 added page-blueprint mapping; both consume
fields through `AntlersFieldContext` (precedence loop → page → global). The blueprint scanner is still
**flat and line-based**: it extracts every `handle:` line regardless of nesting, so a Grid's sub-field
`caption` is wrongly surfaced as a top-level page variable (a latent **flatten-bug**), and nothing knows
that `{{ rows }}…{{ /rows }}` exposes the Grid's sub-fields.

E3a fixes this. It rebuilds the scanner as an indentation-aware **tree** (each field carries the path of
container handles it lives under), inlines fieldset `import:` references, and extends
`AntlersScopeResolver` so a container-typed field used as a pair tag pushes a scope of its sub-fields.
Because completion / references / docs already consume `BlueprintScope`s, sub-field completion, nav, and
docs inside a container work the moment the resolver emits the scope — no consumer rewrites.

It stays **platform-only** and needs **no grammar/lexer changes**.

## 2. Principles

1. **Reuse E1/E2.** Container scope is a new source of `BlueprintScope`; the resolver + `AntlersFieldContext`
   + all consumers are otherwise unchanged.
2. **Tree without API churn.** The container chain lives entirely in `BlueprintNamespace.path`; a field's
   namespace *is* its location in the tree, so `fieldsFor` (which matches on namespace equality) needs no
   new logic and `BlueprintField` is unchanged from E1. The scanner output stays `List<BlueprintField>`.
3. **Tolerant.** The tree scanner and import resolver never throw; malformed YAML, odd indentation,
   missing/cyclic imports degrade to fewer/mis-placed fields, never a crash.
4. **No recursion in scope resolution.** The resolver resolves container fields directly against
   `BlueprintService`/`PageBlueprintResolver` using its partial stack — never back through
   `AntlersFieldContext` (which calls the resolver).
5. **Pair-tag form only.** E3a scopes only the `{{ container }}…{{ /container }}` form. Single-statement
   `{{ group:sub }}`/`{{ group.sub }}` access is E3b.

## 3. Components

### 3.1 Path-bearing namespace model (`blueprint/BlueprintNamespace.kt`)

Only `BlueprintNamespace` changes — it gains a `path` naming the container chain. `BlueprintField` is
**unchanged from E1** (its tree location is carried by its `namespace`).

```kotlin
data class BlueprintNamespace(val kind: Kind, val handle: String, val path: List<String> = emptyList()) {
    enum class Kind { COLLECTION, TAXONOMY, USER, FORM, ASSET, GLOBAL, FIELDSET, UNKNOWN }
}
```

`path` defaults to empty, so every existing construction stays valid (top-level fields have an empty
path). `BlueprintNamespace.fromPath` is unchanged (it never sets `path`); the scanner supplies the path
via `.copy(path = …)` per field (§3.2). A field's namespace `COLLECTION("blog", ["rows"])` means "a
sub-field of the `rows` container in collection `blog`".

### 3.2 Tree-aware scanner (`blueprint/BlueprintScanner.kt`, rewrite)

Replace the flat line extractor with an indentation-stack tree builder. Still hand-rolled, tolerant,
never throws, still records the handle-**value** offset.

Per file, `extract` computes the file's base namespace once (`BlueprintNamespace.fromPath(file.path)`)
and walks the lines tracking an indent stack of `(indent, handle)`:
- Leading-whitespace count of a line = its `indent` (the `- ` list marker counts toward indent).
- On a `handle:` match at indent *I*: pop the stack while `top.indent >= I`; the remaining stack's
  handles (in order) are this field's container **path**; the field's namespace is
  `base.copy(path = thoseHandles)`; then push `(I, handle)`.
- `display`/`type` are read from the following more-indented lines, stopping at the next `handle:` or a
  line dedented to/below the handle (as today, bounded look-ahead).
- **Container nesting:** Grid/Group nest sub-fields under a `fields:` key; Replicator/Bard nest under
  `sets: › <set> › fields:`. The indent-stack approach captures both uniformly because sub-field
  `handle:` lines are simply more-indented; for Replicator/Bard the sub-fields of all sets share the
  container's path (the **union across sets** — a row's set is unknown), which the indent stack yields
  naturally since every set's `fields:` sits under the same container handle.

Output: `List<BlueprintField>` whose namespaces carry the container path. (Import expansion: §3.3.)

### 3.3 Fieldset import inlining (`blueprint/BlueprintScanner.kt`)

`import: <fieldset>` lines compose a reusable fieldset's fields at that point. Two phases inside
`scan(project)`:

1. **Parse** every blueprint and fieldset file (§3.2) into path-bearing fields, and record **import
   markers** `(namespace, path, importedHandle)` for each `import:` line. Fieldsets land in the
   `FIELDSET` namespace via the existing `fromPath`.
2. **Splice.** For each import marker importing fieldset *F* at `(ns, path)`: resolve *F*'s fields
   (recursively expanding *F*'s own imports first, guarded by a set of in-progress fieldset handles),
   then **graft** each of *F*'s fields as a new `BlueprintField` whose namespace is
   `ns.copy(path = path + Ffield.namespace.path)` (the import site's path prepended to the field's path
   within *F*); the grafted field keeps *F*'s `file`/`offset` (so go-to-def lands on the fieldset
   declaration). A missing/cyclic import is skipped (tolerant).

The cached `scan` result already has imports inlined; `BlueprintService`/`fieldsFor` stay import-unaware.
A fieldset `prefix:` is **ignored in v1** (fields graft without the prefix — documented limitation).

### 3.4 Field lookup by path (`blueprint/BlueprintService.kt`)

`fieldsFor(ns)` now matches kind + handle + **path**:

```kotlin
fun fieldsFor(ns: BlueprintNamespace): List<BlueprintField> =
    scanned().filter { it.namespace == ns }.distinctBy { it.handle }
```

Since `BlueprintNamespace` is a data class and now includes `path`, equality already covers the path —
no code change beyond the model. Top-level (`path=[]`) returns only top-level fields, **fixing the
flatten-bug**. `field(handle)` (global, path-agnostic) is unchanged.

### 3.5 Container scope in the resolver (`scope/AntlersScopeResolver.kt`)

Add `CONTAINER_TYPES = setOf("grid", "group", "replicator", "bard")`. In the opener branch, after the
catalog-iterating-tag and alias checks and **before** the "catalog pair tag → transparent frame"
fallback, attempt container resolution:

1. Compute the **current namespaces** at this walk position: the active frames' namespaces (innermost
   first); or, if no active frame is open, `PageBlueprintResolver.namespacesFor(stmt)`.
2. Resolve the head: `BlueprintService.getInstance(project).fieldsFor(ns).firstOrNull { it.handle == head }`
   over those namespaces (first match wins).
3. If that field's `type` (lowercased) ∈ `CONTAINER_TYPES`, push an **active** frame whose namespace is
   `field.namespace.copy(path = field.namespace.path + head)`. Honor `as="alias"` via the existing alias
   mechanism (a non-null `as` value pushes the frame aliased, exactly as for iterating tags).
4. Else fall through to today's behavior.

**No recursion:** the resolver uses `BlueprintService` + `PageBlueprintResolver` directly with the stack
it has built so far — never `AntlersFieldContext`. Dependency direction stays acyclic
(`AntlersScopeResolver` → `BlueprintService`/`PageBlueprintResolver`; `AntlersFieldContext` →
`AntlersScopeResolver`).

Nesting/cascade fall out for free: an inner container resolves against the outer container's sub-field
namespace, pushing `path = [outer, inner]`; innermost-first ordering + dedup already live in
`AntlersFieldContext.fieldsInScope`.

### 3.6 Consumer touch-ups

- **Docs** (`documentation/AntlersDocumentationProvider.namespaceLabel`): include the namespace `path` so
  a sub-field hover reads e.g. *Field `caption` (text) … · collection: blog › rows* instead of dropping
  the container. When `path` is empty it renders exactly as today.
- **Completion loop-meta gate** (`completion/AntlersCompletionProvider`): unchanged — stays
  `AntlersScopeResolver.scopesAt(...).isNotEmpty()`. Loop-meta vars are therefore now offered inside
  Grid/Replicator/Bard (correct — they iterate). Inside a `group` (a single array, not a loop) this
  over-offers `index`/etc.; **accepted v1 imprecision**, documented (fixing it needs an `iterating` flag
  on the scope — deferred).
- Completion/references are otherwise **untouched** — they already consume `BlueprintScope`s via
  `AntlersFieldContext`, so sub-field completion and nav inside a container work automatically.

## 4. File / Package Layout

```
blueprint/BlueprintNamespace.kt      (add path)
blueprint/BlueprintScanner.kt        (rewrite: indentation tree, sets namespace.path per field + import inlining)
scope/AntlersScopeResolver.kt        (container-field scope detection)
documentation/AntlersDocumentationProvider.kt (namespaceLabel renders path)
```
(`BlueprintField.kt` and `BlueprintService.kt` are unchanged — the path lives in the namespace, which
`fieldsFor`'s data-class equality already covers.)

No `plugin.xml` changes. No grammar/lexer changes. No new consumer wiring.

## 5. Testing

`BasePlatformTestCase` (runs via `./gradlew test`). Fixtures: a collection blueprint with a top-level
text field, a Grid `rows` (sub-field `caption`), a nested Grid `gallery` inside `rows`, a Group, a
Replicator with two sets, and a fieldset `seo` (field `meta_title`) imported by the blueprint.

- **Tree scanner** (`BlueprintScannerTest` additions): `caption`'s namespace is
  `COLLECTION("blog", ["rows"])`; the top-level field's namespace path is `[]`; a field nested in
  `rows › gallery` has namespace path `["rows","gallery"]`; Replicator/Bard sub-fields from both sets
  share the container path (union).
- **Flatten-bug fix** (regression): `BlueprintService.fieldsFor(COLLECTION "blog", [])` excludes
  `caption`; a top-level `{{ <caret> }}` completion omits `caption`.
- **Import inlining** (`BlueprintScannerTest`/new): the blueprint importing `seo` exposes `meta_title`
  at the import's path; go-to-def on `meta_title` lands in `seo.yaml`; a cyclic import doesn't hang and
  yields a finite list.
- **Container scope** (`AntlersContainerScopeTest`): inside `{{ rows }}{{ <caret> }}{{ /rows }}`
  completion offers `caption` + loop-meta (`index`) and **excludes** the sibling top-level field; nav on
  `{{ cap<caret>tion }}` resolves to the sub-field's blueprint line; nested
  `{{ rows }}{{ gallery }}…{{ <caret> }}…{{ /gallery }}{{ /rows }}` scopes to the deeper sub-fields; a
  page Grid (resolved via E2 page mapping) opens its scope at top level.
- **Regression**: all E1/E2 scope, page, and variable tests stay green — flat blueprints put every field
  at `path=[]`, so `fieldsFor`/completion/nav are unchanged for them.

## 6. Out of Scope (E3b / follow-ups)

- Dotted/colon single-statement access: `{{ group:sub }}`, `{{ group.sub }}`, `{{ author.name }}` (E3b).
- Fieldtype augmentation properties not in the blueprint (`asset.url`, `user.email`) — a curated catalog (E3b).
- Fieldset `import:` `prefix:` handling.
- Per-set Replicator/Bard narrowing (v1 unions all sets).
- Distinguishing iterating containers (Grid/Replicator/Bard) from non-iterating `group` for the loop-var
  gate.

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Indentation-stack mis-parses exotic YAML (tabs, irregular nesting) | Best-effort + tolerant; never throws; worst case a field lands at the wrong path (mis-scoped), never a crash. Flat blueprints (the common case + all existing tests) are unaffected |
| Resolver does field lookups per non-catalog opening tag (perf) | `fieldsFor` is cached; lookups are small list scans; only for heads that aren't catalog tags; same cost class as the existing walk |
| Recursion (`scopesAt` ↔ `AntlersFieldContext`) | Resolver calls `BlueprintService`/`PageBlueprintResolver` directly using its partial stack — never `AntlersFieldContext`; dependency direction stays acyclic |
| Replicator/Bard set-union offers fields from the wrong set | Documented; union matches "can't know the row's set" (same as E1's multi-blueprint union) |
| `group` offers loop-meta vars | Accepted v1 imprecision; documented; needs an `iterating` flag (deferred) |
| Import `prefix:` ignored | Documented limitation; prefixed fields graft without the prefix in v1 |
| Cyclic / missing fieldset import | Cycle guard (in-progress handle set) cuts recursion; missing import skipped |
| Scanner rewrite regresses flat extraction (offsets, display/type) | Keep the existing handle/display/type regexes and offset capture; only the parent-path tracking + import phase are new; the E1/E2 scanner tests guard the flat behavior |
