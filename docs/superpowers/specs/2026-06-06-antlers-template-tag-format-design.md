# Combined-HTML Formatter — Template-Named Tags

**Date:** 2026-06-06
**Branch:** `antlers-template-tag-format`
**Status:** Approved approach (focused scope), pending spec review

## Goal

Reformat Code indents the attributes and body of **template-named HTML tags** — `<{{ html_tag }} … >` …
`</{{ html_tag }}>` — so the user's component template (the `<{{ html_tag }}>` block: multi-line
attributes, the `{{ if }}` conditional attributes, and the element body) indents correctly. Indentation
only (Prettier-safe). This is the focused slice of the combined-HTML sub-project.

## Why this is the gap

A probe of the HTML data-language PSI showed:
- Real HTML (`<div><span>…`) parses into a clean nested `HtmlTag` tree → already indented (additively) today.
- HTML inside Antlers (`{{ if }}<span>…`) parses fine (Antlers as inline) → already indented today.
- **`<{{ html_tag }}>` is invisible to the HTML parser** — `<`, `class="x"`, `>` come back as flat text,
  no `HtmlTag`. So its attributes and body get no indent. This is the only real gap in the example.

So: handle template-named tags ourselves; leave real-HTML indentation to the existing engine.

## Scope decisions

- Indentation only; no reflow. Reformat-off opt-out preserved.
- The multi-line open tag's terminating `>` aligns with its `<{{` (the example's `+8` was a hand-made
  artifact, normalized).
- Real-HTML indentation (incl. the documented empty-element quirk) is unchanged — "own all HTML" is the
  deferred bigger sub-project.

## Components

### A. `formatter/AntlersTemplateTags` (pure scanner)

`fun elements(text: String): List<TemplateElement>` where
`TemplateElement(openStartLine: Int, openEndLine: Int, closeLine: Int?)` — `openStartLine` is the `<{{`
line, `openEndLine` is the line of the tag's terminating `>`, `closeLine` is the `</{{ … }}>` line (null if
unclosed). Pure (line indices computed from offsets), IntelliJ-free, unit-testable.

Single left-to-right scan with this state:
- **`antlersDepth`** (count of open `{{`): while > 0 we are inside an Antlers expression — only `{{`/`}}`
  change it; everything else (incl. `>`) is skipped. (So `>` in `{{ x > 0 }}` is not a tag end, and the
  `{{ html_tag }}` / conditional-attribute `{{ … }}` inside a tag are skipped.)
- **`quote`** (current HTML attribute quote char or none): inside it, `<`/`>` are literal (so `class="a>b"`
  is safe).
- When outside Antlers and outside a quote, recognize:
  - `</{{` → a template close tag: record `closeLine = line(here)`, pop the nearest open frame, and advance
    past the tag's terminating `>`.
  - `<{{` → a template open tag: remember `openStartLine = line(here)`, enter "scanning open tag" until the
    first `>` / `/>` found outside Antlers/quote; that line is `openEndLine`. `/>` → self-closing (emit no
    container); otherwise push a frame `(openStartLine, openEndLine)`.
- Unbalanced: a `</{{` with no open frame is ignored; open frames left at EOF become `TemplateElement`s
  with `closeLine = null`. Never throws.

### B. `AntlersBlockIndentProcessor` — add template depth

In the existing processor, after the Antlers `depth[]`/`touched[]`/`baseOf[]` are computed, compute the
template contribution from `AntlersTemplateTags.elements(text)`:
- `templateDepth = IntArray(lineCount)`, `templateOwned = BooleanArray(lineCount)`.
- Walk the (possibly nested) elements: for each element at template-nesting `t`, the **attribute** lines
  `(openStartLine+1 .. openEndLine-1)` and the **body** lines `(openEndLine+1 .. (closeLine ?: lineCount)-1)`
  get `templateDepth = t + 1` and `templateOwned = true`; the boundary lines (`openStartLine`,
  `openEndLine`, `closeLine`) get `templateDepth = t` and `templateOwned = true`; nested elements recurse at
  `t + 1`.
- In the per-line edit loop, process a line when `touched[line] || templateOwned[line]` (was: `touched`
  only). Combined depth `c = depth[line] + templateDepth[line]`. Targets:
  - HTML element line (`<` + letter/`/`): `currentIndent + unit.repeat(c)` (additive on the HTML formatter's
    real-HTML indent — unchanged mechanism, just `c` instead of `depth`).
  - otherwise (Antlers `{{`, the `<{{`/`>`/`</{{` template lines, text): `(baseOf[line] ?: "") + unit.repeat(c)`
    (absolute).
  - blank → ""; lines inside a newline-containing `T_STRING` → skipped (existing guard).

So a `{{ if }}` inside a `<{{ html_tag }}>` body gets `depth`(if=1) + `templateDepth`(body=1) = 2; a `<span>`
inside that if gets `currentIndent`(real html) + (1+1) = its real-html depth + 2. The multi-line `{{ … }}`
attribute's param/bracket lines are owned by the multiline pass, which reads the (now template-indented)
opener line and adds bracket depth on top.

### Composition / ordering

Unchanged processor order: spacing → block-indent → multiline. Block-indent now also indents template-tag
attribute/body lines (including the multi-line `{{ }}` opener line); the multiline pass then bracket-indents
that opener's continuation lines relative to its new indent.

## Data flow

`AntlersTemplateTags.elements(text)` → per-line `templateDepth` → combined with the Antlers `depth` in
`AntlersBlockIndentProcessor` → absolute/additive per-line indents. Real HTML and the in-`{{ }}` spacing are
untouched.

## Error handling / edge cases

- **Reformat disabled** → no-op.
- **`>` inside `{{ }}` or inside attribute quotes** → not treated as the tag end (scanner state).
- **Self-closing `<{{ x }} … />`** → no container, body unaffected.
- **Single-line `<{{ x }}>…</{{ x }}>`** → openStart==openEnd==close on one line, no interior lines → no
  indent change.
- **Unbalanced template tags** → nearest-frame pop / unclosed-to-EOF; never throws.
- **Idempotency** → combined depth is absolute (Antlers) / additive-on-reset-HTML; reformat-twice is a fixed
  point (pinned).

## Testing

- **Golden:** the user's `<{{ html_tag }} … > … </{{ html_tag }}>` block reformats to the agreed indentation
  (attributes +1, `>` aligned with `<{{`, body +1, the `{{ if }}` branches dedented and their content +1, the
  multi-line `{{ [ … ] }}` array bracket-nested, the `<span>` line indented inside its `{{ if }}`).
- **Scanner unit tests** (pure `AntlersTemplateTags.elements`): single-line open+close; multi-line open
  (attributes); self-closing `/>`; `>` inside `{{ }}`; `>` inside `"…"`; nested template tags; unbalanced.
- **Processor tests:** template attributes +1; body +1; `{{ if }}` inside a template body compounds; a real
  `<span>` inside a template body indents; the `>` aligns with `<{{`.
- **Idempotency:** reformat-twice fixed point for the golden + a nested case.
- **Opt-out:** reformat-off untouched.
- **No regression:** existing formatter suites stay green (no existing fixtures use template-named tags).
  Full-suite gate.

## Out of scope (deferred)

- Owning real-HTML indentation / fixing the empty-element quirk (the bigger "full HTML" sub-project).
- Reflow / line breaking. In-`{{ }}` spacing (separate processor).
