# Antlers Deeper Augmentation J3 — Relationship Traversal — Design

**Date:** 2026-06-01
**Status:** Approved
**Scope:** Sub-project **J3** of "Antlers completeness" (closes J). Dotting into a relationship field
(`entries`/`terms`/`users`/`assets`) reaches the **linked** blueprint's own fields, not just the curated
augmentation properties — `{{ author.full_name }}` where `author` links collection `team`. Multi-hop
chains and bracket/index access fall out of the same generalization.

## 1. Background & Goal

E3b made `{{ field.member }}` resolve to a container's sub-field or a curated fieldtype augmentation
property (`asset.url`, `entry.title`). It stops at relationship fields: for an `entries` field it offers
only the generic entry props, never the linked collection's custom fields.

J3 captures each relationship field's link target (`collections:`/`taxonomy:`/`container:`/`users`) and
generalizes the member resolver so a member is looked up across the **linked** namespace(s). Multi-hop
(`author.avatar.url`) and bracket access (`author[0].title`) need no extra machinery — the resolver
walks namespaces hop by hop, and `namePath.segments` already skips bracket nodes.

## 2. Principles

1. **Reuse E3b.** One helper (`childNamespace` → `childNamespaces`) and its three callers change; the
   classifier, augmentation catalog, and scope stack are untouched.
2. **Linked fields + curated props together.** Relationship members = the linked blueprint's fields plus
   `FieldtypeProperties.forType(type)`, deduped (a real field shadows a same-named prop).
3. **Deterministic from config.** Link targets come from the blueprint YAML (`collections:` etc.); no
   guessing. An uncaptured link degrades to E3b (curated props only), never a wrong target.
4. **Tolerant.** The scanner never throws; bounded look-ahead; unparsed config yields no link.

## 3. Components

### 3.1 Link target on the field model (`blueprint/BlueprintField.kt`)

```kotlin
data class BlueprintField(
    val handle: String,
    val display: String,
    val type: String,
    val file: VirtualFile,
    val offset: Int,
    val namespace: BlueprintNamespace,
    val linkedNamespaces: List<BlueprintNamespace> = emptyList()  // relationship target(s)
)
```

`linkedNamespaces` defaults empty, so every existing construction stays valid.

### 3.2 Link capture in the scanner (`blueprint/BlueprintScanner.kt`)

Inside each field's existing look-ahead window (lines between this `handle:` and the next), capture the
relationship config and map it to namespaces by the field's resolved `type`:
- `type: entries` + `collections:` → one `COLLECTION <h>` per listed handle. Support the block-list form
  (`collections:` then `- <h>` items) and the inline form (`collections: [a, b]`).
- `type: terms` + `taxonomy: <h>` (single scalar) → `[TAXONOMY <h>]`.
- `type: assets` + `container: <h>` (single scalar) → `[ASSET <h>]`.
- `type: users` / `user` → `[USER user]` (no config key).

Regexes: `COLLECTIONS_KEY = ^\s*collections:\s*(\[[^\]]*\])?\s*$` (inline list captured in group 1; empty
→ block list follows), a block list-item `^\s*-\s*['"]?([A-Za-z0-9_-]+)['"]?\s*$`, and single-scalar
`taxonomy:`/`container:` keys `^\s*<key>:\s*['"]?([A-Za-z0-9_-]+)['"]?\s*$`. Best-effort and tolerant; a
link list extending past the look-ahead window is the documented edge. The captured handles + the field's
type produce `linkedNamespaces`, set on the emitted `BlueprintField`.

Import-grafted fields (E3a `expandImports`) carry their `linkedNamespaces` through the `copy(...)` graft
unchanged (only `namespace` is re-stamped).

### 3.3 Member resolution generalization (`scope/AntlersMemberResolver.kt`)

Replace the single `childNamespace` with a list-valued `childNamespaces`:

```kotlin
private val CONTAINER_TYPES = setOf("grid", "group", "replicator", "bard")

/** The namespace(s) whose fields are [field]'s members: container sub-fields OR linked blueprint(s). */
fun childNamespaces(field: BlueprintField): List<BlueprintNamespace> = when {
    field.type.lowercase() in CONTAINER_TYPES -> listOf(field.namespace.copy(path = field.namespace.path + field.handle))
    field.linkedNamespaces.isNotEmpty() -> field.linkedNamespaces
    else -> emptyList()
}

fun resolveField(element: PsiElement, pathPrefix: List<String>, project: Project): BlueprintField? {
    if (pathPrefix.isEmpty()) return null
    var current = AntlersFieldContext.resolveField(element, pathPrefix[0], project) ?: return null
    val svc = BlueprintService.getInstance(project)
    for (i in 1 until pathPrefix.size) {
        current = childNamespaces(current)
            .firstNotNullOfOrNull { ns -> svc.fieldsFor(ns).firstOrNull { it.handle == pathPrefix[i] } }
            ?: return null
    }
    return current
}
```

`CONTAINER_TYPES` is duplicated from `AntlersScopeResolver` (or extracted to a shared constant — the
plan picks one). Multi-hop is automatic: each segment consults the previous field's `childNamespaces`.

