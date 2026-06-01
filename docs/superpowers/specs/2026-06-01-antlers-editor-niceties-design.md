# Antlers Editor Niceties (Sub-project D) — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Sub-project **D** of the "full Statamic 6 Antlers compatibility" effort, scoped to
**brace matching, auto-close, commenter, and folding**. The formatter and structure view are deferred
to their own later projects.

## 1. Background & Goal

Sub-projects A (grammar/PSI), B (completion), and C (docs + navigation) are complete. This sub-project
adds the everyday editor quality-of-life features that make the Antlers editor feel finished: matched
delimiter highlighting, auto-inserting the closing `}}`, Ctrl-/ Antlers comments, and code folding.
All are small, independent platform contributions; none depend on the others.

## 2. Principles

1. **Small, independent units.** Each feature is one class + one plugin.xml registration.
2. **Reuse the existing tokens/PSI.** Delimiter tokens (`T_LDOUBLE`, `T_COMMENT_OPEN`, …), the
   `AntlersStatement` / `AntlersNamePath` / `AntlersClosingTag` PSI, and the noparse/php block nodes
   already exist from A.
3. **Antlers files only.** The typed handler acts only in `.antlers.html` (Antlers) files.

## 3. Components

### 3.1 Brace matcher
`editor/AntlersBraceMatcher.kt` implementing `PairedBraceMatcher`, registered
`<lang.braceMatcher language="Antlers">`.

- `getPairs()` returns `BracePair`s (left, right, structural):
  - `T_LDOUBLE` ↔ `T_RDOUBLE` (structural)
  - `T_COMMENT_OPEN` ↔ `T_COMMENT_CLOSE`
  - `T_PHP_RAW_OPEN` ↔ `T_PHP_RAW_CLOSE`
  - `T_PHP_ECHO_OPEN` ↔ `T_PHP_ECHO_CLOSE`
  - `T_LPAREN` ↔ `T_RPAREN`, `T_LBRACKET` ↔ `T_RBRACKET` (in-expression, non-structural)
- `isPairedBracesAllowedBeforeType` → true; `getCodeConstructStart` → returns the offset unchanged.

### 3.2 Auto-close `}}`
`editor/AntlersTypedHandler.kt` extending `TypedHandlerDelegate`, registered `<typedHandler>`.

- In `charTyped` (or `beforeCharTyped`): when the typed char is `{` in an Antlers file and the two
  characters immediately before the caret are now `{{` (i.e. the user just completed the opener) and it
  is **not** already followed by content forming `{{{`/a comment/php opener, insert ` }}` after the
  caret and move the caret between the spaces → `{{ <caret> }}`.
- Guards: only act when the file's language is Antlers; do nothing if the next non-space chars are
  already `}}` (avoid doubling); do nothing for `{{#`/`{{?`/`{{$` (those are separate openers — leave
  them, or optionally close the matching `#}}`/`?}}`/`$}}` — for v1, only `{{ }}` auto-closes).

### 3.3 Commenter
`editor/AntlersCommenter.kt` implementing `Commenter`, registered `<lang.commenter language="Antlers">`.

- `getBlockCommentPrefix()` = `{{#`, `getBlockCommentSuffix()` = `#}}` (with surrounding spaces handled
  by returning `"{{# "` / `" #}}"` if the platform doesn't add them — return the bare delimiters; the
  platform inserts them as-is).
- `getLineCommentPrefix()` = null and the line-comment token methods return null/empty (Antlers has no
  line comments). Ctrl-/ therefore performs block commenting with `{{# … #}}`.

### 3.4 Folding
`editor/AntlersFoldingBuilder.kt` extending `FoldingBuilderEx` (and `DumbAware`), registered
`<lang.foldingBuilder language="Antlers">`.

- **Single-node folds:** each `AntlersComment`, `AntlersNoparseBlock`, and `AntlersPhpBlock` becomes a
  fold region over its full text range, placeholder `{{# … #}}` / `{{ noparse }}…` / `{{? … ?}}`.
- **Paired tag/condition folds:** iterate the file's top-level `AntlersStatement`s in order. Maintain a
  stack: an opening statement is one whose body is a `namePath` (not a `closingTag`) whose head is a
  **pair tag** (catalog `isPair`) or a condition keyword (`if`/`unless`); push its (name, statement). A
  closing statement is one whose body is an `AntlersClosingTag`; pop the matching name from the stack
  and emit a fold region spanning the opener's start to the closer's end. Unmatched opens/closes are
  ignored (no fold). Placeholder = the opening statement's text.
- `isCollapsedByDefault` → false for all (everything expanded initially).

## 4. File / Package Layout

```
editor/AntlersBraceMatcher.kt
editor/AntlersTypedHandler.kt
editor/AntlersCommenter.kt
editor/AntlersFoldingBuilder.kt
resources/META-INF/plugin.xml   (+ lang.braceMatcher, typedHandler, lang.commenter, lang.foldingBuilder)
```

## 5. Testing

`BasePlatformTestCase` (runs via `./gradlew test`):
- **Auto-close:** `myFixture.type("{{")` in a `.antlers.html` file → `checkResult("{{ <caret> }}")`;
  typing `{{` where `}}` already follows does not double it.
- **Commenter:** select text, `myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)` (or
  `ACTION_COMMENT_BLOCK`) → result wraps the selection in `{{# … #}}`; toggling again removes it.
- **Folding:** `myFixture.testFolding(path)` with `<fold>` markers covering a paired tag block and a
  comment.
- **Brace matcher:** assert `AntlersBraceMatcher().getPairs()` contains the `T_LDOUBLE`/`T_RDOUBLE` and
  `T_COMMENT_OPEN`/`T_COMMENT_CLOSE` pairs.

## 6. Out of Scope (future projects)

- **Formatter** (`FormattingModelBuilder`) — reformat/indent; large and fragile given the flat grammar
  + HTML template-data interaction.
- **Structure view** (`StructureViewModel`).
- Auto-closing `{{#`/`{{?`/`{{$` openers (v1 only auto-closes `{{ }}`).

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Typed handler interferes with the HTML data language's own `{` handling | Guard to Antlers language + only act on completing `{{`; return `CONTINUE` otherwise so other handlers run |
| Auto-close doubles `}}` | Check the following non-space chars aren't already `}}` |
| Paired-tag folding mismatch on malformed templates (unbalanced) | Stack approach ignores unmatched opens/closes; never throws |
| Brace matcher pairs interfering with HTML brace matching in outer regions | Antlers brace matcher only sees Antlers tokens; outer HTML uses the HTML matcher |
