# Antlers partial `@param` hints at the include site

**Date:** 2026-06-06
**Status:** Approved (design)

## Problem

A reusable partial declares its inputs with leading directive comments, e.g. `components/_button.antlers.html`:

```antlers
{{#
    @name Button attributes
    @desc A single button component.
    @param* label The caption label.
    @param as The wrapping element. Defaults to `a`.
    @param button_type `Inline` if the button needs to be rendered as an inline button.
    @param faux Boolean. For faux button wrapped in an actual button/anchor.
#}}
```

When a developer includes that partial — `{{ partial:components/button label="…" as="…" }}` — the IDE
gives no help with those parameters. We want the include site to surface the included partial's declared
`@param`s.

## Scope

In scope (both the colon form `{{ partial:components/button … }}` and the `src=` form
`{{ partial src="components/button" … }}`):

1. **Parameter-name autocomplete** at the include site, sourced from the included partial's `@param`
   declarations (name, required marker, description).
2. **Quick documentation** (hover / Ctrl-Q) on a param name, showing its `@param` description and
   required/optional status.

Explicitly out of scope (may be separate features later):

- Parameter info (Ctrl/⌘P) popup for partial params.
- An inspection that warns when a required (`@param*`) parameter is omitted.
- Value completion for partial params.
- Resolving addon-namespaced partials (`vendor::path`); these simply yield no params.

## `@param` line format

`@param[*] <name> <description…>`

- The directive may carry a trailing `*` (`@param*`) meaning **required**.
- `AntlersHintParser`'s regex (`^(\s*)(@[a-zA-Z]+)\b(.*)$`) matches `@param` and leaves the `*` in the
  captured value, so a line `@param* label The caption label.` parses to directive name `param`,
  value `"* label The caption label."`.
- Decomposition of the value: trim; a leading `*` ⇒ `required = true` (strip it); the first
  whitespace-delimited token ⇒ `name`; the remainder (trimmed) ⇒ `description`.
- `@param as The wrapping element.` ⇒ `name = as`, `required = false`,
  `description = "The wrapping element."`.
- A value with no name token (empty after stripping) ⇒ no param (ignored).

## Architecture (Approach 1: thin module + reuse existing extension points)

### New: `AntlersPartialParams`

A small module (pure decomposition + a PSI reader).

- `data class PartialParam(val name: String, val required: Boolean, val description: String)`
- `fun fromDirectiveValue(value: String): PartialParam?` — **pure**; implements the decomposition above;
  returns null when no name token remains.
- `fun of(partialFile: PsiFile): List<PartialParam>` — reads the partial file's `{{# … #}}` comment
  bodies, runs `AntlersHintParser.parse`, keeps `@param` directives, maps each via `fromDirectiveValue`,
  drops nulls. Preserves declaration order. De-dupes by name (first wins) defensively.

`of` reads the Antlers PSI of the partial file (its comment elements). Decomposition lives in
`fromDirectiveValue` so it is unit-testable without the platform.

### Reused: resolve the included partial from the include site

Add to `AntlersPartialReferenceHelper`:

- `fun includedPartialFile(statement: AntlersStatement): PsiFile?` — returns the resolved partial
  `PsiFile`, or null. Path comes from either:
  - the colon/slash/dot path via the existing `extractPartialPath(statement)`, or
  - the `src=` parameter's string value (when present),
  then `StatamicProject.resolvePartial(anchorElement, path)` → `VirtualFile` →
  `PsiManager.getInstance(project).findFile(vf)`.

This reuses the existing, already-tested resolution (partials/ dir, underscored, dotted/slash nesting,
`.antlers.html`/`.antlers.php`).

### Wiring point 1 — completion (`AntlersCompletionProvider`, `PARAMETER` branch)

When `info.tagHead == "partial"`:

