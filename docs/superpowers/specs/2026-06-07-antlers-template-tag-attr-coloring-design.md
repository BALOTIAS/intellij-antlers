# Attribute coloring for template-named Antlers tags

**Date:** 2026-06-07
**Status:** Approved (design)

## Problem

When an HTML tag's name is an Antlers interpolation — `<{{ as or 'article' }} class="…">` … `</{{ as or 'article' }}>` — the tag's attributes lose their HTML coloring. Confirmed via an editor-highlighter token dump:

```
<{{ as or 'article' }} class="prose max-w-none">
  ' class="prose'   XML_DATA_CHARACTERS   keys=HTML_CODE        ← plain text, no green
vs a normal <div class="prose max-w-none">:
  'prose max-w-none' XML_ATTRIBUTE_VALUE_TOKEN keys=HTML_ATTRIBUTE_VALUE  ← green
```

### Root cause

The editor highlighter (`AntlersTemplateHighlighter`) is a `LayeredLexerEditorHighlighter`: it lexes the file with the Antlers lexer, then re-runs the HTML lexer **independently on each `T_OUTER_HTML` chunk**. When `<{{ … }}>` splits a tag, the chunk after `}}` (` class="…">`) is lexed from HTML's default *text* state, so it becomes plain `XML_DATA_CHARACTERS` instead of attribute tokens. `LayeredLexerEditorHighlighter` cannot carry the HTML lexer's "inside-a-tag" state across the `{{ }}` gap, and because the tag *name* is the gap, even `<` is not recognized as a tag start. This is inherent to the layered approach.

## Goal

Restore HTML attribute coloring (names + values, including the "green" value color) inside template-named `<{{ … }}>` tags, without rebuilding the highlighting pipeline. Normal `<div …>` tags already color correctly via the lexer and must stay untouched. The interpolated tag name and any `{{ }}` inside attribute values stay Antlers-colored.

## Approach (chosen: targeted annotator)

A `language="Antlers"` annotator overlays HTML's own attribute color keys onto the attribute ranges of `<{{ }}>` tags. The lexer pipeline is unchanged. Two units:

### 1. `AntlersTemplateTagAttributes` (new, pure, IntelliJ-free)

- `enum class Kind { NAME, VALUE }`
- `data class AttrPart(val start: Int, val end: Int, val kind: Kind)` — half-open `[start, end)` offsets in the file text.
- `fun parts(text: String): List<AttrPart>` — pure text scan, tolerant, never throws.

Algorithm:
- Scan for template-named open tags only: a `<` immediately followed by `{{` (i.e. `<{{`). (A normal `<div …>` is skipped — the lexer already colors it.)
- From the `<{{`, find the open tag's terminating `>`, skipping over `{{ … }}` regions and quoted strings (`"…"` / `'…'`) so a `>` inside an Antlers expression or an attribute value does not end the tag. This mirrors the scan discipline in the existing `AntlersTemplateTags`.
- Within the open tag (after the leading `<{{ … }}` name interpolation), scan attributes:
  - Skip whitespace. Skip any `{{ … }}` region (leave it Antlers-colored — emit no part).
  - An identifier run (`[A-Za-z_:-]` style, matching HTML attribute-name chars incl. `:` and `-` for `x-ref`, `attr:class`, `@click`, `:href`) → one `AttrPart(NAME)`.
  - If the next non-whitespace char is `=`, then a value follows:
    - Quoted (`"…"` or `'…'`): emit `AttrPart(VALUE)` covering the quoted value **split around any `{{ … }}`** — one VALUE part per run of non-interpolation characters, with the opening quote included in the first part and the closing quote in the last part; `{{ }}` sub-ranges get no part (so the run before an interpolation ends at the `{{`, and the run after resumes at the `}}`). Quoted values may span multiple lines. (HTML colors the quote delimiters with the same `HTML_ATTRIBUTE_VALUE` key, so including them matches normal-tag rendering.)
    - Unquoted (`name=foo`): one `AttrPart(VALUE)` for the token run (stops at whitespace / `>` / `{{`).
