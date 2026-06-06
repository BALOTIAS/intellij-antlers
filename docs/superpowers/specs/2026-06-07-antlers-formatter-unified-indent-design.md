# Antlers formatter — unified absolute-indent pass

**Date:** 2026-06-07
**Status:** Approved (design)

## Problem

Reformat Code mis-indents templates that nest HTML and Antlers within each other. Confirmed minimally
and idempotently (current behavior):

```
input:  {{ if a }} / {{ if b }} / <div> / <span>x</span> / </div> / {{ /if }} / {{ /if }}
actual: if[0] if[4] <div>[12] <span>[16] </div>[12] /if[4] /if[0]      (should be <div>[8] <span>[12])

input:  {{ collection }} <div> {{ if a }} <span>x</span> {{ /if }} </div> {{ /collection }}
actual: {{ if a }}[4] <span>[16]                                       (should be {{ if }}[8] <span>[12])
```

On a realistic Peak template, Antlers `{{ }}` lines sit under-indented (e.g. `{{ field:field }}` at col 12
inside `<template><div>` when it should be 20).

### Root cause (architectural)

Indentation is computed by two uncoordinated passes that don't compose into the true nesting depth:

- The platform HTML formatter (`AntlersHtmlFormattingModelBuilder`) indents HTML by HTML nesting only;
  the `{{ }}` regions are opaque blocks contributing zero indent.
- `AntlersBlockIndentProcessor` then bolts on Antlers-pair depth — **absolutely** for `{{ }}` lines (so
  they count only enclosing *Antlers pairs*, never the HTML elements they're inside → under-indented) and
  **additively** for HTML lines (HTML-formatter indent + Antlers depth → mis-counted).

Neither pass ever sees the real answer: `indent = (enclosing HTML elements) + (enclosing Antlers
pairs/conditions) + (enclosing template-named tags) + (multi-line continuation)`, counted together.

Note: an earlier-observed **non-idempotency** was a downstream effect of parse errors (`{{ %form:fields }}`,
slash-path closers) that have since been fixed; it no longer reproduces. The remaining bug is purely the
wrong/inconsistent columns above.

## Goal

Replace the two-model approach with a single post-format pass that computes one combined depth per line
and rewrites the line's leading whitespace absolutely. Correct *and* idempotent by construction. Preserve
the existing contract: indentation-only (never reflow or break lines), Prettier-safe, honors the Code
Style → Antlers indent size/tabs and the "Reformat Antlers code" off switch, tolerant (never throws).

## Confirmed decisions

- **All HTML tags add a level** — inline (`<span>`, `<a>`) and block alike. We only adjust lines the user
  already broke; no block-vs-inline list.
- **Preserve all opaque/whitespace-significant regions** — never touch interior leading whitespace inside
  `<pre>`/`<textarea>`, multi-line Antlers strings, `{{# comments #}}`, noparse blocks, and PHP blocks.
  Their outer boundary lines still get indented.
- **Rewrite the ~50 formatter tests** to the corrected columns; drop the "known-limitation" tests that
  encoded the bugs.

## Architecture

One post-format pass owns all indentation. The platform HTML formatter is removed from the indentation
path. Pipeline on Reformat Code: **spacing pass** (unchanged) → **`AntlersIndentProcessor`** (new).

### Depth model

For each physical line, depth = sum of four independent **region contributors**. A region marks `+1` on
the lines *strictly inside* its open/close boundaries; the boundary lines themselves stay at the enclosing
level (so an opener/closer sits at its parent's depth, its body one deeper). Contributors:

1. **HTML elements** — from the HTML data PSI tree (`viewProvider.getPsi(HTMLLanguage.INSTANCE)`). Each
   `XmlTag` contributes `+1` to its interior lines `(startLine+1 .. endLine-1)`. The parser yields correct
   structure: void (`<br>`) / self-closing / optional-close (`<li>`, `<p>`) tags have no multi-line
   interior so contribute nothing; multi-line attribute lists and inline tags are handled naturally.
   Interpolated-name tags `<{{ … }}>` are **not** valid `XmlTag`s, so they're not counted here (they come
   from contributor 3 — no double counting).
2. **Antlers pairs & conditions** — from `AntlersNestingTreeBuilder`: each `NestingNode` contributes `+1`
   to its body lines `(openerEndLine+1 .. closerStartLine-1)`, where `openerEndLine` is the line of the
   opener statement's closing `}}` (so a **multi-line opener**'s own param lines are NOT counted as body —
   they belong exclusively to contributor 4). `{{ else }}` / `{{ elseif }}` lines render at the `{{ if }}`
   level (one less than the body), as today.