### 3.4 Consumer wiring (completion, references, docs)

All three already route member lookups through `AntlersMemberResolver`. Each changes from the single
`childNamespace` to iterating `childNamespaces`:
- **Completion** `offerMembers(field)`: offer `BlueprintService.fieldsFor(ns)` over **all**
  `childNamespaces(field)` (deduped by handle, typeText "Field"), then `FieldtypeProperties.forType(field.type)`
  (typeText "Property", deduped against the fields). No tags/system-vars (member space, unchanged).
- **References** (`AntlersDefinitionReferenceHelper`): the non-head member branch resolves the segment by
  searching `childNamespaces(parent)` for a field with this handle; if found, attach the existing soft
  `AntlersBlueprintMemberReference(element, thatNamespace, handle)` pointing at its declaration.
- **Docs** (`AntlersDocumentationProvider`): the non-head branch searches `childNamespaces(parent)` for a
  blueprint sub-field (field doc with the namespace-path label), else a `FieldtypeProperties` property doc,
  else the catalog tag-method fallback.

### 3.5 Bracket/index access

No new code. `{{ field[0].member }}` parses with the `[0]` in a `bracketAccess` node; `namePath.segments`
(direct `T_IDENT` children) already excludes it, so the segments are `[field, member]` — resolved exactly
like `{{ field.member }}`. Covered by tests only.

## 4. File / Package Layout

```
blueprint/BlueprintField.kt        (add linkedNamespaces)
blueprint/BlueprintScanner.kt      (capture collections:/taxonomy:/container:/users → linkedNamespaces)
scope/AntlersMemberResolver.kt     (childNamespace → childNamespaces; resolveField walks them)
completion/AntlersCompletionProvider.kt (offerMembers over childNamespaces)
references/AntlersDefinitionReferenceHelper.kt (member ref over childNamespaces)
documentation/AntlersDocumentationProvider.kt (member doc over childNamespaces)
```

No `plugin.xml`, grammar, or lexer changes.

## 5. Testing

`BasePlatformTestCase`. Fixtures: collection `blog` with `author` (entries, `collections: [team]`),
`topics` (terms, `taxonomy: tags`), `hero` (assets, `container: images`); collection `team` (field
`full_name` + `avatar` assets `container: images`); taxonomy `tags` (field `tag_color`); asset blueprint
`resources/blueprints/assets/images.yaml` (field `caption`); the page/collection setup so segment 0
resolves.

- **Link capture** (`BlueprintScannerTest`): `author.linkedNamespaces` == `[COLLECTION "team"]`; `topics`
  == `[TAXONOMY "tags"]`; `hero` == `[ASSET "images"]`; a `users` field == `[USER "user"]`; inline/block
  `collections: [team, blog]` captures both.
- **childNamespaces** (`AntlersMemberResolverTest`): container field → path-extended; relationship field
  → its `linkedNamespaces`; plain text field → empty.
- **Relationship completion** (`AntlersRelationCompletionTest`): `{{ author.‹caret› }}` offers `full_name`
  (Field) and `id`/`url` (Property), excludes a blog sibling; `{{ topics.‹caret› }}` offers `tag_color`;
  `{{ hero.‹caret› }}` offers `caption` and `url`/`alt`.
- **Multi-hop**: `{{ author.avatar.‹caret› }}` offers `url` (asset prop via team→avatar).
- **Bracket access**: `{{ author[0].‹caret› }}` offers `full_name`; `{{ author[0].full_name }}` go-to-def
  resolves to team's blueprint.
- **Relationship nav/docs**: go-to-def on `{{ author.full‹›_name }}` resolves to team's `full_name`; hover
  names the `team` collection.
- **Regression**: E3b member tests (group sub-fields, plain augmentation) stay green.

## 6. Out of Scope (follow-ups)

- Dynamic/bound link handles (`collections: [{{ x }}]`).
- Per-entry blueprint selection within a collection that has several entry blueprints.
- `bard`/`replicator` set-specific relationship fields; nav-link entry fields (J2 follow-up).
- Link lists longer than the scanner's look-ahead window.

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Link list past the look-ahead window | Documented edge; window covers typical configs; tolerant (no link → curated props, never wrong) |
| Inline `collections: [a,b]` vs block list | Scanner handles both; unrecognized form → no link, never crash |
| Multi-collection union offers cross-collection fields | Deduped union — matches "field may point at several collections" (same as E1 multi-blueprint) |
| `childNamespaces` plural ripples to 3 consumers | All route through `AntlersMemberResolver`; change is list-vs-single in one helper + callers |
| Deep/recursive relationship chains | `resolveField` walks only the finite typed prefix; completion offers one level — no cycle |
| Asset container blueprint path | `resources/blueprints/assets/<container>.yaml` → `ASSET` namespace already exists (E1) |
| Curated props mixing with linked fields | Deduped by name; a real blueprint field shadows a same-named curated prop |
| `CONTAINER_TYPES` duplicated in two files | Extract to one shared constant (plan decides) to avoid drift |
