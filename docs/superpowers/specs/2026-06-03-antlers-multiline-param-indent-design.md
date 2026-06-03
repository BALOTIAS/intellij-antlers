# Antlers Multi-line Tag Parameter Indentation — Design

**Date:** 2026-06-03
**Branch:** `antlers-multiline-param-indent`
**Status:** Approved approach, pending spec review
**Feedback item:** #9 ("multi-line tag-parameter indentation")

## Goal

When an Antlers tag spans more than one line, Reformat Code should indent its parameter lines one level
under the `{{` line and keep `}}` on its own line. Target:

```
{{ collection:blog
    limit="3"
    as="posts"
}}
```

(params indented one unit under the opener line; `}}` on its own line at the opener's indent).

## Background (current behavior, reproduced)

Reformat today **destroys** multi-line tags. Verified with a throwaway probe (`reformatText` on the whole
file):

| input | output today |
|---|---|
| `{{ collection:blog`⏎`    limit="3"`⏎`    as="posts"`⏎`}}` | `{{ collection:blog`⏎`limit="3"`⏎`as="posts" }}` |
| (same, in a `<div>`) | `<div>`⏎`····{{ collection:blog`⏎`····limit="3" }}`⏎`</div>` |

Two failures: param indentation is **stripped** (to the opener-line indent — column 0 at top level, the
HTML-child column inside an element), and the closing `}}` is **pulled onto the last param line**.

Root cause (two cooperating pieces):
- The HTML formatter treats the whole `{{ … }}` as one opaque template block and re-lays its interior to
  the opener-line indent (it does not add a deeper level for params).
- `AntlersSpacingPostFormatProcessor.normalizeGap` collapses *any* blank gap adjacent to `{{`/`}}`/`|`
  to a single space — including the newline before `}}`, which yanks the closer up.

Block-level indentation via the formatting model is a known dead end here (attempted and abandoned twice —
runaway indent / content-dependent block wrapping). Multi-line *parameter* indentation is a narrower,
text-based problem we can own with a post-format processor.

## Approach (A)

A new post-format processor **overwrites** the leading whitespace of each interior line of a multi-line
`{{ … }}` region — setting param lines to `openerIndent + oneUnit` and the `}}` line to `openerIndent`.
Because it sets indentation absolutely, it is independent of whatever the HTML formatter did to the
interior, so it is robust and idempotent. A one-line fix to the spacing processor stops it joining lines
(so `}}` is no longer pulled up). Both stay entirely out of the formatting model.

## Components

### 1. `AntlersSpacingPostFormatProcessor` (modify, ~2 lines)

In `normalizeGap(aEnd, bStart)`, after reading `current = document.getText(gap)`, add: if
`current.contains('\n')` return (do not collapse). Effect: intentional line breaks inside `{{ }}` are
preserved and `}}` is never pulled onto the previous line. Existing single-line spacing tests have no
newlines, so they are unaffected.

### 2. `AntlersMultilineTagIndentProcessor` (new `PostFormatProcessor`)

`src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersMultilineTagIndentProcessor.kt`,
registered in `plugin.xml` alongside the spacing processor. `processElement` returns `source` unchanged;
`processText(source, rangeToReformat, settings)` does the work. Never throws. Pattern mirrors the spacing
processor: fetch the Antlers PSI via `source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile`
(else return the range unchanged) and the `source.viewProvider.document`.

Indent unit: `CodeStyle.getIndentOptions(source)` → `unit = if (USE_TAB_CHARACTER) "\t" else
" ".repeat(INDENT_SIZE)` (the same source the Enter-handler uses;
`import com.intellij.application.options.CodeStyle`).

For each `AntlersStatement` (= a `{{ … }}` region; `PsiTreeUtil.findChildrenOfType(antlersFile,
AntlersStatement::class.java)`) whose range lies within `rangeToReformat`:

- `openerLine = document.getLineNumber(stmt.textRange.startOffset)`;
  `closerLine = document.getLineNumber(stmt.textRange.endOffset - 1)`. If equal → single-line → skip.
- `openerIndent` = the leading-whitespace run of the opener line (from its line start).
  `contIndent = openerIndent + unit`. The `}}` token start is `stmt.textRange.endOffset - 2`.
- Pre-collect the document ranges of every `T_STRING` leaf inside the statement (Antlers strings may
  contain newlines) for the string-safety guard.
- For each line `L` from `openerLine + 1` through `closerLine`:
  - `lineStart = document.getLineStartOffset(L)`; `firstNonWs` = offset of the first non-`[ \t]`
    character on the line (or line end if blank); the current indent range is `[lineStart, firstNonWs)`.
  - **String guard:** if `lineStart` is strictly inside any collected `T_STRING` range
    (`tokenStart < lineStart < tokenEnd`), skip the line (it is string content).
  - **Target:** if the line is blank → `""`; else if `L == closerLine` and `firstNonWs == }}` token start
    (the `}}` is the first non-whitespace on its line) → `openerIndent`; else → `contIndent`.
  - If the current indent text ≠ target and the edit range is within `rangeToReformat`, queue a replace
    of `[lineStart, firstNonWs)` with the target.
- Apply queued edits end-to-start (offsets stay valid); track the total length delta.

Return `TextRange(rangeToReformat.startOffset, rangeToReformat.endOffset + delta)`.

### 3. Registration

Add to `plugin.xml` (next to the existing `<postFormatProcessor …AntlersSpacingPostFormatProcessor>`):
```xml
<postFormatProcessor implementation="com.github.balotias.intellijantlers.formatter.AntlersMultilineTagIndentProcessor"/>
```

## Data flow / ordering

Post-format processors run after the formatting model. The spacing processor (now newline-safe) and the
new processor edit different things on a multi-line tag — spacing skips the newline gaps, the new processor
rewrites interior line-leading whitespace — so their order does not matter. The new processor sets indents
absolutely, so it does not depend on the HTML formatter's interior result.

## Error handling / edge cases

- Single-line `{{ }}`, comments (`{{# #}}`), PHP (`{{? ?}}` / `{{$ $}}`), noparse, and all HTML → untouched
  (only multi-line `AntlersStatement`s are processed).
- `}}` left inline with the last param (not on its own line) → that line is treated as a param line
  (`contIndent`); we do **not** force a split in v1.
- A param value string that spans lines → the string-safety guard skips lines starting inside the string,
  so its content is never reindented/corrupted.
- A line with non-whitespace before `{{` on the opener line (rare for multi-line tags) → `openerIndent` is
  the opener line's leading whitespace; params indent under that. Acceptable.
- Idempotent: re-running reformat reproduces the same layout (the formatter re-flattens, the processor
  re-applies the same absolute indents).

## Testing

`AntlersMultilineFormatTest` (mirrors `AntlersSpacingFormatterTest`: `reformatText(file, 0, len)` in a
`WriteCommandAction`, then `PsiDocumentManager.commitAllDocuments()`, assert `file.text`):

- Canonical: `{{ collection:blog`⏎`limit="3"`⏎`as="posts"`⏎`}}` → params at one unit, `}}` on its own line
  at column 0.
- Indent-agnostic: the same tag pre-indented with 8 spaces reformats to the **same** canonical output
  (proves it overwrites, not preserves).
- Idempotent: reformatting the canonical output again is unchanged.
- Inside an element: `<div>`⏎`{{ collection:blog`⏎`limit="3"`⏎`}}`⏎`</div>` → opener at the div-child
  indent, params one unit deeper, `}}` at the opener indent.
- Single-line untouched: `{{ collection:blog limit="3" }}` unchanged; existing `AntlersSpacingFormatterTest`
  stays green (spacing fix doesn't affect single-line).
- `}}` inline with the last param → last line indented as a param line (no crash, no split).
- String safety: a multi-line string value inside a param is not reindented (its inner lines unchanged).
- Full-suite gate (`./gradlew --rerun-tasks test`, failures/errors grep empty) — in particular
  `AntlersHtmlFormatTest` and `AntlersSpacingFormatterTest` must stay green.

## Out of scope

- Collapsing a multi-line tag to one line, or breaking a long single-line tag into multiple lines (the
  user chose "preserve multi-line, indent one level"; auto-wrap is a separate, more opinionated feature).
- Block-level indentation of Antlers pair-tag bodies (the abandoned formatting-model work — unchanged).
- Aligning params to the column just after `{{ ` (the user chose one-level-under instead).
- Forcing `}}` onto its own line when the user kept it inline with the last param.
