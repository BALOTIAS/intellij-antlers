# Antlers Template IDE Hints (scope 2) — Design

**Date:** 2026-06-04
**Branch:** `antlers-template-hints`
**Status:** Approved approach, pending spec review
**Source:** Konafets/antlers-idea #140 — recognize the Antlers Toolbox "Template IDE Hints" (`{{# @name … #}}`).

## Goal

Recognize the 7 hint directives inside `{{# … #}}` comments:
```
{{#
    @name A friendly name
    @desc A description
    @collection blog
#}}
```
- **Recognition:** highlight the `@directive` tokens distinctly, and complete the directive names after `@`.
- **Blueprint scoping:** a leading comment's `@collection` / `@entry` / `@blueprint <handle>` overrides the
  template→blueprint mapping, so field completion/resolution uses the declared blueprint.

The 7 directives (enumerable, per the Stillat docs): `@name`, `@desc`, `@param`, `@entry`, `@collection`,
`@blueprint`, `@set`.

**Deferred (scope 3):** `@param` → partial-parameter completion at call sites; `@set` → Bard/Replicator
set scoping. In this cycle `@param`/`@set`/`@name`/`@desc` are highlighted + completed but not otherwise
wired.

## Components

### A1. `scope/AntlersHintParser` (pure)

`parse(commentBody: String): List<Directive>`, `Directive(name, nameSpan, value, valueSpan)` where `name`
is e.g. `"@collection"`, `nameSpan`/`valueSpan` are half-open `IntRange`-like `TextSpan(start, end)`
offsets relative to `commentBody`, `value` is the trimmed remainder of the line. A directive is a line
whose first non-whitespace token is one of the 7 `@<directive>` keywords:
`^(\s*)(@(?:name|desc|param|entry|collection|blueprint|set))\b(.*)$`. Pure / IntelliJ-free; tolerant.

### A2. Highlighting — `editor/AntlersHintAnnotator`

An `Annotator` gating on a `T_COMMENT_TEXT` leaf: runs `AntlersHintParser.parse(leaf.text)` and paints
each `Directive.nameSpan` (offset by the leaf's `textRange.startOffset`) with a new color key
**`AntlersSyntaxHighlighter.HINT`** (default `DefaultLanguageHighlighterColors.DOC_COMMENT_TAG`), via the
silent-`INFORMATION` overlay used by 4b. Registered `<annotator language="Antlers" …>`. The key is added
to `AntlersColorSettingsPage` (descriptor "Template hint" + a `<hint>` demo tag in a `{{# @name … #}}`
demo block).

### A3. Completion — directive names after `@`

In `AntlersCompletionProvider.addCompletions`, **before** the existing `classify`/NONE early-return: if
`parameters.position` is a `T_COMMENT_TEXT` leaf and the text on its line before the caret ends with
`@<prefix>` (an `@` optionally followed by word chars, with no whitespace between), offer the 7 directive
names. Each lookup is the bare name (`name`, `desc`, …) inserted after the already-typed `@`;
`result.withPrefixMatcher(<text after the last @>)` filters them. Then `return` (skip the normal
statement-completion path). A small `commentDirectivePrefix(position, caretOffset): String?` helper
encapsulates the "in a comment, after `@`" detection and is unit-testable.

### B. Blueprint scoping — `blueprint/AntlersViewHints`

`@Service`-free object, cached per-file on `PsiModificationTracker.MODIFICATION_COUNT` via
`CachedValuesManager` (same idiom as `ViewFrontMatterService`):

- `declaredNamespaces(file: PsiFile): List<BlueprintNamespace>` — finds the **leading** `AntlersComment`
  (`PsiTreeUtil.findChildOfType(file, AntlersComment::class.java)` — the first comment), parses its body
  with `AntlersHintParser`, and maps every `@collection` / `@entry` / `@blueprint <handle>` to
  `BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, handle)` (deduped, in document order). Empty if
  no leading comment or no scoping directives.

`AntlersFieldContext.namespacesFor(element)` consults it between E1 and E2:
```kotlin
val scopes = AntlersScopeResolver.scopesAt(element)
if (scopes.isNotEmpty()) return scopes.map { it.namespace }   // E1 loop scope (unchanged, wins)
val hints = AntlersViewHints.declaredNamespaces(element.containingFile)
if (hints.isNotEmpty()) return hints                          // NEW: explicit @collection/@entry/@blueprint
val page = PageBlueprintResolver.namespacesFor(element)
if (page.isNotEmpty()) return page                            // E2 page mapping (unchanged)
return null
```
So an explicit hint overrides the file-path page guess, but a loop scope still wins. Everything downstream
(`fieldsInScope`, `resolveField`, completion field offering, docs) flows through `namespacesFor`, so no
other consumer changes.

## Data flow

`{{# @collection blog #}}` (leading) → `AntlersViewHints.declaredNamespaces` → `[COLLECTION:blog]` →
`AntlersFieldContext.namespacesFor` returns it → field completion/resolution restricts to blog's blueprint
fields. Separately, the annotator colors `@collection` (and the other directives) in any comment, and
completion offers the directive names after `@`.

## Error handling / edge cases

- No hint comment / unrecognized `@word` → parser returns nothing; scoping falls through to E2/global;
  highlighting paints nothing.
- A hint directive with no value (`@collection` alone) → no namespace contributed (handle blank → skipped).
- `@blueprint <h>` is treated as a collection handle (v1 limitation; non-collection blueprints unresolved).
- The leading comment is found by PSI position; front matter (if present) precedes it harmlessly (the
  first `AntlersComment` is still the hint comment).

## Testing

- **Parser** (pure unit tests): recognizes each of the 7 directives at line-start with its value; ignores
  non-directive lines and a bare `@` / unknown `@foo`; spans are correct (`commentBody.substring`).
- **Highlight** (`AntlersHintAnnotator` test, via `doHighlighting` forced keys): `@name`/`@collection` in a
  `{{# … #}}` comment carry `HINT`; ordinary comment prose does not.
- **Completion**: `{{# @<caret> #}}` offers `name`/`collection`/… ; `{{# @col<caret> #}}` filters to
  `collection`; a normal `{{ <caret> }}` is unaffected (still tags/vars).
- **Scoping**: with a project fixture exposing blog's blueprint fields, a file whose leading comment is
  `{{# @collection blog #}}` offers/resolves blog's fields at top level (no loop, no page mapping); and
  inside a `{{ collection:other }}` loop the loop scope still wins (precedence). Mirrors the existing E2
  scope tests.
- **Color-settings page** test: add `HINT` to the expected descriptor-key set and `"hint"` to the tag map.
- Full-suite gate.

## Out of scope

- `@param` partial-parameter completion and `@set` Bard/Replicator scoping (scope 3).
- Non-collection `@blueprint` kinds (form/taxonomy) and multi-handle precedence subtleties.
- Reformatting/structure-view treatment of hint comments (the formatter already leaves comments alone).
