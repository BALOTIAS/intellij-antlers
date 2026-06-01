# Antlers Formatter G2 — In-Delimiter Spacing Normalization — Design

**Date:** 2026-06-01
**Status:** Approved
**Scope:** Sub-project **G2** of "G (Formatter + Structure view)". A `PostFormatProcessor` that, on
"Reformat Code", normalizes spacing **inside** Antlers `{{ }}` regions — one space against the delimiters
and around modifier pipes — without touching the HTML formatting. Block indentation (the full
`TemplateLanguageFormattingModelBuilder`) is an explicit follow-up.

## 1. Background & Goal

Reformat Code currently formats the surrounding HTML and leaves Antlers untouched (no formatter is
registered — safe, but `{{x}}` stays `{{x}}`). A finished plugin tidies its own delimiters. The full
indentation-aware formatter for a template-data split is "large and fragile," so G2 delivers the safe,
high-value slice: **in-delimiter spacing normalization** via a `PostFormatProcessor` that runs *after* the
HTML formatter and edits only inside `{{ }}` spans.

Because it operates on **lexer tokens** (not raw text), a `|` or space inside a string is part of a
`T_STRING` and is never touched — strings are safe by construction.

## 2. Principles

1. **Safe, not fragile.** Post-hoc, token-based, edits confined to `{{ }}` spans; never fights the HTML
   formatter; never throws.
2. **Minimal, predictable rules.** Only delimiter edges and modifier pipes; nothing else (no operator/
   `:`/`.`/`=` spacing). Idempotent.
3. **String-safe by token boundaries.** A pipe inside a string is `T_STRING`, not `T_PIPE`.
4. **Explicit reformat only.** Runs on Reformat Code, not on-type.

## 3. Components

### 3.1 The post-format processor (`formatter/AntlersSpacingPostFormatProcessor.kt`)

Implements `com.intellij.psi.impl.source.codeStyle.PostFormatProcessor`. Registered globally
`<postFormatProcessor implementation="…">`.

```kotlin
class AntlersSpacingPostFormatProcessor : PostFormatProcessor {
    // processElement: not used for whole-token reformat; delegate to processText over its range.
    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        val antlers = source.viewProvider.getPsi(AntlersLanguage.INSTANCE) as? AntlersFile ?: return rangeToReformat
        // 1. Collect edit sites within rangeToReformat. 2. Apply end-to-start. 3. Return adjusted range.
    }
}
```

`source.viewProvider.getPsi(AntlersLanguage.INSTANCE)` returns the `AntlersFile` regardless of which file
the formatter passes (the view provider's base language is Antlers); a non-Antlers file yields null →
the range is returned unchanged (inert).

### 3.2 Edit-site collection

Walk the Antlers PSI leaves under `antlers` whose range is inside `rangeToReformat`. An **edit site** is a
whitespace gap to normalize to exactly one space:
- **After `T_LDOUBLE`** (`{{`): the gap between the `{{` and the next non-whitespace leaf — *unless* that
  next leaf is `T_RDOUBLE` (empty `{{ }}`/`{{}}` — skip).
- **Before `T_RDOUBLE`** (`}}`): the gap between the previous non-whitespace leaf and `}}` — unless that
  previous leaf is `T_LDOUBLE` (empty — skip).
- **Around each `T_PIPE`** (`|`): the gap before it (from the previous non-whitespace leaf's end) and the
  gap after it (to the next non-whitespace leaf's start), each → one space.

A "gap" is either an existing whitespace leaf (`T_WS`/`PsiWhiteSpace`) between the two tokens — whose text
is replaced with `" "` — or empty (the tokens are adjacent) — where `" "` is inserted at the boundary.
Only `T_LDOUBLE`/`T_RDOUBLE`/`T_PIPE` anchors fully within `rangeToReformat` are considered (a delimiter
straddling a partial selection is skipped). Comment (`T_COMMENT_OPEN/CLOSE`) and php (`T_PHP_*`)
delimiters are **not** anchors in v1.

### 3.3 Applying edits

Collect all edits as `(TextRange toReplace, String replacement)` (an insertion is a zero-length range).
Sort by start offset **descending** and apply each to the document
(`source.viewProvider.document`) so earlier offsets don't shift. Skip an edit whose existing gap text is
already exactly `" "` (idempotence + fewer document changes). Track the net length delta and return
`TextRange(rangeToReformat.startOffset, rangeToReformat.endOffset + delta)`.

A `PostFormatProcessor` runs inside the platform's format write action, so direct document edits are
valid; the platform re-commits the PSI afterward.

## 4. File / Package Layout

```
formatter/AntlersSpacingPostFormatProcessor.kt   (new)
resources/META-INF/plugin.xml                    (+postFormatProcessor)
```

No grammar/lexer changes.

## 5. Testing

`BasePlatformTestCase`. A helper configures a `.antlers.html` fixture and reformats the whole file in a
write action via `CodeStyleManager.getInstance(project).reformat(file)` (or `reformatText(file, 0,
file.textLength)`), then asserts `file.text`.

- **Edge spacing:** `{{x}}` → `{{ x }}`; `{{  x  }}` → `{{ x }}`; `{{ x }}` → unchanged (idempotent).
- **Separators untouched:** `{{collection:blog}}` → `{{ collection:blog }}` (`:` not spaced);
  `{{ author.name }}` → unchanged.
- **Pipe spacing:** `{{ x|upper }}` → `{{ x | upper }}`; chained `{{ x|a|b }}` → `{{ x | a | b }}`;
  `{{ x | upper }}` → unchanged.
- **String safety:** `{{ x | replace('a|b', 'c') }}` → the `a|b` and its spacing inside the string are
  unchanged (only the leading `x |` pipe is normalized).
- **Params untouched:** `{{ partial:src="blog/card" }}` → `=` not spaced; edges fine.
- **HTML preserved + multiple regions:** `<div>{{x}}</div>\n{{ y|z }}` → both Antlers regions normalized,
  HTML intact.
- **Non-expr left alone:** `{{# comment #}}`, `{{? $x ?}}` unchanged.

## 6. Out of Scope (follow-ups)

- Block indentation / the full `TemplateLanguageFormattingModelBuilder`.
- Operator / comma / `:` / `.` / `=` spacing; comment & php delimiter normalization.
- On-type formatting; a code-style settings page (rules are fixed in v1).

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Editing whitespace inside a string | Token-based — a `|`/space inside a string is `T_STRING`, never `T_PIPE`/`T_WS`; sites only at `T_LDOUBLE`/`T_RDOUBLE`/`T_PIPE` boundaries |
| Fighting the HTML formatter | Runs after it and edits only inside `{{ }}` spans (opaque `ANTLERS_FRAGMENT` to HTML); no overlap |
| Offset shifting across multiple edits | Apply end-to-start; return the range adjusted by net delta |
| Selection partly covering a delimiter | Only anchors fully within `rangeToReformat` are edited; partial delimiters skipped |
| Over-aggressive operator spacing | v1 normalizes only edges + pipes; never touches operators/`:`/`.`/`=` |
| Processor not invoked for the Antlers tree | Global `<postFormatProcessor>`; fetches the Antlers PSI via `viewProvider.getPsi` |
| Malformed/unclosed `{{` | Token-based; a region with no matching `T_RDOUBLE` simply produces no before-`}}` edit; never throws |
| Empty `{{}}` / `{{ }}` | Skipped (the after-`{{` site's next leaf is `T_RDOUBLE`) |
