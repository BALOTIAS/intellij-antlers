# Antlers Editor UX Fixes — Design

**Date:** 2026-06-02
**Branch:** `antlers-editor-ux-fixes`
**Status:** Approved scope (user chose "small fixes now, formatter next"), pending spec review

## Goal

Fix three user-reported live-editor defects (plus two small nits) that the PSI-level unit tests never
exercised. **Out of scope (separate follow-up): real Reformat block-indentation (#3)** — that needs a
`TemplateLanguageFormattingModelBuilder` and is its own sub-project.

## Fixes

### Fix 1 — Live templates double the delimiters inside `{{ }}`

**Symptom:** picking `if` while inside `{{ <caret> }}` yields `{{ {{ if }}{{ /if }} }}`.
**Root cause:** every bundled live template embeds its own `{{ }}` (`liveTemplates/Antlers.xml`), but
`AntlersTemplateContextType.isInContext` returns `true` for the whole `.antlers.html` file, so the
templates fire *inside* existing braces. They are meant for HTML/text context only.

**Fix:** `isInContext` returns true only when the caret is **outside** any Antlers `{{ }}` construct.
```kotlin
override fun isInContext(context: TemplateActionContext): Boolean {
    val file = context.file
    if (file.fileType !is AntlersFileType) return false
    val el = file.findElementAt(context.startOffset) ?: return true   // empty/EOF → outside
    return PsiTreeUtil.getParentOfType(
        el, AntlersStatement::class.java, AntlersComment::class.java,
        AntlersNoparseBlock::class.java, AntlersPhpBlock::class.java
    ) == null
}
```
Because the platform unions `isInContext` over the base (Antlers) PSI file, the check is evaluated on
the Antlers tree: an HTML-region caret sits in a `T_OUTER_HTML` leaf (no Antlers-construct parent →
true), an in-braces caret sits under an `AntlersStatement` (→ false).

**Test impact:** the existing `AntlersLiveTemplatesTest.testInContextForAntlersFile` (caret at offset
0 of `{{ x }}`) asserted `true`; it now becomes the new semantics — update it to assert templates are
offered in an HTML region and *not* inside `{{ }}`.

### Fix 2 — Pair-tag completion drops the caret in the block, not the param slot

**Symptom:** selecting `collection` lands the caret inside the loop body, when you want to type a
handle/param in the opening tag.
**Root cause:** `AntlersTagInsertHandler` (pair branch) appends `"\n    \n{{ /name }}"` and moves the
caret onto an indented body line — and hardcodes a 4-space indent that ignores context (and has no
reformatter to fix it).

**Fix:** for pair tags, produce a one-line `{{ name <caret> }}{{ /name }}` with the caret in the
**opening-tag parameter slot** (one space after the name, one space before `}}`). No forced newlines
or hardcoded indent — the user presses Enter to expand to a block when they want one; real block
indentation is the deferred #3. Single (non-pair) tag behavior is unchanged.

### Fix 4 — Tag names aren't colored distinctly

**Symptom:** tag names look like plain text.
**Root cause:** `AntlersSyntaxHighlighter` maps `T_IDENT → IDENTIFIER` unconditionally; `T_IDENT` is
*every* identifier (tags, fields, params, modifiers, condition keywords), so a lexer highlighter
can't tell them apart. Distinguishing requires **semantic** highlighting over the PSI.

**Fix:** a new `editor/AntlersSemanticHighlightAnnotator` (`Annotator`, registered
`<annotator language="Antlers">` alongside the balance annotator) that paints, via
`holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(leaf).textAttributes(KEY).create()`:
- the **tag-head** ident of a name path whose head is a known catalog tag → `ANTLERS_TAG`,
- the **closing-tag** name ident (`{{ /collection }}`) → `ANTLERS_TAG`,
- the **condition keyword** ident (`if`/`unless`/`else`/`elseif`) → `ANTLERS_KEYWORD`,
- the **modifier** name ident (after `|`) → `ANTLERS_MODIFIER`.

Variables/fields/params keep the plain `IDENTIFIER` color (so blueprint variables still read as data,
tags/keywords/modifiers stand out).

**New color keys** (added to `AntlersSyntaxHighlighter`'s companion, surfaced in the ColorSettingsPage):
- `ANTLERS_TAG` ← `DefaultLanguageHighlighterColors.KEYWORD`
- `ANTLERS_KEYWORD` ← `DefaultLanguageHighlighterColors.KEYWORD`
- `ANTLERS_MODIFIER` ← `DefaultLanguageHighlighterColors.INSTANCE_METHOD`

(`ANTLERS_TAG` and `ANTLERS_KEYWORD` share the KEYWORD default but are separate keys, so a user can
recolor tags vs. conditionals independently.) The ColorSettingsPage gains three descriptors and the
demo text already contains a tag (`collection`), a condition (`if`), and a modifier (`upper`).

### Nit A — `coll` live-template default is invalid Antlers

`{{ collection:$HANDLE$ }}` with default `"blog"` (quoted) produces `{{ collection:"blog" }}`; the
colon form takes a **bare** handle. Change the default to `blog` (unquoted).

### Nit B — (noted, NOT changing) modifier args use `()`

`ModifierInsertHandler` inserts `truncate()`; Antlers idiomatically uses `| truncate:50`. The `()`
form is valid; left as-is (the user didn't flag it, and the colon form would need its own insert UX).

## Architecture notes

- All fixes are additive/local: one context method, one insert handler, one new annotator + 3 color
  keys + ColorSettingsPage descriptors, one XML default. No new dependency; no parser/grammar change.
- The new annotator is separate from `AntlersBalanceAnnotator` (single responsibility: one diagnoses,
  one paints).

## Testing (`BasePlatformTestCase`)

- **Fix 1** (`AntlersLiveTemplatesTest`, updated): `isInContext` true at an HTML-region caret in a
  `.antlers.html` file; false inside `{{ <caret> }}`; false in a `.txt` file.
- **Fix 2** (`AntlersTagInsertHandler` via completion, new test): completing a pair tag yields
  `{{ name <caret> }}{{ /name }}` with the caret between `name ` and `}}` (assert the resulting text +
  caret offset); a single tag is unchanged.
- **Fix 4** (`AntlersSemanticHighlightAnnotator`, new test): `myFixture.doHighlighting()` (or a direct
  annotator invocation) yields an info with `forcedTextAttributesKey == ANTLERS_TAG` over `collection`
  in `{{ collection:blog }}`, `ANTLERS_KEYWORD` over `if` in `{{ if x }}`, `ANTLERS_MODIFIER` over
  `upper` in `{{ title | upper }}`, and NO such info over a plain variable `{{ title }}`.
- **Nit A**: `liveTemplates/Antlers.xml` `coll` default is `blog` (resource text assertion).
- Full suite stays green (the highlighter/ColorSettingsPage keys change is additive).

## Out of scope

- **#3 real Reformat indentation** (`TemplateLanguageFormattingModelBuilder`) — next sub-project.
- Changing single-tag insert behavior.
- Colon-form modifier-argument insert UX.
