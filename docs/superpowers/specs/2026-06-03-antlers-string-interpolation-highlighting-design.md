# Antlers String-Interpolation Highlighting — Design

**Date:** 2026-06-03
**Branch:** `antlers-string-interpolation-highlighting`
**Status:** Approved approach, pending spec review
**Feedback items:** #3 / #11 (sub-project 4b)

## Goal

Highlight Antlers `{ … }` interpolation **inside string literals** as real Antlers, instead of one
flat `STRING` color. Reported cases:

- `{{ "object-position: {logo:focus_css}" }}` — `{logo:focus_css}` should not be the same color as the
  surrounding `object-position:` string text.
- `{{ :class="{['flex flex-col gap-md', view:class] | classes}" }}` — the array, the `view:class`
  path, the `|`, and the `classes` modifier should be colored like a normal Antlers expression.

Scope decided with the user: **(b) full inner highlighting** — color each piece inside the `{ … }`
(identifiers, operators, brackets, strings, numbers, and the modifier name). NOT (c): no
completion / go-to-definition / reference resolution *inside* interpolations (that stays out of scope —
it would require reworking the JFlex lexer into brace-tracking sub-states plus grammar + lexer + parser
regen).

## Background (root cause, verified)

`AntlersLexer.flex:34`:
```
STRING=\"([^\"\\]|\\.)*\"|'([^'\\]|\\.)*'
```
The `STRING` rule greedily matches the whole quoted span as **one** `T_STRING` token (only in the
`<EXPR>` state, line 56). `AntlersSyntaxHighlighter.getTokenHighlights` (`:45`) maps `T_STRING` →
`STRING_KEYS`, so the entire literal — interpolation included — is painted uniformly. There is no token
boundary at the `{ … }`, so nothing else can color it. This is highlighting only; the parser already
treats the literal as one `T_STRING` and parses cleanly.

## Approach

A new **annotator** that overlays Antlers colors on the `{ … }` sub-spans of each `T_STRING` leaf.
Annotators run *after* lexer highlighting and override specific ranges (exactly how
`AntlersSemanticHighlightAnnotator` repaints a `T_IDENT` from `IDENTIFIER` to `KEYWORD`/`MODIFIER`), so
the interpolation portions get re-colored on top of the flat `STRING` base while the surrounding string
text keeps `STRING`.

**No lexer / parser / grammar / regen changes. No new color keys.** Every `T_STRING` already lives
inside a `{{ }}` expression (the lexer emits `T_STRING` only in `<EXPR>`), so no host gating is needed,
and the interpolation reuses the existing Antlers color keys so it themes consistently.

## Components

### 1. `AntlersStringInterpolationAnnotator` (new)

`src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationAnnotator.kt`,
registered in `plugin.xml` alongside the other two annotators:
```xml
<annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersStringInterpolationAnnotator"/>
```

`annotate(element, holder)`:
- Act only when `element` is a leaf whose `node.elementType == AntlersTypes.T_STRING`. Otherwise return.
- Run the **span scanner** (below) over the leaf's text. For each span, paint via the same
  silent-annotation helper the semantic annotator uses:
  `holder.newSilentAnnotation(INFORMATION).range(absRange).textAttributes(key).create()`, where
  `absRange = TextRange` offset by the leaf's `textRange.startOffset`.

### 2. Span scanner

Pure function over the leaf's text → `List<Span>`, `Span(openBrace, contentStart, contentEnd, closeBrace)`
(offsets relative to the string text; `contentStart == openBrace+1`, `contentEnd == closeBrace`).