- Resolve the included partial via `includedPartialFile(statement)`.
- If it resolves, add a lookup element for each `PartialParam`:
  - lookup string = `name`
  - type text = `"Param*"` when required else `"Param"`
  - tail text = the description (when non-blank)
  - insert handler = the existing `ParameterInsertHandler` (inserts `="<caret>"`)
- **`src` suppression:** detect the form via `extractPartialPath(statement)` (which reads the namePath /
  slash path, and is null for `{{ partial src="…" }}` and bare `{{ partial }}`):
  - **colon form** (`extractPartialPath != null`, e.g. `partial:components/button`): offer ONLY the
    component's own params — do NOT add the catalog `partial` `src` parameter.
  - **`src=` form** (`extractPartialPath == null` but a `src=` value resolves) and **bare form** (both
    null): keep the existing catalog `src` behavior so `src` stays discoverable, and (for `src=`) also
    add the resolved partial's params.
- If the partial does not resolve (or declares no `@param`s), fall back to today's behavior.

### Wiring point 2 — quick documentation (`AntlersDocumentationProvider`)

Before the existing catalog-parameter case: if the caret is on a parameter **name**
(`AntlersParameterMixin`) whose enclosing tag head is `partial`, and `includedPartialFile` resolves, and
a `PartialParam` matches that name, render a doc section:

- title: `Parameter <b>name</b> (required|optional) — partial <code>components/button</code>`
- content: the `@param` description.

Use the provider's existing `section(...)` HTML helper. If no match, fall through to existing behavior
(which, for an unknown `partial` param, currently returns null — unchanged).

## Data flow

```
include statement  --extractPartialPath / src=-->  path string
path string        --StatamicProject.resolvePartial-->  VirtualFile  --findFile-->  PsiFile
PsiFile            --AntlersPartialParams.of-->  List<PartialParam>
List<PartialParam> --> completion lookup elements  /  doc section for the caret's param name
```

## Error handling / edge cases

- Unresolved partial, missing `src` value, no comments, or no `@param` lines ⇒ empty list ⇒ no extra
  completion, no doc; never throws. Falls back to existing catalog behavior.
- Addon-namespaced partials (`vendor::path`) that `resolvePartial` cannot find ⇒ no params (documented
  limitation).
- Default project / no Statamic project root ⇒ resolver returns null ⇒ no params.
- Reading the partial's top comments is cheap; **no caching** in the first iteration (deferred — add
  `CachedValuesManager` keyed on the partial file only if profiling shows a need).

## Testing (TDD)

Unit (pure, no platform):
- `fromDirectiveValue("* label The caption label.")` ⇒ `(label, required=true, "The caption label.")`
- `fromDirectiveValue("as The wrapping element.")` ⇒ `(as, false, "The wrapping element.")`
- `fromDirectiveValue("faux")` ⇒ `(faux, false, "")`
- `fromDirectiveValue("")` / `fromDirectiveValue("*")` ⇒ null

Platform (`BasePlatformTestCase`, partial added via `addFileToProject`):
- `AntlersPartialParams.of(partialFile)` returns the four params in order with correct required flags.
- Completion in `{{ partial:components/button <caret> }}` offers `label/as/button_type/faux`, marks
  `label` as `Param*`, and does **not** offer `src`.
- Completion in `{{ partial src="components/button" <caret> }}` offers the same params **and** still
  offers `src`.
- Quick docs on `as` in an include shows "The wrapping element." and "optional".
- Quick docs on `label` shows "required".
- Unresolved partial (`{{ partial:does/not/exist <caret> }}`) ⇒ no crash, no partial params.

## Files touched

- New: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParams.kt`
- Edit: `references/AntlersPartialReferenceHelper.kt` (add `includedPartialFile`)
- Edit: `completion/AntlersCompletionProvider.kt` (`PARAMETER` branch)
- Edit: `documentation/AntlersDocumentationProvider.kt` (partial-param doc case)
- New tests under `src/test/kotlin/.../references`, `.../completion`, `.../documentation`.
- README: add partial-`@param` autocomplete + docs to the Navigation/Completion notes.
