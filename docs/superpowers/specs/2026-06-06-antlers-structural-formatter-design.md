# Structural Indentation — Decomposed; Sub-project 1: Antlers-Side (conditionals + multi-line expressions)

**Date:** 2026-06-06
**Branch:** `antlers-structural-formatter`
**Status:** Approved approach (decomposed), pending spec review

## Context & decomposition

The original goal — full combined HTML+Antlers structural indentation on Reformat (indentation only, no
reflow, Prettier-safe) — decomposes into ~4 layers. Building all at once is high-risk in this
historically-fragile area, so it is split into sequential, independently-shippable sub-projects:

1. **Antlers conditionals** — `else`/`elseif` dedent to the `{{ if }}` opener level. *(this sub-project)*
2. **Multi-line `{{ … }}` expression nesting** — indent expression lines by bracket depth (`[ ( {`).
   *(this sub-project)*
3. **Combined HTML element nesting** — HTML indenting inside Antlers blocks and vice-versa, via a custom
   HTML tokenizer (void/raw-text/comments/quotes). *(deferred follow-up)*
4. **Multi-line + template-named HTML tags** (`<{{ html_tag }} … >`). *(deferred follow-up, depends on 3)*

**This sub-project = layers 1 + 2**, implemented as surgical extensions to the two existing post-format
processors (no HTML reimplementation, no model-builder change, minimal regression churn). Indentation
only; the reformat-off setting (Prettier opt-out) is unchanged.

## Current behavior (what we extend)

- `AntlersBlockIndentProcessor` indents paired-tag/condition bodies one level per enclosing pair. `else`/
  `elseif` currently land at **body level** (they are ordinary body lines in its depth walk).
- `AntlersMultilineTagIndentProcessor` indents the param lines of a multi-line `{{ … }}` **one flat level**
  under `{{` (no bracket-depth nesting), and keeps `}}` at the opener indent.

## Components

### A. `else`/`elseif` dedent — `formatter/AntlersBlockIndentProcessor`

In the depth walk, a body line that begins an `else`/`elseif` statement renders at the **enclosing node's
depth** (the `{{ if }}` level), not the body depth. Detection: a statement whose
`AntlersConditionMixin.keyword` is `"else"` or `"elseif"` (the only keywords inside an `if`/`unless` body
that act as branch markers).

Mechanics: precompute `elseLines` = the set of lines whose statement is an `else`/`elseif`. In `walk`, when
assigning the node's body lines `depth = d + 1`, assign `depth = d` for any line in `elseLines`. The lines
*after* an `else`/`elseif` remain body lines (`d + 1`), so each branch's content stays indented `+1`.

Result for `{{ if x }} … {{ else }} … {{ /if }}`: `{{ if }}`, `{{ else }}`, `{{ /if }}` align at the
opener level; both branches' content is `+1`.

### B. Multi-line expression bracket nesting — `formatter/AntlersMultilineTagIndentProcessor`

Within a multi-line `{{ … }}` statement, replace the single flat `openerIndent + unit` for every interior
line with a **bracket-depth-aware** indent:

```
interiorIndent(line) = openerIndent + (1 + openBefore(line) − closesFirst(line)) * unit
```
- `openBefore(line)` = net count of `[ ( {` minus `] ) }` in the statement text **before** the line's first
  content char, counting only brackets **outside Antlers strings** (skip chars inside `T_STRING` spans —
  the processor already collects these).
- `closesFirst(line)` = 1 if the line's first non-whitespace char is `]`, `)`, or `}` (so a closing bracket
  dedents to its opener's level), else 0.
- The `}}` terminator line stays at `openerIndent` (unchanged).

Result for the multi-line array:
```
{{
    [
        'btn' => …,
        …
    ] | filter_empty | classes | attribute:class
}}
```
— `[` at `+1`, elements at `+2`, the `]`-line back at `+1`, `}}` at the opener.

### Composition

`else`/`elseif` (A) is block-level; bracket nesting (B) is within-statement. They compose exactly as the
block + multiline processors already compose (block sets the opener/closer/body lines; multiline owns the
continuation lines of a multi-line statement). Processor order is unchanged (spacing → block-indent →
multiline). Both keep their `reformatEnabled` opt-out, document-commit re-sync, and never-throw behavior.

## Data flow

Reformat → spacing → block-indent (now dedents `else`/`elseif`) → multiline (now bracket-depth-aware for
multi-line `{{ … }}`). HTML lines keep today's behavior (the combined-HTML rewrite is the deferred
follow-up).

## Error handling / edge cases

- **Reformat disabled** → both passes no-op (Prettier).
- **`else`/`elseif` with no enclosing `if`** (stray) → not inside any node's body → unaffected (no dedent).
- **Brackets inside strings** (`{{ x = "a[b" }}`) → skipped via the string spans, so they don't shift the
  depth.
- **Unbalanced brackets** in a malformed expression → depth clamps at ≥ 1 (never negative); never throws.
- **Single-line `{{ … }}`** → not a multi-line statement → bracket logic doesn't apply.
- **Idempotency** → both indents are absolute (block from node depth + opener anchor; multiline from the
  opener indent + bracket depth), so reformat-twice is a fixed point.

## Testing

- **`else`/`elseif`:** `{{ if x }}\n{{ a }}\n{{ else }}\n{{ b }}\n{{ /if }}` → `{{ if }}`/`{{ else }}`/
  `{{ /if }}` at col 0, `{{ a }}`/`{{ b }}` at `+1`; an `{{ elseif }}` chain dedents likewise; nested
  `if`/`else` compounds.
- **Bracket nesting:** the multi-line array example indents elements by bracket depth (`[`→`+1`,
  elements→`+2`, `]`→`+1`); nested `[ [ … ] ]`; a `(` group; a bracket char inside a string does not shift
  depth; the `}}` stays at the opener.
- **Composition:** a multi-line `{{ [ … ] }}` inside an `{{ if }}` block compounds (block `+1`, then bracket
  depth on top).
- **Idempotency:** reformat-twice is a fixed point for both.
- **Opt-out:** reformat-off leaves the file untouched.
- **No regression:** existing `AntlersBlockIndent*`, `AntlersMultilineFormatTest`, `AntlersHtmlFormatTest`,
  `AntlersSpacingFormatterTest`, `AntlersFormatterOptOutTest` stay green — where the new (correct) `else`/
  bracket output differs from a pinned old expectation, that expectation is re-recorded with before→after.
  Full-suite gate.

## Out of scope (deferred follow-ups)

- **Combined HTML element nesting** (layer 3) and **multi-line / template-named HTML tags** (layer 4) — the
  custom HTML tokenizer; their own spec/plan/build once this Antlers-side layer is proven stable.
- Reflow / line breaking (Prettier's job).
- In-`{{ }}` spacing (owned by `AntlersSpacingPostFormatProcessor`).