3. **Template-named tags** — `<{{ … }}> … </{{ … }}>` from `AntlersTemplateTags.elements`: interior lines
   `+1` (attribute lines between `<{{` and its `>` and body lines between `>` and `</{{`), excluding the
   `>` boundary line, as today.
4. **Multi-line `{{ }}` params** — for a statement spanning multiple lines, each line in
   `(startLine+1 .. endLine)` gets `+ (1 + openBracketsBefore − closesFirst)` (bracket nesting: `( [ {`
   open deeper, a line beginning with `) ] }` one shallower), **except** a line whose first token is the
   closing `}}` gets `+0` (it sits back at the opener level). This mirrors today's
   `AntlersMultilineTagIndentProcessor`. These param lines are owned solely by this contributor —
   contributor 2 excludes them (see above) so they are never double-counted.

`depth[line]` = sum over all four. Indent string = `unit.repeat(depth[line])` where
`unit = if (USE_TAB_CHARACTER) "\t" else " ".repeat(INDENT_SIZE)` from `CodeStyle.getIndentOptions`.

### Preserve set

A `preserve[line]` flag; preserved lines are never edited (their interior whitespace is significant or
opaque). Marked for the **interior** lines of:
- `<pre>` and `<textarea>` elements (from the HTML tree).
- Multi-line Antlers strings (`T_STRING` spanning lines) — a line whose start offset is strictly inside a
  T_STRING range.
- `{{# … #}}` comments, noparse blocks, and PHP blocks (`{{$ $}}`, `{{? ?}}`, `<?php ?>`, `<?= ?>`).

Boundary lines of these regions are still indented normally.

### Components

- **`AntlersHtmlFormattingModelBuilder`** (edit) — return the indent no-op block model *always* (today it
  only does so when reformatting is disabled), so the platform HTML formatter never indents. Keep the
  registration so the Reformat action stays available and the post-format processors run.
- **`AntlersIndentProcessor`** (new `PostFormatProcessor`) — the unified pass. Commits the document
  (after the spacing pass), reads the Antlers + HTML PSI, builds `depth[]` and `preserve[]`, applies the
  absolute indent to in-range, non-preserved, non-blank lines (blank lines → stripped). **Replaces**
  `AntlersBlockIndentProcessor` and `AntlersMultilineTagIndentProcessor`, which are **deleted** and their
  `plugin.xml` `postFormatProcessor` registrations removed; the new one is registered after the spacing
  processor.
- **`AntlersHtmlNesting`** (new helper) — given the `Document` and the HTML data `PsiFile`, returns
  `htmlDepth: IntArray` and a `preserveLines: Set<Int>` (the `<pre>`/`<textarea>` interiors). Keeps the
  XML/PSI specifics in one focused, testable unit. Walks all `XmlTag`s via `PsiTreeUtil`.
- Reused unchanged: `AntlersNestingTreeBuilder`, `AntlersTemplateTags`, `AntlersSpacingPostFormatProcessor`,
  `AntlersFormatterSettings`, `AntlersLanguageCodeStyleSettingsProvider`.

### Data flow (per Reformat Code)

```
spacing pass edits document (may be uncommitted)
  → AntlersIndentProcessor.processText:
      gate on AntlersFormatterSettings.reformatEnabled
      PsiDocumentManager.commitDocument(document)
      antlers = viewProvider.getPsi(Antlers); html = viewProvider.getPsi(HTML)
      depth[] = htmlDepth(html) + antlersDepth(nestingTree) + templateDepth(templateTags) + multilineDepth(statements)
      preserve[] = pre/textarea interiors ∪ multi-line T_STRING interiors ∪ comment/noparse/php interiors
      for each line in document:
        skip if line-start outside rangeToReformat, or preserve[line]
        blank line → strip to ""
        else replace leading whitespace with unit*depth[line]
      return adjusted TextRange
```

