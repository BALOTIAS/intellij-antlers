# Antlers Block Indentation — Design

**Date:** 2026-06-04
**Branch:** `antlers-block-indent`
**Status:** Approved approach, pending spec review

## Goal

Make Reformat Code indent the body of a paired Antlers tag/condition one level deeper than its opener:
```
{{ collection:articles }}
    <article>
        <h2>{{ title }}</h2>
    </article>
    {{ if featured }}
        <span>★</span>
    {{ /if }}
{{ /collection }}
```
Today the formatter treats Antlers `{{ }}` regions as inline (to avoid runaway indentation), so a
paired-tag body gets no indent level — a documented accepted tradeoff in `AntlersHtmlFormattingModelBuilder`.

## Scope (from brainstorming, after an empirical probe of the formatter)

**Chosen: "tag lines + simple HTML."** Indent the `{{ }}` statement lines by Antlers nesting (set
absolutely) AND shift HTML/text body lines by the Antlers depth (added onto the HTML formatter's own
output). The full structural HTML-nesting engine (pixel-perfect combined indent) is explicitly out of scope.

**Probe findings that drive the design (reformatting paired tags with body content):**
- The HTML formatter does **not** touch `{{ }}` lines — a manually-indented `{{ title }}` inside a pair
  stays put across reformats. So a naive additive pass on Antlers lines would **runaway**; Antlers lines
  must be set **absolutely**.
- The HTML formatter **does** re-normalize HTML element lines each reformat (e.g. 8→4 spaces). So adding
  the Antlers depth onto an HTML line's current indent is idempotent.
- The HTML formatter's indentation of HTML *inside* an Antlers pair is **inconsistent** (empty
  `<article></article>` gets a level; `<article>` with children gets none) — a pre-existing quirk we
  accept, not fix.

## Component

### `formatter/AntlersBlockIndentProcessor` (new) — `PostFormatProcessor`

Registered in `plugin.xml` **before** `AntlersMultilineTagIndentProcessor` so per-tag parameter
indentation composes on top of the block-adjusted opener indent. Text/document-based (same pattern as
the multiline processor), honors the `AntlersFormatterSettings.reformatEnabled` opt-out, never throws.

`processText(source, rangeToReformat, settings)`:

1. If reformat is disabled → return `rangeToReformat` unchanged.
2. `PsiDocumentManager.commitDocument(document)` — re-sync PSI after the spacing pass (same desync
   guard the multiline processor uses), then fetch the Antlers PSI
   (`source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile`).
3. `tree = AntlersNestingTreeBuilder.build(antlers, project)` — the paired structure (shared with
   folding/balance/structure-view). `NestingNode(opener, name, closer?, children)`.
4. **Per-line Antlers depth.** Walk the tree to assign each line a depth `D`:
   - A node at tree-depth `d` (number of ancestor nodes) contributes: its **opener line** and **closer
     line** are at depth `d`; the lines strictly between the opener's last line and the closer's first
     line (its body) are at depth `d + 1`, except where a deeper child node overrides them. Unclosed
     nodes (`closer == null`) indent their body to EOF at `d + 1`. Lines in no node's body are depth `0`.
   - `unit = "\t"` or `INDENT_SIZE` spaces, from `CodeStyle.getIndentOptions(source)`.
5. **`base` anchor.** For each outermost (tree-root) opener, capture its current leading whitespace as
   `base`; depth-0 opener lines are left unchanged, so `base` is stable across reformats. (Top-level
   blocks → `base == ""`.)
6. **Per-line target.** For each line `L` in `rangeToReformat`:
   - **Continuation line of a multi-line `{{ }}`** (`openerLine < L <= statementEndLine` for the
     statement starting on `openerLine`) → **skip**; the multiline processor owns it.
   - **Line whose first non-whitespace is `{{` (an Antlers statement), or plain text** → set indent
     **absolutely** to `base + D*unit`.
   - **Line whose first non-whitespace is `<` (HTML)** → set indent to `currentIndent + D*unit`
     (additive; HTML re-normalizes the base each reformat → idempotent).
   - **Blank line** → strip to `""`.
   - **Inside an Antlers `T_STRING` that spans newlines** → skip (never reindent a line that begins
     inside a string), mirroring the multiline processor's guard.
7. Collect `(indentRange, replacement)` edits, `distinct()`, apply **end-to-start**, return the range
   widened by the net delta.

### Composition with existing processors

Post-format order becomes: `AntlersSpacingPostFormatProcessor` → **`AntlersBlockIndentProcessor`** →
`AntlersMultilineTagIndentProcessor`. The block processor sets each statement's **opener line** indent;
the multiline processor then indents that statement's **continuation param lines** relative to the
(now block-adjusted) opener indent. Both commit the document at entry, so offsets stay valid.

## Data flow

Antlers PSI → `AntlersNestingTreeBuilder.build` → per-line Antlers depth → per-line absolute (Antlers/
text) or additive (HTML) indent target → document edits. The HTML formatter has already run (primary
indentation); this processor only adds the Antlers nesting level the inline model omits.

## Error handling / edge cases

- **Reformat disabled** → no-op (defer to Prettier etc.).
- **Unclosed / unbalanced tags** → `NestingTree` marks them (`closer == null` / `unmatchedClosers`);
  unclosed openers indent their body to EOF; unmatched closers contribute no depth. Never throws.
- **Single-line / non-paired tags** → not in the tree as openers → depth 0 → untouched.
- **Multi-line opener tag** → only its first (opener) line is set here; its param continuation lines are
  skipped (owned by the multiline processor).
- **Newline-containing Antlers strings** → lines starting inside a string are skipped.
- **HTML-inside-pair quirk** → the HTML formatter's pre-existing inconsistent indent persists; an HTML
  body line can land one level off in the empty-element edge case. Idempotent, never runaway.
- **Standalone text inside an HTML element inside a pair** → may flatten to `base + D*unit` (treated as
  text, not HTML-nested). Accepted edge.

## Idempotency

Antlers/text lines are set absolutely from the stable `base` anchor (their own prior indent is never
read), and HTML lines are added onto the HTML formatter's freshly re-normalized indent. Reformatting an
already-formatted file is therefore a no-op. Pinned by a reformat-twice test.

## Testing

- **Nested blocks:** `{{ collection }}…{{ /collection }}` and nested `{{ if }}…{{ /if }}` bodies indent
  one level per enclosing pair; closer lines align with their openers.
- **HTML body:** an HTML element line inside a pair shifts by the Antlers depth.
- **Composition:** a multi-line tag (`{{ collection:blog\n limit="3"\n}}`) inside a pair still gets its
  params indented relative to the block-adjusted opener (block + multiline together).
- **Idempotency:** reformat twice → identical output (no runaway), for an antlers-only and a mixed
  HTML+antlers sample.
- **Opt-out:** with reformat disabled the body is untouched.
- **Robustness:** an unclosed `{{ if }}` (no `{{ /if }}`) and a stray `{{ /collection }}` don't crash and
  don't mis-indent surrounding content.
- **No regression:** existing `AntlersHtmlFormatTest` / `AntlersMultilineFormatTest` / `AntlersSpacing
  FormatterTest` / `AntlersFormatterOptOutTest` stay green (the processor is a no-op without paired
  Antlers blocks).
- **Full-suite gate.**

## Out of scope

- A full structural HTML+Antlers indentation engine (re-deriving HTML nesting incl. void/inline/`<pre>`/
  unclosed) — pixel-perfect combined indent.
- Replacing or restructuring `AntlersHtmlFormattingModelBuilder`.
- Reflowing/wrapping content; only leading-whitespace indentation changes.