Single left-to-right pass:
- Track a backslash-escape flag: after a `\`, the next char is literal (so `\{` and `\}` are not
  interpolation delimiters). This mirrors the lexer's `\\.` escape in the `STRING` rule.
- Outside interpolation: a non-escaped `{` opens a span (record `openBrace`, depth = 1).
- Inside interpolation: track a single-quote inner-string flag (a `'`-delimited string), and while
  inside it ignore braces — so `{ foo:'}' }` finds the correct closing `}`. (Double quotes can't appear
  inside, since the first one would close the outer `"…"` literal.) Outside the inner string, `{`
  increments depth and `}` decrements; when depth returns to 0, close the span (`closeBrace` = this `}`).
- An opener with no matching `}` before end-of-text is discarded (left as plain string — not painted).
- An empty span (`contentStart == contentEnd`, i.e. `{}` or `{ }` with only whitespace) still paints the
  braces but contributes no inner tokens.

### 3. Inner sub-lexer + coloring

For each span:
- Paint the `{` (range `[openBrace, openBrace+1)`) and `}` (range `[closeBrace, closeBrace+1)`) with
  `BRACES`.
- Re-lex the inner content `[contentStart, contentEnd)` with a fresh `_AntlersLexer` wrapped in a
  `FlexAdapter`, started in the **`EXPR`** state:
  `adapter.start(leafText, contentStart, contentEnd, _AntlersLexer.EXPR)`. Iterate
  `tokenType`/`tokenStart`/`tokenEnd`/`advance()` to walk tokens. Starting in `EXPR` is required so the
  content lexes as an Antlers expression (identifiers, `:`/`.`/`|`, `'…'` strings, brackets) rather than
  outer HTML.
- Map each token to a color and paint it (skip tokens that map to none). Track the previous
  non-whitespace token type so a `T_IDENT` directly after a `T_PIPE` is a modifier:

  | token | color |
  |---|---|
  | `T_IDENT` (prev non-WS == `T_PIPE`) | `MODIFIER` |
  | `T_IDENT`, `T_DOLLAR` | `IDENTIFIER` |
  | `T_STRING` | `STRING` |
  | `T_NUMBER` | `NUMBER` |
  | `T_PIPE`, `T_COLON`, `T_DOT`, `T_OP`, `T_EQUALS`, `T_ARROW`, `T_SLASH`, `T_COMMA`, `T_SEMICOLON` | `OPERATOR` |
  | `T_LBRACE`, `T_RBRACE`, `T_LBRACKET`, `T_RBRACKET`, `T_LPAREN`, `T_RPAREN`, `T_AT` | `BRACES` |
  | `T_WS`, `BAD_CHARACTER`, anything else | (none — leave `STRING` base) |

  This matches `AntlersSyntaxHighlighter`'s token→color choices for the shared tokens (identifier /
  string / number / operator), adds the pipe→`MODIFIER` rule (mirroring the semantic annotator), and
  paints structural punctuation (`[ ] ( ) { }`) as `BRACES` so it doesn't read as string text.

## Data flow

`T_STRING` leaf → span scanner → per span: paint braces `BRACES` + sub-lex inner in `EXPR` state →
per inner token: `colorFor(tokenType, prevType)` → `newSilentAnnotation(INFORMATION).range(abs).textAttributes(key)`.

## Error handling / edge cases

- No `{` in the string → annotator does nothing.
- Unmatched `{` (no closing `}`) → that span is discarded; the string stays `STRING`-colored. No
  partial/garbage coloring.
- `\{` / `\}` → escaped, not delimiters.
- `}` inside a single-quoted inner string → ignored by the scanner (does not close the span).
- Empty `{}` → braces painted, no inner tokens.
- `BAD_CHARACTER` from the sub-lexer → no color (leaves base).
- Performance: runs only on `T_STRING` leaves; one O(n) scan + one O(n) sub-lex of the string. Trivial.

## Testing

New `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt`,
mirroring `AntlersSemanticHighlightTest`'s helpers (`myFixture.doHighlighting()` →
`firstOrNull { it.text == token && it.forcedTextAttributesKey != null }?.forcedTextAttributesKey`, and a
`coloredCount` variant). Hosts use a bare string in an expression — `{{ "…{…}…" }}` — which parses as
`tail_+` with one `T_STRING` (zero errors).

- **Reported #11** — `{{ "object-position: {logo:focus_css}" }}`: `logo` → `IDENTIFIER`,
  `focus_css` → `IDENTIFIER`, the inner `:` → `OPERATOR`, `{` and `}` → `BRACES`.
- **Modifier** — `{{ "{title | upper}" }}`: `upper` → `MODIFIER`, `|` → `OPERATOR`, `title` →
  `IDENTIFIER`.
- **Reported #3 (array + path + modifier)** — `{{ "{['a', view:class] | classes}" }}`: `classes` →
  `MODIFIER`, `[` and `]` → `BRACES`, `view` → `IDENTIFIER`, `'a'` → `STRING`, the inner `,` →
  `OPERATOR`.
- **Surrounding text stays string** — in `{{ "object-position: {logo:focus_css}" }}`, the word
  `object` (string text outside the braces) has **no** forced annotation (`keyOver(...) == null`), so it
  keeps the lexer `STRING` color.
- **Plain string untouched** — `{{ "hello world" }}`: `hello` → no forced annotation.
- **Two spans** — `{{ "a {one} b {two} c" }}`: both `one` and `two` → `IDENTIFIER` (two interpolations
  in one string are each colored).
- **Escaped brace** — `{{ "a \{not} b" }}`: `not` → no forced annotation (the `\{` is escaped, so there
  is no interpolation).
- Full-suite gate (`./gradlew --rerun-tasks test`, then the failures/errors grep must be empty) to
  confirm no regression in parsing / existing highlight / balance tests.

## Out of scope

- Completion / navigation / reference resolution **inside** interpolations (scope (c)) — needs a lexer
  rework (brace-tracking sub-states) + grammar + lexer & parser regen.
- Interpolation in non-string contexts, or `{{ }}`-style (double-brace) nesting inside a string.
- Structured PSI for the interpolated expression (this is highlighting only; the literal remains one
  `T_STRING` to the parser).
