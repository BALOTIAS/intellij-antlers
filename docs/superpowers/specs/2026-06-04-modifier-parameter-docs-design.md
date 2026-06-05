# Modifier Parameter Docs — Design

**Date:** 2026-06-04
**Branch:** `modifier-parameter-docs`
**Status:** Approved approach, pending spec review

## Goal

Give Antlers modifiers real parameter information and surface it in three places:

1. **Quick-docs** (hover / Ctrl+Q) — the signature `replace(search, replacement)` plus a description per parameter.
2. **Completion** — the signature as grey tail text, and an insert template with a tab-stop per required parameter.
3. **Parameter info** (Ctrl+P) — an inline signature popup that bolds the current argument as you type.

Today `ModifierDef` only carries a `takesArguments: Boolean`; there is no per-parameter data, so completion inserts a bare `()` and quick-docs shows only name + description.

## Scope decisions (from brainstorming)

- **Surfaces:** all three (docs + completion + Ctrl+P).
- **Coverage:** author parameter data for **all 82** modifiers that take arguments. Genuinely variadic modifiers get one honest descriptive parameter rather than invented names.
- **Per-parameter data:** name + description + `optional` flag + `default` value (no per-param type hint).
- **Model:** a dedicated `ModifierParam` (approach A) — kept separate from the tag-side `ParamDef`, because tag params render as `name="…"` attributes while modifier params are positional.

## Key limitation (stated, accepted)

All three surfaces work on **real modifier chains** that have PSI, e.g. `{{ title | replace('a','b') }}`. They do **not** fire inside string interpolation (`"{… | replace(…)}"`), which is highlighting-only with no PSI — the same reason hover never worked there. Normal modifier usage is fully covered; interpolation support would be a separate, larger effort and is out of scope.

## Components

### A. Data model — `catalog/CatalogModels.kt`

```kotlin
/** One positional argument of a modifier. `default` is shown only when `optional`. */
data class ModifierParam(
    val name: String,
    val description: String = "",
    val optional: Boolean = false,
    val default: String = "",
)
```

`ModifierDef` gains `val parameters: List<ModifierParam> = emptyList()`. `takesArguments` stays (project-scanned modifiers set it without param data); `parameters` is the new richer source for bundled modifiers.

### B. Data authoring — `catalog/CatalogModifiers.kt`

Populate `parameters = listOf(ModifierParam(...))` for every modifier currently flagged `takesArguments = true` (82 of them). Conventions:

- Required parameters first, optional parameters last with a `default`.
- Source of truth: the modifier pages on statamic.dev.
- Variadic / fuzzy modifiers (arbitrary list of values) → a single descriptive parameter, e.g. `ModifierParam("values", "One or more values to test against.")`.
- Non-argument modifiers (`upper`, `lower`, …) keep `parameters = emptyList()`.

### C. Signature helper — `catalog/ModifierSignature.kt` (pure, IntelliJ-free)

```kotlin
object ModifierSignature {
    /** "replace(search, replacement)"; optional params render as "name = default" (or "name?" if no default). */
    fun render(def: ModifierDef): String
}
```

One source of truth consumed by docs, completion, and Ctrl+P. Pure → directly unit-testable. A modifier with no `parameters` renders just its bare name (no parens).

### D. Quick-docs — extend `documentation/AntlersDocumentationProvider`

In the existing modifier branch (resolved via `AntlersModifierMixin`):
- Title: `Antlers modifier <b>replace(search, replacement)</b>` (signature via `ModifierSignature.render`).
- Body: the description, then — when `parameters` is non-empty — a list, one row per parameter: `<b>name</b> — description` followed by ` <i>(optional, default: …)</i>` when optional.
- Footer: the existing `docUrl` link.

A modifier with no parameter data falls back to today's output (name + description + link). Mirrors the existing tag-parameter rendering already in this provider.

