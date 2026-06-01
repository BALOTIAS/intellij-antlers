# Antlers Blueprint Variable Resolution (v1) — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Blueprint-backed variable completion, go-to-definition, and docs in `{{ }}` — the
"offer-all-fields" v1. Precise template→blueprint scoping and loop-scope tracking are deferred.

## 1. Background & Goal

Sub-projects A–D are complete. This adds **variable awareness** from Statamic blueprints. Statamic
blueprints/fieldsets are YAML files defining fields; each field's `handle` is the name used as a
`{{ variable }}` in templates. This v1 parses all blueprints + fieldsets and offers their field
handles (plus standard Statamic system variables) as completions in `{{ }}` variable positions, with
go-to-definition to the field and hover docs. It stays **platform-only** (hand-rolled YAML
extraction, `FilenameIndex`/VFS) and does **not** attempt precise scoping.

## 2. Principles

1. **Platform-only.** Hand-rolled YAML field extraction; no YAML-plugin or YAML-library dependency.
2. **Reuse A–D.** Variable completion extends the existing `AntlersCompletionProvider`; go-to-def
   reuses the C custom-leaf reference mechanism; docs extend the C documentation provider.
3. **Offer-all, deduped.** All blueprint/fieldset field handles + curated system variables, deduped
   by handle. No template→collection mapping in v1.
4. **Tolerant parsing.** The extractor never throws; malformed YAML simply yields fewer fields.

## 3. Components

### 3.1 Blueprint model + scanner (`blueprint/`)
- `BlueprintField(handle: String, display: String, type: String, file: VirtualFile, offset: Int)`.
- `BlueprintScanner` (object): `scan(project): List<BlueprintField>`.
  - Finds `*.yaml` files whose path contains `/resources/blueprints/` or `/resources/fieldsets/`
    (via `FilenameIndex.getAllFilesByExt(project, "yaml", ...)` + path filter).
  - Hand-extracts fields by scanning lines for `handle: <name>` (with or without a leading `- `);
    for each, captures the handle and looks at the following more-indented lines for `display:` and
    `type:` (and the inline `field: <type>` short form). Records the file + the offset of the handle
    value. Tolerant: ignores lines it can't parse; never throws.
- `BlueprintService` (`@Service(PROJECT)`): caches `scan` results with a
  `PsiModificationTracker.MODIFICATION_COUNT` cached value; exposes `fields(): List<BlueprintField>`
  (deduped by handle, first wins) and `field(handle): BlueprintField?`.

### 3.2 System variables (`blueprint/SystemVariables.kt`)
A curated bundled list (Kotlin data) of common Statamic globals, e.g.: `title`, `url`, `permalink`,
`slug`, `id`, `date`, `last_modified`, `status`, `published`, `content`, `author`, `template`,
`layout`, `collection`, `mount`, `parent`, `depth`, `is_current`, `is_parent`, `now`, `site`,
`locale`, `current_url`, `current_uri`, `csrf_token`, `environment` — each `SystemVariable(name,
description)`.

### 3.3 Variable completion (extend `completion/AntlersCompletionProvider.kt`)
In the existing `TAG_NAME` branch (the `{{ <caret> }}` head position — a tag *or* variable in
Antlers), additionally add:
- blueprint fields: `LookupElement` per `BlueprintService.fields()` field, `typeText = "Field"`, tail
  text = the field's display label;
- system variables: `typeText = "Variable"`, tail text = the description.
Deduped against each other and against the catalog tag names. Plain identifiers (no insert handler).

### 3.4 Variable go-to-definition (`references/AntlersBlueprintFieldReference.kt` + wire-in)
- `AntlersBlueprintFieldReference(element, handle)` — a **soft** `PsiReferenceBase` whose `resolve()`
  looks up `BlueprintService.field(handle)` and returns the PSI element at that file/offset
  (`psiFile.findElementAt(offset)` ?: the file). Soft, so non-field variables aren't flagged.
- Wire into `AntlersDefinitionReferenceHelper.refsForIdent`: for a `{{ }}` head identifier (the first
  segment of the statement's `namePath`), also append a blueprint-field reference. The element may
  thus carry both the custom-tag reference (from C) and this blueprint-field reference; the platform
  resolves whichever is non-null.

### 3.5 Variable docs (extend `documentation/AntlersDocumentationProvider.kt`)
For a head identifier that is **not** a catalog tag: if it's a blueprint field, show its display label
+ type ("Field `handle` — Display (type)"); else if it's a system variable, show its description.
Returns null when it's neither (plain unknown variable).

## 4. File / Package Layout

```
blueprint/BlueprintField.kt, BlueprintScanner.kt, BlueprintService.kt, SystemVariables.kt
references/AntlersBlueprintFieldReference.kt
references/AntlersDefinitionReferenceHelper.kt    (extend: append blueprint-field ref for the head ident)
completion/AntlersCompletionProvider.kt           (extend TAG_NAME branch)
documentation/AntlersDocumentationProvider.kt     (extend: field/system-var docs)
```

## 5. Testing

`BasePlatformTestCase` (runs via `./gradlew test`):
- **Scanner:** a fixture `resources/blueprints/collections/blog/blog.yaml` with `handle: hero_title`
  → `BlueprintScanner.scan` includes `hero_title` with the right file/offset.
- **Completion:** in a fixture project with the blueprint, `{{ <caret> }}` completion includes
  `hero_title` (Field) and `title` (system Variable), alongside `collection` (tag).
- **Go-to-def:** `{{ hero_<caret>title }}` resolves to the blueprint YAML; an unknown variable resolves
  to null.
- **Docs:** `generateDoc` for `hero_title` mentions its display/type; for `url` (system var) mentions
  its description.

## 6. Out of Scope (follow-ups)

- Precise template → collection → blueprint mapping (offer only the relevant blueprint's fields).
- Loop / `as`-alias scope tracking; variables introduced by `{{ collection }}…{{ /collection }}`.
- Nested field sets (Bard / Replicator / Grid sub-fields), dotted access (`{{ author.name }}`).
- Global-set and taxonomy variables beyond the curated system list.

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Hand-rolled YAML mis-parses exotic blueprints | Extractor is line-based + tolerant; only `handle:` is required, metadata is best-effort; never throws |
| Variable completion noise (all fields offered) | Deduped; clear `typeText` (Field/Variable/Tag); precise scoping is a documented follow-up |
| Head-ident reference ambiguity (tag vs field) | Both references are soft; resolve in order; native tags/fields each resolve only their own target |
| Scanner performance on large projects | Cached via modification tracker; path-filtered to `resources/blueprints`/`resources/fieldsets` |
