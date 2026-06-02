# Antlers Variable Refactoring (H2) — Design

**Date:** 2026-06-02
**Branch:** `antlers-grammar-completion`
**Status:** Approved, ready for implementation plan

## Goal

Give blueprint **field handles** (the names behind `{{ variable }}`) full refactoring support:
precise go-to-declaration, **Find Usages**, and **Rename** that propagates across both YAML
(the canonical `handle:`) and every `{{ handle }}` / `{{ container:handle }}` usage in templates.

Scope (confirmed): **top-level field handles AND nested member fields** (sub-fields of
Grid/Group/Replicator/Bard, and relationship members). Rename invoked **from a usage** (or from
the synthetic declaration); rename invoked directly on the YAML `handle:` token is **out of scope**
(would need a separate plain-text rename handler — deferred; rename-from-usage already updates the
declaration).

## Background — current state

- `AntlersBlueprintFieldReference` (handle resolved via scope context) and
  `AntlersBlueprintMemberReference` (explicit `namespace` + `handle`) both resolve a usage to
  `psiFile.findElementAt(field.offset)` in the blueprint `.yaml`. **Soft** references.
- `BlueprintScanner` parses blueprints/fieldsets as **plain text via regex** (no YAML PSI),
  capturing each field's exact `handle:` char `offset` into `BlueprintField(handle, display, type,
  file, offset, namespace, linkedNamespaces)`.
- Blueprints live under `resources/blueprints/`; fieldsets under `resources/fieldsets/`. A field
  declared in a fieldset is **imported** (grafted) into every blueprint that `import:`s it.

### Why the current target is unusable for refactoring

On a plain-text `.yaml` file the entire file is a **single PSI leaf**, so
`findElementAt(anyOffset)` returns the *same* element for every field. Consequences:

1. Go-to-declaration navigates to the **top of the file**, not the handle.
2. Find Usages cannot tell `title` from `subtitle` (same target leaf).
3. A plain-text leaf is **not renamable**.

## Approach (chosen: B — synthetic element, no YAML dependency)

Rejected alternatives:
- **A — add the bundled YAML plugin dependency.** Real build/test-sandbox risk for this project,
  and a `YAMLScalar` still isn't a `PsiNamedElement`, so cross-file rename machinery would still be
  hand-written. Dependency cost without removing the work.
- **C — hybrid (YAML dep + synthetic wrapper).** Worst of both.

**B** introduces a synthetic, identity-bearing, renamable declaration element that both references
resolve to. Self-contained, no new dependency, and it *improves* go-to-declaration precision.

## Components

### 1. `AntlersFieldDeclaration` (new) — `RenameableFakePsiElement`

Lightweight fake PSI element built from a `BlueprintField`. The single find-usages / rename target.

- `getName()` → `field.handle`
- `setName(newName)` → edits the YAML text at `[field.offset, field.offset + field.handle.length)`
  via the document, renaming the canonical `handle:`. Returns `this`.
- `navigate(requestFocus)` / `canNavigate()` → `OpenFileDescriptor(project, field.file,
  field.offset).navigate(...)` — lands precisely on the handle (fixes the "jumps to file top" bug).
- Identity: `isEquivalentTo(other)` (and `equals`/`hashCode`) by
  `(handle, namespace, file, offset)`. So `blog.title`, `news.title`, and a nested
  `grid_field.title` are all distinct declarations.
- `getParent()` / `getContainingFile()` → the blueprint PSI file (gives the platform a context for
  rename/usages UI).
- `getProject()` / `getLanguage()` / `getTextRange()` as required by `FakePsiElement` contract;
  text range derived from `(offset, offset+handle.length)`.

### 2. References resolve to the declaration

Both `AntlersBlueprintFieldReference` and `AntlersBlueprintMemberReference`:

- `resolve()` → build an `AntlersFieldDeclaration` from the resolved `BlueprintField` (instead of
  `findElementAt(offset)`). Stays **soft** (so non-Statamic projects don't show unresolved errors).
- `isReferenceTo(target)` → `true` when `target` is an `AntlersFieldDeclaration` whose
  `(handle, namespace)` equals this reference's resolved field's `(handle, namespace)`.
- `handleElementRename(newName)` → rewrite only the `{{ }}` ident segment this reference covers,
  using the H1 leaf mechanic: `(element as LeafPsiElement).replaceWithText(rewritten).psi`. The
  member reference's `element` already *is* the specific sub-field ident leaf, so its rewrite is the
  whole element text; the field reference's range covers the lone variable ident.

### 3. `AntlersFieldReferenceSearcher` (new) — `QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>`

Same shape as H1's `AntlersPartialReferenceSearcher`. When
`params.elementToSearch` is an `AntlersFieldDeclaration`:
- iterate every Antlers file via `FileTypeIndex.getFiles(AntlersFileType.INSTANCE, scope)`,
- recursively visit elements, and for each `AntlersBlueprintFieldReference` /
  `AntlersBlueprintMemberReference` whose `isReferenceTo(target)` holds, `consumer.process(ref)`.

Registered `<referencesSearch>`. Powers both Find Usages and rename's reference collection.

### 4. `AntlersFieldRenameProcessor` (new) — `RenamePsiElementProcessor`

Safety net for the soft-reference risk (soft refs can be skipped by the default rename flow).
- `canProcessElement(element)` → `element is AntlersFieldDeclaration`.
- Drives `setName` on the declaration and collects usages through `ReferencesSearch` (our searcher),
  guaranteeing every soft usage reference is renamed via its `handleElementRename`.
- Registered `<renamePsiElementProcessor>`.

### 5. Find Usages provider

The existing `AntlersFindUsagesProvider` (from H1, `<lang.findUsagesProvider>`) already covers the
Antlers side. The synthetic declaration is reached through `ReferencesSearch` (our searcher); if the
usages view needs a node title for the declaration, supply it via the element's `getName()` /
`ElementDescriptionProvider` default from `FakePsiElement`.

## Data flow — rename from a usage

```
{{ hero_title }}  --Rename-->
  platform resolves the reference
    -> AntlersFieldDeclaration (handle=hero_title, ns=…, file, offset)
      -> AntlersFieldRenameProcessor
           setName  -> edits YAML `handle: hero_title` -> `handle: hero_subtitle`
           ReferencesSearch (AntlersFieldReferenceSearcher)
             -> each {{ hero_title }} / {{ x:hero_title }}.handleElementRename("hero_subtitle")
