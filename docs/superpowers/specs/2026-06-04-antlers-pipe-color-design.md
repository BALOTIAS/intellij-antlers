# Antlers Modifier-Pipe Color — Design

**Date:** 2026-06-04
**Branch:** `antlers-pipe-color`
**Status:** Approved approach, pending spec review
**Source:** user feedback — the modifier pipe `|` is colored green (Operator) while a comma is white
(uncolored); the two separators should be consistent. The pipe should default to plain text (white).

## Background

`AntlersSyntaxHighlighter.getTokenHighlights` colors `T_OP, T_PIPE, T_EQUALS, T_ARROW, T_COLON, T_SLASH,
T_DOT` as `OPERATOR`, while `T_COMMA`/`T_SEMICOLON`/parens/brackets fall through to no color (default
text). So `|` reads green and `,` reads white — an inconsistency for two separators. The 4b
`AntlersStringInterpolationAnnotator.colorFor` likewise maps `T_PIPE` to `OPERATOR`.

## Change — a dedicated, themeable "Modifier pipe" color (default plain text)

Give `|` its own color key, defaulting to plain text so it renders white (like a comma) out of the box,
while remaining independently themeable for anyone who wants a colored pipe.

### Components

1. **`AntlersSyntaxHighlighter`**: add
   `val PIPE = TextAttributesKey.createTextAttributesKey("ANTLERS_PIPE", HighlighterColors.TEXT)` (import
   `com.intellij.openapi.editor.HighlighterColors`) + `private val PIPE_KEYS = arrayOf(PIPE)`. In
   `getTokenHighlights`, **remove `T_PIPE` from the `OPERATOR` branch** and add `AntlersTypes.T_PIPE ->
   PIPE_KEYS`. (`OPERATOR` keeps `T_OP, T_EQUALS, T_ARROW, T_COLON, T_SLASH, T_DOT`.)
2. **`editor/AntlersStringInterpolationAnnotator.colorFor`**: split `T_PIPE` out of the `OPERATOR`
   alternation into its own branch returning `AntlersSyntaxHighlighter.PIPE`, so the pipe is consistent in
   string interpolation too. (The `T_IDENT`-after-`T_PIPE` → `MODIFIER` rule is unchanged — `prev` still
   tracks `T_PIPE`.)
3. **`highlighting/AntlersColorSettingsPage`**: add `"pipe" to AntlersSyntaxHighlighter.PIPE` to the tag
   map, `AttributesDescriptor("Modifier pipe", AntlersSyntaxHighlighter.PIPE)` to the descriptors, and tag
   the demo pipe: the `{{ title | <mod>upper</mod> }}` line becomes
   `{{ title <pipe>|</pipe> <mod>upper</mod> }}`.

`HighlighterColors.TEXT` is the editor's default foreground, so the pipe is white by default, matching the
comma; a user can recolor "Modifier pipe" in *Settings → Editor → Color Scheme → Antlers*.

## Testing

- **Base highlighter** (`AntlersSyntaxHighlighter` unit test): `getTokenHighlights(T_PIPE)` contains `PIPE`
  (and not `OPERATOR`); `getTokenHighlights(T_COLON)` still contains `OPERATOR` (unchanged for the other
  operators).
- **Interpolation** (`AntlersStringInterpolationHighlightTest`): update `testInterpolatedPipeIsOperator` →
  assert the interpolated `|` is `PIPE` (rename to `testInterpolatedPipeUsesPipeColor`); the
  `replace`/`upper`-after-pipe → `MODIFIER` assertions stay green.
- **Color settings page** (`AntlersColorSettingsPageTest`): add `PIPE` to the expected descriptor-key set
  and `"pipe"` to the expected tag-map keys.
- Full-suite gate.

## Out of scope

- Reclassifying the other operator/separator tokens (`:` `.` `=` vs `,` `;` parens) — only the pipe was
  flagged; leave the rest as-is.
- Any non-highlighting behavior (the pipe still lexes/parses as `T_PIPE`; modifiers are unaffected).
