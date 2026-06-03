# Antlers Editor Polish II — Design

**Date:** 2026-06-03
**Branch:** `antlers-editor-polish-ii`
**Status:** Approved approach, pending spec review

Three small, independent editor/UX improvements, shipped on one branch as three tasks.

## A. Tab from a condition jumps into the block

**Goal:** After completing `{{ if ‹caret› }}{{ /if }}` and typing a condition, **Tab** jumps the caret
into the block (`{{ if test }}‹caret›{{ /if }}`) — a single jump, no parameter-slot repeating.

**Background:** `AntlersKeywordInsertHandler` deliberately arms **no** `AntlersParamSession` (a condition
is one expression, not space-separated params). So Tab does nothing special there today.

**Design:** Add a `conditionMode: Boolean = false` flag to `AntlersParamSession`. In
`AntlersKeywordInsertHandler`, for **OPENER** (`if`/`unless`) and **MID** (`elseif`) inserts, arm a
session (markers for the opening `{{` and `}}`, exactly like `AntlersTagInsertHandler`) with
`conditionMode = true`. **PLAIN** keywords (`else`/`endif`/`endunless`) arm nothing (the caret is already
past `}}`). In `AntlersParamTabHandler`, force the terminal jump when the session is condition-mode —
i.e. compute `val toBlock = session.conditionMode || before.isEmpty() || before.last() == ' '` and use
the existing empty-slot branch (collapse trailing spaces to one, move the caret to the terminal stop,
set `reachedTerminal`). The repeating-param behavior is never engaged for conditions. Tag (non-keyword)
sessions are unchanged (`conditionMode = false`).

**Edge cases:** first Tab on an empty condition (`{{ if ‹caret› }}`) → `{{ if }}‹caret›{{ /if }}` (into
the block). A second Tab at the terminal ends the session (existing `reachedTerminal` path).

## B. Documentation on hover for modifiers (and catalog tags/params)

**Goal:** Hovering a modifier shows its description **and** a link to the official docs.

**Background:** `AntlersDocumentationProvider.generateDoc` **already** produces modifier docs with a
docUrl `<a>` link (`section(...)` → `appendDocUrl`). But the provider has **no
`getCustomDocumentationElement`**, so the platform cannot resolve the hovered `T_IDENT` (modifiers,
catalog tags, and params have no PSI reference to hang documentation on) → `generateDoc` is never called
on hover. The existing `AntlersDocumentationProviderTest` calls `generateDoc` directly, so it passes
while real hover fails.

**Design:** Implement
`getCustomDocumentationElement(editor, file, contextElement, targetOffset): PsiElement?` to return the
`T_IDENT` at the cursor — `contextElement` if it is a `T_IDENT`, else `file.findElementAt(targetOffset)`
when that is a `T_IDENT`, else null. The platform then calls `generateDoc` on that ident, which already
yields the modifier/tag/param doc + official-docs link. No change to `generateDoc`. This fixes hover for
modifiers and, as a bonus, for catalog tags and parameters (all reference-less, catalog-backed).

## C. Color logic keywords distinctly from tags

**Goal:** `if`/`else`/`unless`/… (control flow) and `collection`/`nav`/… (tags) no longer share a color.

**Background:** Both `AntlersSyntaxHighlighter.TAG` and `.KEYWORD` map to
`DefaultLanguageHighlighterColors.KEYWORD`.

**Design:** Change **`TAG`** to `DefaultLanguageHighlighterColors.FUNCTION_CALL` (tags read as
directives/calls); `KEYWORD` (logic) stays `KEYWORD` (control flow). The `TextAttributesKey` external
names (`ANTLERS_TAG`, `ANTLERS_KEYWORD`) are unchanged, so the color settings page and all existing
semantic-highlight tests (which assert the *keys*, not the base colors) are unaffected; users can still
re-customize either key. One-line change.

## Architecture

All additive/local: a flag + arming for A; one override for B; one base-color swap for C. No
parser/grammar/catalog change. A reuses the existing `AntlersParamSession`/`AntlersParamTabHandler`;
B reuses the existing `generateDoc`.

## Testing

- **A** (`AntlersConditionTabTest`, new): complete `if`, `type("test")`,
  `performEditorAction(ACTION_EDITOR_TAB)` → assert `document.text == "{{ if test }}{{ /if }}"` and the
  caret is at `"{{ if test }}".length` (in the block); a second Tab clears the session. Also: empty
  condition (`if` then immediate Tab) → caret in block. `elseif` (inside an open if) → Tab into its
  region. (Use `editor.document.text`, not `file.text`.)
- **B** (extend `AntlersDocumentationProviderTest`): assert
  `AntlersDocumentationProvider().getCustomDocumentationElement(editor, file, ctx, offset)` returns the
  modifier `T_IDENT` for `{{ title | up<caret>per }}`, and that `generateDoc` on it contains both
  `upper` and a `statamic.dev` link (the docUrl). A null case for a non-ident position.
- **C**: no behavioral unit test (a default base-color mapping isn't meaningfully unit-testable); the
  existing semantic-highlight tests still pass (keys unchanged). Optionally assert `TAG` and `KEYWORD`
  are distinct `TextAttributesKey`s (already true).
- Full-suite gate.

## Out of scope

- Tab-to-block for a hand-typed `{{ if x }}` with no completion session (A is completion-flow only).
- Hover docs for scoped fields beyond what `generateDoc` already returns.
- A dedicated new color *tone* for tags (reusing the platform `FUNCTION_CALL` default keeps it
  theme-friendly).