### E. Completion — `completion/AntlersCompletionProvider` + `completion/ModifierInsertHandler`

- **Lookup presentation:** for modifier lookups, show the signature as grey tail/type text (e.g. typing `rep` surfaces `replace(search, replacement)`).
- **Insertion:** `ModifierInsertHandler` builds an IntelliJ live `Template` (via `TemplateManager`) with one tab-stop variable per **required** parameter, rendered inside `()`:
  `replace(‹search›, ‹replacement›)` — Tab cycles through the stops.
  - Optional parameters are omitted from the inserted call (they remain in docs / Ctrl+P).
  - A modifier whose params are all optional, or that has params but none required, inserts `()` with the caret inside (today's behavior).
  - Non-argument modifiers are inserted unchanged (no parens).

### F. Parameter info (Ctrl+P) — `completion/AntlersModifierParameterInfoHandler`

`ParameterInfoHandler<AntlersModifierMixin, ModifierDef>`, registered as `<codeInsight.parameterInfo language="Antlers" .../>`:
- `findElementForParameterInfo` / `findElementForUpdatingParameterInfo`: the `AntlersModifierMixin` whose argument region (inside its `()` or after its first `:`) contains the caret.
- `updateParameterInfo`: compute the current parameter index by counting argument separators before the caret — commas within the `()` form, or `:` within the colon form.
- `updateUI`: render `ModifierSignature.render(def)` and bold the current parameter (standard `ParameterInfoUIContextEx` range highlighting). A variadic modifier's single parameter stays highlighted for every position.
- No `ModifierDef`, or a modifier with no params → no parameter info shown.

## Data flow

`AntlersModifierMixin` → `modifierName` → `AntlersCatalogService.modifiers()` → matching `ModifierDef` (with `parameters`) → `ModifierSignature.render` → rendered in quick-docs / completion tail / Ctrl+P. The current-argument index for Ctrl+P comes from the caret offset relative to the modifier's argument tokens.

## Error handling / edge cases

- **Modifier absent from catalog, or project-scanned with no params** → graceful: docs show name + description only; completion inserts `()` (or nothing for a no-arg modifier); Ctrl+P shows nothing.
- **Caret outside any modifier argument region** → Ctrl+P inactive.
- **Variadic modifier** → single descriptive parameter; Ctrl+P highlights it regardless of how many args are typed.
- **Both arg syntaxes** — Ctrl+P and index counting handle `replace('a','b')` and `replace:'a':'b'`. Insertion always uses the `()` form (matches existing behavior).

## Testing

- **`ModifierSignatureTest`** (pure): required-only, optional-with-default, all-optional, variadic, and no-params cases produce the expected strings.
- **Doc-provider test:** hovering `replace` in `{{ x | replace('a','b') }}` shows the signature and each parameter description; a modifier with no catalog params renders name + description only.
- **Completion test:** an arg-taking modifier's lookup exposes the signature; inserting it yields the `replace(‹search›, ‹replacement›)` template (assert the resulting document text and that tab-stops exist); an all-optional modifier inserts `()`; a no-arg modifier inserts no parens.
- **Parameter-info test:** `findElementForParameterInfo` resolves the modifier at the caret, and the current-parameter index is computed correctly from caret position for both `()` and `:` forms.
- **Data-sanity test:** every bundled modifier with `takesArguments = true` has ≥1 `ModifierParam`, and every bundled modifier with `parameters` is flagged `takesArguments = true` (guards the 82-entry authoring against drift).
- **Full-suite gate.**

## Out of scope

- Modifiers inside string interpolation (`"{… | replace(…)}"`) — no PSI to attach to.
- A colon-form (`replace:a:b`) **insertion** template — insertion uses `()`; Ctrl+P still reads the colon form.
- Per-parameter type hints (string / int / array); only optional + default are captured.
- Parameter data for project-scanned (custom) modifiers — they keep the graceful name-only fallback.
