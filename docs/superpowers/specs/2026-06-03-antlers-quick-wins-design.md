# Antlers Quick Wins — Design

**Date:** 2026-06-03
**Branch:** `antlers-quick-wins`
**Status:** Approved approach, pending spec review

Three small, independent improvements from the feedback batch (items #5, #7, #10).

## A. Add missing global system variables (#5 + audit)

**Goal:** `template_content` (and other globally-injected Statamic cascade variables) appear in `{{ }}`
variable completion and hover.

**Background:** `blueprint/SystemVariables.ALL` has 26 entries but is missing several globally-available
cascade variables (verified against the official Statamic variables docs). It already has `content`,
`now`, `current_url`, `current_uri`, `csrf_token`, `site`, `locale`, `template`, `layout`, etc.

**Design:** Add these `SystemVariable(name, description)` entries to `SystemVariables.ALL` (global,
commonly-used; asset-context-only vars like `width`/`focus_css`/`size_*` are intentionally **excluded**
— they belong to asset member completion, not the global list):

| name | description |
|---|---|
| `template_content` | The rendered template content (output inside a layout). |
| `current_template` | The template actually used to render the page. |
| `current_layout` | The layout actually used to render the page. |
| `current_user` | The authenticated user (null if logged out). |
| `logged_in` | Whether the visitor is authenticated. |
| `homepage` | The site's homepage URL. |
| `is_homepage` | Whether the current URL is the homepage. |
| `last_segment` | The final segment of the current URL. |
| `segment_1` | The first URL segment (`segment_2`, `segment_3`, … follow the same pattern). |
| `csrf_field` | A hidden input field containing the CSRF token. |
| `config` | Access Statamic/Laravel configuration values. |
| `get` | Query-string variables. |
| `post` | Submitted POST data. |
| `old` | Old (previous-request) input, for re-populating forms after validation. |
| `response_code` | The HTTP response code (200 or 404). |
| `live_preview` | Whether the page is rendering in Live Preview. |
| `edit_url` | Control-Panel edit URL for the current content. |
| `sites` | All configured sites. |
| `is_entry` | Whether the current content is an entry. |

(`template`/`layout` stay as-is; `current_template`/`current_layout` are the resolved-name companions.)

## B. Plugin display name → "Antlers" (#10)

**Goal:** Distinguish from the existing "Antlers Language Support" plugin.

**Design:** In `META-INF/plugin.xml`, change `<name>intellij-antlers</name>` to `<name>Antlers</name>`.
The `<id>` (`com.github.balotias.intellijantlers`) is unchanged. The README `<!-- Plugin description -->`
block (marketplace copy) already reads well and needs no change.

## C. Distinct color for parameter names vs variables (#7)

**Goal:** A parameter name (`from` in `from="x"`) no longer looks identical to a variable (`title`).

**Background:** `AntlersSemanticHighlightAnnotator` paints tag heads (TAG), condition keywords (KEYWORD),
modifier names (MODIFIER), and closing names; **parameter names and variables both fall through to the
lexer's `IDENTIFIER`** color, so they're indistinguishable.

**Design:**
- Add `AntlersSyntaxHighlighter.PARAMETER = TextAttributesKey.createTextAttributesKey("ANTLERS_PARAMETER",
  DefaultLanguageHighlighterColors.PARAMETER)`. (If `PARAMETER` does not resolve in this SDK, use
  `INSTANCE_FIELD` — both are reliably colored and distinct from `IDENTIFIER`/`METADATA`.)
- In `AntlersSemanticHighlightAnnotator`, add a case `is AntlersParameterMixin ->` that paints the
  parameter-name `T_IDENT` (the `firstIdent`) with `PARAMETER`. This covers static (`from="x"`) and
  bound (`:src="x"`) params — the first `T_IDENT` is the name in both.
- Add `AttributesDescriptor("Parameter name", AntlersSyntaxHighlighter.PARAMETER)` to
  `AntlersColorSettingsPage`, and a `"param"` → `PARAMETER` entry in its demo-text additional-highlight
  map (so the settings preview shows it). Variables keep `IDENTIFIER`.

## Architecture

All additive/local: data entries (A), one metadata line (B), one new attribute key + one annotator
case + one settings descriptor (C). No parser/grammar/catalog-structure change.

## Testing

- **A** (extend `AntlersVariableCompletionTest` or `AntlersCompletionTest`): completing `{{ <caret> }}`
  offers `template_content` (and spot-check one more, e.g. `current_user`). A `SystemVariables` unit
  assertion that the new names are present.
- **B**: assert `META-INF/plugin.xml` `<name>` equals `Antlers` (a small resource-text test), or rely on
  `verifyPlugin`; no behavioral test.
- **C** (extend `AntlersSemanticHighlightTest`): a parameter name (`from` in `{{ collection from="x" }}`)
  is highlighted with `ANTLERS_PARAMETER`; a plain variable (`{{ title }}`) is **not** (no forced
  attribute / not PARAMETER).
- Full-suite gate.

## Out of scope

- Asset-context member variables (`width`, `focus_css`, `size_*`, …) — those belong to asset member
  completion, a separate concern.
- Re-coloring variables themselves (they stay `IDENTIFIER`).
