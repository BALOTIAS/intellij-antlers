# Full Structural Indentation Engine — Design

**Date:** 2026-06-06
**Branch:** `antlers-structural-formatter`
**Status:** Approved approach, pending spec review

## Goal

Reformat Code produces correct combined **HTML + Antlers** indentation: HTML elements and multi-line tag
attributes indent one level per enclosing `{{ if }}` / HTML tag, and Antlers blocks indent inside HTML —
all idempotently and **without ever inserting or removing line breaks** (indentation-only → safe to use
alongside Prettier). This replaces the current "HTML-reuse + additive block-indent", whose combined indent
was approximate and content-dependent.

## Scope decisions (from brainstorming)

- **Indentation only.** Never reflow/break/join lines (that is Prettier's job and the part that conflicts
  with it). `<span>x</span>` stays on its line; only its leading indent changes.
- **`else`/`elseif` dedent** to the `{{ if }}` opener level; the branch content under them is `+1`.
- The lone `>` (or `/>`) closing a **multi-line HTML tag** aligns with its `<` (the opener line's indent).
- Single-line constructs (`{{ if x }}…{{ /if }}` on one line, `<span>…</span>` on one line) stay one line.
- The existing **reformat-off setting is preserved** (Prettier users disable Antlers reformatting).

## Architecture

One **line-based indentation pass** computes each line's indent from a single **open-container stack** that
mixes HTML elements and Antlers blocks. Because each line's indent is set absolutely from its container
depth (never read from its own prior indent), the pass is idempotent by construction. The pass **owns all
indentation**; we stop relying on IntelliJ's HTML formatter for indentation (it was the source of the
content-dependent inconsistency).

