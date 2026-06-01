# Antlers Semantic Layer — Docs + Navigation (Sub-project C) — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Sub-project **C** of the "full Statamic 6 Antlers compatibility" effort, scoped to
**documentation + navigation**. Blueprint-based variable resolution is deferred to its own later
sub-project.

## 1. Background & Goal

Sub-projects A (grammar/PSI) and B (structural completion) are complete: the parser produces
`AntlersStatement` / `AntlersNamePath` / `AntlersParameter` / `AntlersModifier` nodes, and
`AntlersCatalogService` provides a bundled Statamic-6 tag/modifier catalog merged with project-scanned
custom tags/modifiers.

This sub-project adds the **semantic layer's docs and navigation**: hover documentation, partial
go-to-definition + completion, and custom tag/modifier go-to-definition. It stays **platform-only**
(no PHP-plugin dependency) so the plugin keeps working in every JetBrains IDE.

## 2. Principles

1. **Platform-only.** Navigation uses `FilenameIndex` / VFS, not the PHP PSI. Custom-tag go-to-def
   lands on the PHP *file*, not a precise PHP symbol.
2. **Reuse A + B.** Documentation and references reuse `AntlersCatalogService`, the PSI mixins, and
   the token-based context classification already built.
3. **Small, focused units.** Each feature is one contributor/provider plus a tiny helper.
4. **Convention over configuration.** Partial resolution follows Statamic's `resources/views/`
   convention, discovered from the file's location rather than configured.

## 3. Components

### 3.1 Documentation provider
`documentation/AntlersDocumentationProvider.kt` (rewrite of the existing stub), registered as
`<lang.documentationProvider language="Antlers">` (already registered).

- Implements `AbstractDocumentationProvider` (`getQuickNavigateInfo` + `generateDoc`).
- For the identifier under the caret, classify it using the same token-based logic as
  `AntlersCompletionContext` (tag head / `:method` / parameter name / modifier name) plus the
  enclosing statement's tag head, then look it up in `AntlersCatalogService`:
  - **Tag** → name, "block/single" note, description, a parameter table (name — description), and a
    clickable `docUrl` link.
  - **Tag method** → the method name under its tag.
  - **Parameter** → the parameter's description/type/required, scoped to its tag.
  - **Modifier** → name, description, `docUrl`.
- Returns `null` for anything not in the catalog (e.g. plain variables) so the platform shows nothing
  rather than wrong docs.
- Output is simple HTML using `DocumentationMarkup` builders.

### 3.2 Partial navigation
`references/AntlersPartialReferenceContributor.kt` + `references/AntlersPartialReference.kt`,
registered as `<psi.referenceContributor language="Antlers">`.

- Attaches a reference to the partial path in both forms:
  - `{{ partial:src="blog/card" }}` — the `src` parameter's string value.
  - `{{ partial:blog/card }}` — the name-path method after `partial:`.
- `AntlersPartialReference.resolve()` locates the views root (see 3.4) and resolves the path to the
  first existing of `‹viewsRoot›/‹path›.antlers.html`, `‹viewsRoot›/‹path›.html` → returns that
  file's `PsiFile` (go-to-definition).
- `getVariants()` returns partial-path completions: every `*.antlers.html` / `*.html` under the views
  root, presented as slash-separated paths without extension.
- Unresolved paths are left unresolved (platform highlights them); an inspection is out of scope here.

### 3.3 Custom tag / modifier go-to-definition
`references/AntlersTagDefinitionReferenceContributor.kt` + `references/AntlersPhpClassReference.kt`,
registered as `<psi.referenceContributor language="Antlers">`.

- Attaches a reference to:
  - a statement's tag-head identifier (the first `AntlersNamePath` segment), and
  - a modifier's name identifier.
- The reference resolves **only when the name matches a project-scanned custom tag/modifier**:
  `TagScanner`/`ModifierScanner` are extended to expose `find(project, snakeCaseName): NavTarget?`
  returning the PHP file's `VirtualFile` + the class declaration offset. Native catalog tags/modifiers
  resolve to nothing (no project file). Resolve → a navigable PSI element at that file/offset.
- This gives Ctrl-click navigation from `{{ my_custom_tag }}` / `| my_modifier` to the PHP class file.

### 3.4 Shared: views-root locator
`references/StatamicProject.kt` — `fun viewsRoot(file: PsiFile): VirtualFile?` walks up from the file
to the nearest ancestor matching `…/resources/views` (the Statamic/Laravel convention) and returns it;
falls back to searching the project for a `resources/views` directory. Used by partial resolution +
completion.

## 4. File / Package Layout

```
documentation/AntlersDocumentationProvider.kt        (rewrite)
references/StatamicProject.kt                          (views-root locator)
references/AntlersPartialReferenceContributor.kt
references/AntlersPartialReference.kt
references/AntlersTagDefinitionReferenceContributor.kt
references/AntlersPhpClassReference.kt
catalog/scan/TagScanner.kt, ModifierScanner.kt         (extend: name → file+offset NavTarget)
resources/META-INF/plugin.xml                          (+2 psi.referenceContributor; docProvider exists)
```

## 5. Testing

`BasePlatformTestCase` (the test infra now works; tests run via `./gradlew test`):
- **Docs:** `generateDoc` for a tag / parameter / modifier returns HTML containing the catalog
  description and doc URL; returns null for a plain variable.
- **Partials:** with a fixture views tree (`resources/views/blog/card.antlers.html`), the reference in
  `{{ partial:src="blog/card" }}` and `{{ partial:blog/card }}` resolves to that file; `getVariants`
  includes `blog/card`; an unknown path resolves to null.
- **Custom-tag go-to-def:** with a fixture `app/Tags/MyThing.php` defining `class MyThing extends Tags`,
  the reference on `{{ my_thing }}` resolves to that file; a native tag (`collection`) and an unknown
  name resolve to null.
- **Scanner:** `find` maps a snake_case name to the right PHP file + offset.

## 6. Out of Scope (future sub-projects)

- **Blueprint-based variable resolution/completion** (YAML blueprints, template↔collection mapping,
  field variables in `{{ }}`) — its own sub-project.
- Precise PHP-symbol resolution via the PHP plugin (we land on the file).
- Sub-project **D**: brace matching, auto-close `}}`, folding, formatter, structure view.
- Inspections (e.g. "unknown partial"/"unknown tag") beyond leaving references unresolved.

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Views root not found (non-standard project layout) | Walk-up + project-wide `resources/views` search; resolve to null (no crash) if absent |
| Partial form variety (`src=` vs `:path`) | Reference contributor handles both explicitly; tested for each |
| Tag-head reference firing on plain variables | Resolve only when the name matches a scanned custom tag; native/unknown → null reference (no false navigation) |
| Scanner performance for `find` | Reuse the existing cached scan; `find` filters the cached results |
