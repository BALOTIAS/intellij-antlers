# Antlers Blueprint Scoping E3b — Dotted/Colon Member Access — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Sub-project **E3b** of "precise blueprint scoping". Support **member access** after a `.` or
`:` — `{{ hero.headline }}`, `{{ group:sub }}`, `{{ hero_image.url }}` — by resolving the path so far to
a field and offering (a) its blueprint sub-fields (reusing the E3a tree) and (b) curated fieldtype
**augmentation properties** (`asset.url`, `author.email`, …). Completion, go-to-definition, and docs.
Closes the precise-scoping series (E1→E2→E3a→E3b).

## 1. Background & Goal

E3a made the scanner tree-aware and opened sub-field **scopes** for container fields used as pair tags
(`{{ rows }}…{{ /rows }}`). It did **not** handle single-statement member access: `{{ group.sub }}` /
`{{ group:sub }}` (a group's sub-field) or `{{ hero_image.url }}` (a fieldtype augmentation property).
Today the completion classifier has no `T_DOT` branch (returns `NONE` after a dot) and `T_COLON` only
offers catalog tag methods, so member access offers nothing for blueprint fields.

E3b adds member access. It resolves the path before the caret to a field, then offers that field's
blueprint sub-fields (deterministic, from the E3a tree) plus its fieldtype's augmentation properties
(from a new curated catalog). Go-to-def jumps to a sub-field's blueprint declaration; augmentation
properties get docs but no nav. It stays **platform-only** with **no grammar/lexer changes** (the PSI
already tokenizes `.`/`:` and exposes namePath segments).

## 2. Principles

1. **Reuse E1/E2/E3a.** Segment 0 resolves through `AntlersFieldContext` (loop/page/global scope);
   deeper segments walk the E3a sub-field tree. Sub-field nav reuses the blueprint declaration offsets.
2. **Deterministic first, curated second.** Blueprint sub-fields are exact (tree-backed); augmentation
   properties are a curated, clearly-labelled ("Property") starter catalog — extensible, never a wrong
   nav target.
3. **Member space, not statement space.** After a `.`/`:`, completion offers only Field/Property
   members — not statement-level tags or system variables.
4. **Tolerant.** Unresolvable paths (numbers, unknown handles) offer nothing; nothing throws.

## 3. Components

### 3.1 Classifier + completion info (`completion/AntlersCompletionContext.kt`)

Add a kind and enrich the info:

```kotlin
enum class AntlersCompletionKind { TAG_NAME, TAG_METHOD, PARAMETER, MODIFIER, FIELD_PATH, NONE }
data class AntlersCompletionInfo(
    val kind: AntlersCompletionKind,
    val tagHead: String? = null,
    val pathPrefix: List<String> = emptyList()
)
```

`pathPrefix` is the namePath's identifier segments **before** the caret segment. Because completion
inserts a dummy identifier at the caret (the partial being typed is always the last segment),
`pathPrefix` = the namePath's ordered `T_IDENT` segment texts with the last one dropped.

Branches:
- `T_DOT` → `AntlersCompletionInfo(FIELD_PATH, pathPrefix = segmentsBeforeCaret(statement))`.
- `T_COLON` → unchanged kind `TAG_METHOD`, but now also carries
  `pathPrefix = segmentsBeforeCaret(statement)` (the head for `group:‹caret›` is `pathPrefix.last()`,
  i.e. the single segment `[group]`). The existing `tagHead` is still set.

`segmentsBeforeCaret(statement)` = the statement's namePath `T_IDENT` segment texts, `dropLast(1)`.
(Returns `[]` when there are 0–1 segments, which can't happen after a `.`/`:`.)

### 3.2 Member resolver (`scope/AntlersMemberResolver.kt`, new)

```kotlin
object AntlersMemberResolver {
    /** The field that [pathPrefix] resolves to at [element], walking the E3a sub-field tree. Null if unresolved. */
    fun resolveField(element: PsiElement, pathPrefix: List<String>, project: Project): BlueprintField?

    /** The sub-namespace whose fields are [field]'s direct children (for offering/looking up members). */
    fun childNamespace(field: BlueprintField): BlueprintNamespace =
        field.namespace.copy(path = field.namespace.path + field.handle)
}
```

`resolveField` walks: segment 0 via `AntlersFieldContext.resolveField(element, pathPrefix[0], project)`
(honors loop/page/global scope); each deeper segment *i* via
`BlueprintService.fieldsFor(childNamespace(prev)).firstOrNull { it.handle == pathPrefix[i] }`. Returns
null on the first segment that doesn't resolve.

### 3.3 Augmentation property catalog (`catalog/FieldtypeProperties.kt`, new)

Kotlin data (runtime-classpath safe — no JSON parser ships, per project memory):

```kotlin
data class FieldProperty(val name: String, val description: String)

object FieldtypeProperties {
    /** Curated properties for an augmenting fieldtype; empty for types without dotted augmentation. */
    fun forType(type: String): List<FieldProperty>     // normalizes asset→assets, entry→entries, user→users, term→terms
}
```

Starter coverage:
- **assets:** url, permalink, path, alt, title, basename, filename, extension, size, size_bytes, is_image,
  width, height, mime_type, last_modified.
- **entries:** id, title, slug, url, permalink, date, status, published, author, edit_url.
- **users:** id, name, email, avatar, initials, is_admin, last_login, edit_url.
- **terms:** id, title, slug, url, permalink, entries_count.
- **link:** url, title, element.
- **date:** timestamp, iso, day, month, year.

### 3.4 Member completion (`completion/AntlersCompletionProvider.kt`)

- **New `FIELD_PATH` branch:** `val field = AntlersMemberResolver.resolveField(position, info.pathPrefix, project)`;
  if non-null, offer:
  - blueprint sub-fields: `BlueprintService.fieldsFor(AntlersMemberResolver.childNamespace(field))`,
    typeText "Field", deduped by handle;
  - augmentation properties: `FieldtypeProperties.forType(field.type)`, typeText "Property", deduped
    against the sub-fields.
  No tags/system-vars in this branch.
- **Extended `TAG_METHOD` branch:** keep offering `catalog.tag(tagHead).methods` (unchanged). If
  `catalog.tag(tagHead)` is null, fall back to the FIELD_PATH offering using `info.pathPrefix`
  (so `{{ group:‹caret› }}` offers the group's sub-fields/properties).

### 3.5 Member nav (`references/AntlersDefinitionReferenceHelper.kt` + `AntlersBlueprintMemberReference.kt`)

A new soft reference resolves a specific sub-field directly:

```kotlin
class AntlersBlueprintMemberReference(element: PsiElement, namespace: BlueprintNamespace, handle: String)
    : PsiReferenceBase<PsiElement>(element, ..., /* soft */ true)
    // resolve(): BlueprintService.fieldsFor(namespace).firstOrNull { it.handle == handle } → its file/offset
```

`AntlersDefinitionReferenceHelper.refsForIdent` is extended: when `element` is a **non-head** `T_IDENT`
segment of a namePath (not in a parameter value or closing tag — the existing E3a/C guards), let
`prefix` be the segments before it. Resolve `prefix` via `AntlersMemberResolver.resolveField`; if it
resolves to a field, look up this segment's handle in `childNamespace(thatField)`; if found, attach an
`AntlersBlueprintMemberReference(element, childNamespace, handle)`. **Augmentation properties get no
reference** (no declaration to target). The head segment keeps its existing E3a behavior unchanged.

### 3.6 Member docs (`documentation/AntlersDocumentationProvider.kt`)

Generalize the non-head namePath case. For the segment ident under the caret with preceding segments
`prefix`:
1. If `prefix` resolves (`AntlersMemberResolver.resolveField`) to a field, and this segment is a
   blueprint sub-field in `childNamespace(thatField)` → the existing field doc (with its namespace-path
   label from E3a).
2. Else if this segment is in `FieldtypeProperties.forType(parentField.type)` → a property doc:
   `Property <name> — <description> · <type>`.
3. Else fall through to today's behavior (the catalog tag-method doc for `collection:count`, etc.).

The `head` segment branch is unchanged (E1/E2/E3a).

## 4. File / Package Layout

```
completion/AntlersCompletionContext.kt        (FIELD_PATH kind, pathPrefix, T_DOT branch, colon pathPrefix)
completion/AntlersCompletionProvider.kt        (FIELD_PATH branch + TAG_METHOD field fallback)
scope/AntlersMemberResolver.kt                 (new — path → field, childNamespace)
catalog/FieldtypeProperties.kt                 (new — curated augmentation catalog)
references/AntlersBlueprintMemberReference.kt   (new — soft sub-field reference)
references/AntlersDefinitionReferenceHelper.kt  (attach member ref on non-head segments)
documentation/AntlersDocumentationProvider.kt   (member doc on non-head segments)
```

No `plugin.xml` changes, no grammar/lexer changes.

## 5. Testing

`BasePlatformTestCase` (runs via `./gradlew test`), plus a JUnit4 unit test for the catalog. Fixtures: a
collection `blog` blueprint with a Group `hero` (sub-fields `headline` text, `subhead` text), an
`assets` field `hero_image`, an `entries` field `author`; a `content/collections/blog.yaml`
(`template: blog/show`) so a `resources/views/blog/show.antlers.html` file maps to the blueprint.

- **Classifier** (`AntlersCompletionContextTest` additions): `{{ a.b.‹caret› }}` → `FIELD_PATH`,
  `pathPrefix=["a","b"]`; `{{ hero:‹caret› }}` → `TAG_METHOD`, `pathPrefix=["hero"]`.
- **Member completion** (`AntlersMemberCompletionTest`): `{{ hero.‹caret› }}` offers `headline`/`subhead`
  and **not** a page-level sibling; `{{ hero_image.‹caret› }}` offers `url`/`alt` (assets properties);
  `{{ hero:‹caret› }}` (colon) offers the same members as the dot form; works inside
  `{{ collection:blog }}…{{ /collection }}` too (scope-aware segment 0).
- **Augmentation catalog** (`FieldtypePropertiesTest`, JUnit4): `forType("asset") == forType("assets")`
  and contains `url`; `forType("text")` is empty.
- **Member nav** (`AntlersMemberNavTest`): go-to-def on `subhead` in `{{ hero.sub‹caret›head }}` resolves
  to the blueprint's `subhead` line; on `{{ hero_image.u‹caret›rl }}` (augmentation property) resolves to
  null; the head segment still resolves.
- **Member docs**: hover `subhead` → field doc mentioning `hero`; hover `url` on `hero_image` → property
  doc mentioning `assets`.
- **Regression**: E1/E2/E3a + existing completion-context/nav/docs tests stay green (the `T_DOT` branch
  is new; `TAG_METHOD` keeps catalog-tag methods first).

## 6. Out of Scope (follow-ups)

- Deeper augmentation chains past one hop (`author.avatar.url` where `avatar` is itself an augmenting
  value) — v1 offers members for one augmentation level.
- An entry/term/user augmentation expanding into its *own* blueprint's fields via `.` (relationship
  traversal across collections).
- Bracket/index access (`{{ arr[0] }}`), and modifier-style "properties" (`date.format`).
- Exhaustive/version-pinned augmentation property coverage (the catalog is a curated starter set).

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `T_DOT` branch misfires on non-field dots (`4.5`, `{{ arr.0 }}`) | Path resolves to null → offers nothing; numbers aren't field handles; never throws |
| Augmentation catalog incomplete / version drift | Curated starter set, clear "Property" typeText; unknown type → empty; never a wrong nav target; extensible — documented |
| Deep multi-segment across augmentation | v1 resolves blueprint sub-fields fully but stops at the first augmentation level; documented follow-up |
| Member reference leaks onto parameter values / closing tags | Reuse the E3a/C guards (exclude parameter-value namePaths and closing tags) before attaching |
| Colon ambiguity (`collection:count` vs `group:sub`) | Catalog tag methods tried first; field-member fallback only when the head isn't a catalog tag |
| Member completion offering tags/system-vars after a dot | The `FIELD_PATH` branch offers only Field/Property members, never statement-level tags/vars |
| No JSON parser on the plugin runtime classpath | `FieldtypeProperties` is Kotlin data, like the existing tag catalog |