Implemented as a `PostFormatProcessor` (same mechanism as today's processors), `AntlersFormatIndentProcessor`,
which **replaces** both `AntlersBlockIndentProcessor` and `AntlersMultilineTagIndentProcessor` (it subsumes
their behavior). The in-`{{ }}` spacing pass (`AntlersSpacingPostFormatProcessor`) is unchanged. The
HTML-reuse model builder (`AntlersHtmlFormattingModelBuilder`) becomes **indent-neutral** so IntelliJ's
HTML formatter no longer fights this pass; the model builder still provides the formatting model required
for Reformat to run and keeps the reformat-off no-op block.

## Components

### A. `formatter/AntlersStructuralIndent` (pure, the depth model)

A pure, IntelliJ-light object that, given the document text + the Antlers statement ranges + the indent
unit, returns the target indent string for every line. Pure so the depth logic is unit-testable directly.

`fun indents(text: String, antlers: AntlersFile, unit: String): Map<Int, String>` (line → target indent).

The single scan tracks a `stack` of open containers. For each line it records `depth = stack.size` at the
line's start, applies the closer/else dedent, computes the target indent, and updates the stack from the
line's tokens.

Container kinds and stack transitions:
- **Antlers pair**: an Antlers `{{ … }}` statement that the shared `AntlersNestingTreeBuilder` marks as a
  paired opener (catalog `isPair` tags + `if`/`unless`) pushes; its matching closer (`{{ /x }}`,
  `endif`/`endunless`) pops. `else`/`elseif` are rendered at the opener depth and do not change the stack
  (the branch content after them is the body, `depth+1`).
- **HTML element**: scanning the outer-HTML text, an open tag `<name …>` pushes a frame **unless** it is a
  void element, self-closing (`… />`), or closed on the same line (`<name …>…</name>`). `</name>` pops.
  **Template-named tags** `<{{ … }} …>` and `</{{ … }}>` are treated as a matched container pair (the
  scanner keys them by position/`html_tag`-text where possible; a `</{{ … }}>` pops the nearest
  template-named frame).
- **Multi-line opener**: an Antlers `{{ …` whose `}}` is on a later line, or an HTML `<name …` whose `>` /
  `/>` is on a later line, pushes a **continuation** frame. Its interior lines (params/attributes) are
  `depth+1`; the terminator line (`}}`, `>`, `/>`) is rendered at the opener depth and pops the frame.

Indent rules per line (first non-whitespace token decides closer/else):
- A line whose first token closes a container (`{{ /x }}`, `</name>`, a bare `>` / `/>` / `}}` terminating
  a multi-line opener, or `else`/`elseif`) renders at `depth − 1` (the container's own level).
- Otherwise the line renders at `depth`.
- Blank lines → empty. Lines inside a raw-text element or an Antlers string spanning newlines are left
  untouched.

### B. HTML tokenization rules (the main correctness surface)

The scan over `T_OUTER_HTML` regions recognizes, in priority order:
- **Comments** `<!-- … -->` (may span lines; contents untouched, not containers).
- **Raw-text elements** `<script>`, `<style>`, `<pre>`, `<textarea>` — push a container but their inner
  content lines are **left untouched** until the matching close tag (no reindent inside `<pre>` etc.).
- **Void elements** (`area base br col embed hr img input link meta param source track wbr`) — never push.
- **Self-closing** `<name … />` — never push.
- **Same-line open+close** `<name …>…</name>` — net zero (don't push).
- **Quotes**: `<`/`>` inside attribute values (`"…"`/`'…'`) are not tag boundaries.
- **Template-named** `<{{ … }} …>` / `</{{ … }}>` — matched container pair (paragraph A).

`AntlersInterpolationScanner`-style care is taken with quotes/escapes; the `{{ }}` regions inside an HTML
tag are skipped as opaque (they are Antlers, handled by the Antlers side).

### C. `formatter/AntlersFormatIndentProcessor` (the `PostFormatProcessor`)

Mirrors the existing post-processor shape: honors `AntlersFormatterSettings.reformatEnabled`, commits the
document (re-sync after the spacing pass), fetches the `AntlersFile`, calls
`AntlersStructuralIndent.indents(...)`, and applies the per-line indent edits within `rangeToReformat`
(end-to-start, `distinct()`), returning the range widened by the net delta. Never throws.

### D. `AntlersHtmlFormattingModelBuilder` → indent-neutral

Keep `createModel`'s reformat-off no-op path. Otherwise return a model whose blocks carry
`Indent.getNoneIndent()` (or a minimal whole-file block) so IntelliJ performs no HTML indentation — this
pass owns it. `getSpacing` stays null (spacing is the separate processor). Plugin.xml: remove the two
superseded `<postFormatProcessor>` registrations (block-indent, multiline) and register
`AntlersFormatIndentProcessor` after the spacing processor.

## Data flow

Reformat → spacing pass → **structural indent pass**: scan lines → open-container stack (HTML + Antlers +
multi-line openers) → per-line target indent → document edits. The model builder contributes no
indentation. Front matter (YAML) is left untouched (it is its own injected region, not part of the
container scan).

## Error handling / edge cases

- **Reformat disabled** → whole pass no-ops (defer to Prettier).
- **Unbalanced HTML / Antlers** (stray close, unclosed open) → the stack tolerates it (a pop with an empty
  or mismatched stack is ignored; unclosed openers indent their body to EOF). Never throws.
- **Raw-text / `<pre>`** → inner lines untouched.
- **Front matter** and **newline-containing Antlers strings** → lines inside are skipped.
- **Template-named tags** with mismatched `html_tag` text → fall back to nearest-frame popping; worst case
  a cosmetic off-by-one, never runaway (depth is bounded by real nesting).
- **Idempotency** → indents are absolute from container depth; reformatting an already-formatted file is a
  no-op (pinned by reformat-twice tests).

## Testing

- **Golden file:** the user's component template (front matter + assignments + if/elseif/else + the
  multi-line `<{{ html_tag }} … >` tag + nested HTML/Antlers) reformats to the agreed indentation
  (else/elseif dedented, `>` aligned with `<`, no reflow). Pinned as an exact before→after test.
- **Focused unit tests** on `AntlersStructuralIndent.indents` (pure): nested `{{ if }}` bodies; `else`/
  `elseif` dedent + branch `+1`; HTML element nesting; HTML inside Antlers and Antlers inside HTML
  (compounding); void elements / self-closing / same-line open+close don't indent; raw-text `<pre>`/`<script>`
  inner lines untouched; comment spanning lines; multi-line `{{ … }}` params `+1` and `}}` aligned;
  multi-line `<tag …>` attributes `+1` and `>` aligned; template-named `<{{ x }}>…</{{ x }}>` container.
- **Idempotency:** reformat-twice is a fixed point for the golden file and the mixed cases.
- **Opt-out:** reformat-off leaves the file untouched.
- **No regression:** existing formatter tests (`AntlersHtmlFormatTest`, `AntlersMultilineFormatTest`,
  `AntlersSpacingFormatterTest`, `AntlersFormatterOptOutTest`, `AntlersBlockIndent*`) are migrated to the
  new engine's behavior — where the new (correct, combined) indentation differs from the old approximate
  output, the expectation is updated with the before→after recorded; the spacing/opt-out tests stay green
  unchanged. Full-suite gate.

## Out of scope

- Reflow / line breaking / joining, attribute wrapping (Prettier's job; conflicts with it).
- In-`{{ }}` spacing (owned by `AntlersSpacingPostFormatProcessor`).
- Formatting the contents of `<script>`/`<style>` (left untouched).
- A configurable indent style beyond the IDE's existing indent-size/tab setting.