```

Editing the YAML bumps `PsiModificationTracker`; `BlueprintService`'s cached scan refreshes
automatically on the next resolve.

## Edge cases

- **Fieldset-imported fields:** the declaration's `file`/`offset` point at the fieldset's canonical
  `handle:`. Rename edits one place; the searcher updates usages across *all* importing blueprints'
  templates.
- **Same handle, different namespace:** scope-aware `resolveField` disambiguates per usage;
  identity-by-namespace keeps find-usages/rename scoped. An unrelated `news.title` is never touched
  when renaming `blog.title`.
- **Nested members:** a nested field's `namespace` encodes its container path, so `{{ grid:title }}`
  and a top-level `{{ title }}` are distinct declarations and rename independently.
- **Validation:** new handle must match `[A-Za-z_][A-Za-z0-9_]*`; rely on the platform's rename
  input validator (no custom validator).

## Risk & fallback

Soft references may be excluded from the default rename reference search. The
`RenamePsiElementProcessor` is the mitigation (it collects references itself). If during TDD even
that proves intractable on some path, the documented fallback is to ship **Find Usages + precise
go-to-declaration** (both solid under this design) and record the rename limitation. Expectation:
the processor carries rename end-to-end.

## Testing (`BasePlatformTestCase`, runnable via `./gradlew test`)

- `testGoToDeclarationPrecise` — resolve navigates to the exact handle offset, not file top.
- `testFindUsagesTopLevelField` — blueprint `hero_title` + template `{{ hero_title }}` → one usage.
- `testFindUsagesScopedDistinct` — two blueprints each with `title`, two scoped templates →
  find-usages of one returns only its usage.
- `testRenameFieldUpdatesUsagesAndYaml` — rename declaration → `{{ hero_title }}` becomes
  `{{ hero_subtitle }}` and the YAML `handle:` is updated.
- `testRenameFromUsage` — invoke rename on the `{{ hero_title }}` usage → both updated.
- `testRenameNestedMember` — `{{ grid_field:subfield }}` rename of `subfield`.
- `testRenameFieldsetFieldUpdatesAllImportingTemplates` — a fieldset field imported by two
  blueprints, two templates → rename updates both usages.
- `testUnrelatedSameHandleFieldNotRenamed` — a `title` in a different namespace is untouched.

## Out of scope

- Rename invoked directly on the YAML `handle:` token (needs a plain-text rename handler).
- Adding the YAML plugin dependency.
- Renaming fieldtype container *paths* or blueprint file names.