- A tag with no terminating `>` (unterminated at EOF) contributes only the parts found so far.

### 2. `AntlersTemplateTagAttributeAnnotator` (new `Annotator`, `language="Antlers"`)

- Runs once on the `AntlersFile` root (`if (element !is AntlersFile) return`), like `AntlersBalanceAnnotator`. Skips default projects.
- Calls `AntlersTemplateTagAttributes.parts(element.text)`. For each part, emits:
  `holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(TextRange(start, end)).textAttributes(key).create()`
  where `key` = `com.intellij.openapi.editor.XmlHighlighterColors.HTML_ATTRIBUTE_NAME` for `NAME` and `HTML_ATTRIBUTE_VALUE` for `VALUE`.
- Tolerant; never throws.
- Registered in `plugin.xml`: `<annotator language="Antlers" implementationClass="…editor.AntlersTemplateTagAttributeAnnotator"/>`.

## Data flow

```
highlighting pass → AntlersTemplateTagAttributeAnnotator on AntlersFile
  → AntlersTemplateTagAttributes.parts(file.text)
  → one silent INFORMATION annotation per part, HTML_ATTRIBUTE_NAME / HTML_ATTRIBUTE_VALUE
  → layered over the lexer's plain HTML_CODE for those ranges
```

## Edge cases / robustness

- Only `<{{ }}>` tags are processed; normal tags produce no parts (lexer owns them; no double-coloring).
- `{{ }}` interpolations inside the tag (name or attribute value) are never recolored — they stay Antlers-colored (the parts scan emits nothing for those sub-ranges).
- `>` inside an Antlers expression (`{{ x > 0 }}`) or inside a quoted value does not terminate the tag.
- Multi-line attribute values are supported (the value scan crosses newlines until the closing quote).
- Unterminated tag / malformed input → partial or empty result, never throws.
- HTML closing tags `</{{ … }}>` have no attributes → no parts.

## Testing

Pure unit tests for `AntlersTemplateTagAttributes.parts` (no platform):
- single attribute `<{{ as }} class="x">` → NAME `class`, VALUE `x`.
- multiple attributes incl. `x-ref="form"`, `attr:class="y"`, boolean `hidden` (NAME only).
- multi-line value `class="\nprose\nmax-w-none\n"` → VALUE part(s) covering the value text across lines.
- value with interpolation `class="a {{ class }} b"` → VALUE parts for `a ` and ` b`, nothing for `{{ class }}`.
- single quotes; unquoted value.
- `>` inside a value/expression does not end the tag.
- a normal `<div class="x">` → empty (not a template-named tag).
- `</{{ as }}>` → empty.

Platform test (`BasePlatformTestCase`, `myFixture.doHighlighting()`):
- in `<{{ as }} class="prose">…`, the `prose` range carries `HTML_ATTRIBUTE_VALUE`.
- a normal `<div class="prose">` is **not** annotated by this annotator (no added HTML_ATTRIBUTE_VALUE from us — i.e. exactly one source).
- the `{{ class }}` interpolation inside a value is not given an HTML attribute key.

## Files

- New: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributes.kt`
- New: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTemplateTagAttributeAnnotator.kt`
- Edit: `src/main/resources/META-INF/plugin.xml` (register the annotator)
- New tests under `src/test/.../editor` and `.../highlighting`.
- README: update the ⑤ known-limitation note to reflect that template-named-tag attributes are now colored (the remaining coloring caveat narrows or is removed).

## Out of scope

- Carrying HTML lexer state across `{{ }}` (approaches B/C); no lexer/highlighter pipeline changes.
- Coloring normal `<div>` tags (already handled by the lexer).
- Full HTML-lexer fidelity (entities, CDATA, script/style content) inside template-named tags — only attribute name/value coloring.
- The multi-line attribute-value *indentation* gap (separate, documented formatter limitation).

## Verification note

Per past experience, color overlays layered over a base can render differently per theme. The `doHighlighting` test confirms the key is applied; the on-screen color must be eyeballed in the user's PhpStorm before considering it done.