Edits are collected then applied end-to-start; the returned range is widened by the net delta (as the
current processors do).

## Edge cases / robustness

- **Pure-HTML `.antlers.html`** (no Antlers): contributors 2–4 are empty; HTML depth alone indents it
  correctly — replacing what the platform formatter did. Must be covered by tests.
- **Unparseable / partial HTML** (interpolated tag names, stray `<`): the HTML tree yields error elements,
  not `XmlTag`s, so those regions simply don't contribute HTML depth; template-named tags are handled by
  contributor 3. No crash.
- **Default project / no PSI**: `getPsi(HTML)` may be null → treat `htmlDepth` as all-zero (Antlers-only
  indent), never throw.
- **Reformat disabled**: pass returns `rangeToReformat` unchanged (same gate as today).
- **Lines inside strings / opaque blocks**: never reindented (preserve set).
- **`}}` / closer / `</tag>` lines**: sit at the enclosing depth because each construct only marks its
  *interior*; verified by boundary tests.

## Testing

Rewrite `formatter/*` tests to the corrected expectations and add coverage:

- **Mixed nesting** (the reproductions): `{{if}}{{if}}<div><span>` → `<div>[2u] <span>[3u]`;
  `{{collection}}<div>{{if}}<span>` → `{{if}}[2u] <span>[3u]`; the Peak-shaped case with
  `{{ field:field }}` correctly at depth.
- **Pure HTML**: `<div><span>x</span></div>` indents by HTML nesting alone.
- **Inline tags**: content inside `<a>`/`<span>` on its own line gets `+1`.
- **`<pre>`/`<textarea>` preservation**: interior whitespace untouched.
- **Multi-line `{{ }}` params** + bracket nesting; `}}` at opener level.
- **Conditions**: `{{ if }}`/`{{ else }}`/`{{ elseif }}`/`{{ /if }}` dedent correctly.
- **Template-named tags** `<{{ as }}>` … `</{{ as }}>` with attribute lines.
- **Opaque blocks** ({{# #}}, noparse, PHP) interiors preserved; boundaries indented.
- **Multi-line Antlers strings** not reindented.
- **Idempotency**: `reformat(x) == reformat(reformat(x))` for every case above.
- **Opt-out**: with "Reformat Antlers code" off, files are untouched.
- **Tolerance**: unclosed tags / stray closers / malformed input don't crash and don't runaway-indent.

## Files touched

- New: `formatter/AntlersIndentProcessor.kt`, `formatter/AntlersHtmlNesting.kt`
- Edit: `formatter/AntlersHtmlFormattingModelBuilder.kt` (always no-op indent)
- Edit: `META-INF/plugin.xml` (drop the two old postFormatProcessor regs, add the new one)
- Delete: `formatter/AntlersBlockIndentProcessor.kt`, `formatter/AntlersMultilineTagIndentProcessor.kt`
- Tests: rewrite `formatter/AntlersBlockIndentTest.kt`, `formatter/AntlersMultilineFormatTest.kt`,
  `formatter/AntlersHtmlFormatTest.kt` to corrected expectations (consolidating onto the new processor);
  keep `AntlersSpacingFormatterTest`, `AntlersTemplateTagsTest`, `AntlersFormatterOptOutTest`,
  `AntlersFormatterSettingsTest`; add new mixed-nesting / pure-HTML / preservation / idempotency tests.
- README: update the Formatting section to drop the documented "known limitations" that no longer apply.

## Out of scope

- Reflowing / line-breaking (still indentation-only).
- Block-vs-inline distinction (all tags count).
- Reindenting inside opaque/whitespace-significant regions.
- Spacing changes (owned by `AntlersSpacingPostFormatProcessor`, unchanged).
