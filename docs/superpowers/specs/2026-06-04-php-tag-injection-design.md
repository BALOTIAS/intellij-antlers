# Native `<?php ?>` + Hardened PHP Injection — Design

**Date:** 2026-06-04
**Branch:** `php-tag-injection`
**Status:** Approved approach, pending spec review

## Goal

Recognize literal `<?php … ?>` and `<?= … ?>` tags anywhere in Antlers files and inject the real PHP
language into them (so PhpStorm / IDEA Ultimate give highlighting and completion), reusing — and
hardening — the existing `{{? ?}}` / `{{$ $}}` PHP-node injection. This is IDE support only; it does not
change the template **data** language (still HTML).

## Scope decisions (from brainstorming)

- **Recognition surface:** all Antlers files (`.antlers.html` and `.antlers.php`), via the shared lexer.
  Gating to `.antlers.php`-only would need a separate language/file type and was rejected as not worth it
  — literal `<?php` is meaningless in `.antlers.html` and essentially never appears there.
- **Tags covered:** `<?php … ?>` (statements) and `<?= … ?>` (echo expression). Bare `<?` and `<?xml`
  are **not** claimed — they stay outer HTML.
- **Part 3 ("make `{{? ?}}` solid"):** no specific bug reported; treat as hardening the shared injection
  (prefix consistency, empty body, unclosed tag, multi-statement, write-back round-trip).

## Current state (what we build on)

- Lexer states `PHP_RAW` (`{{? … ?}}`) and `PHP_ECHO` (`{{$ … $}}`) emit `T_PHP_*_OPEN`/`CLOSE` and a
  shared `T_PHP_TEXT` body.
- Grammar: `phpBlock ::= phpRawBlock | phpEchoBlock`; both contain a `phpBlockBody` whose mixin
  (`AntlersPhpBlockBodyMixin`) implements `PsiLanguageInjectionHost`.
- `AntlersPhpInjector` injects PHP into every `AntlersPhpBlockBody`, synthesizing a prefix via
  `prefixFor` (`<?= ` for an echo block, else `<?php `). PHP is resolved by id (no compile dependency);
  absent → graceful no-op.
- `AntlersPhpBlockBodyManipulator` handles write-back of edits made in the injected PHP fragment.

## Components

### A. Lexer — `src/main/grammar/AntlersLexer.flex`

Add a `PHP_TAG` state mirroring `PHP_RAW`/`PHP_ECHO`:

- In `CONTENT`, before the catch-all, add:
  - `"<?php"` → enter `PHP_TAG`, return `T_PHP_TAG_OPEN`.
  - `"<?="` → enter `PHP_TAG`, return `T_PHP_ECHO_TAG_OPEN`.
- New state:
  ```
  <PHP_TAG> {
    "?>"   { yybegin(CONTENT); return AntlersTypes.T_PHP_TAG_CLOSE; }
    [^?]+  { return AntlersTypes.T_PHP_TEXT; }
    "?"    { return AntlersTypes.T_PHP_TEXT; }
  }
  ```
  An unclosed `<?php` simply runs to EOF as `T_PHP_TEXT` (no close token emitted).

**Catch-all change (must not fragment normal HTML).** The current outer-HTML rule `[^{@]+` would swallow
`<?php`. Replace it with a run that consumes ordinary text and `<…>` markup but stops at a `<?` boundary,
plus a lone-`<` fallback. The `CONTENT` outer-HTML rules become:

```
  ( [^{@<] | "<" [^?{@<] )+   { return AntlersTypes.T_OUTER_HTML; }   // text + <tag…>, crossing many '<'
  "<"                         { return AntlersTypes.T_OUTER_HTML; }   // lone '<' before ?, {, @, <, or EOF
  "@"                         { return AntlersTypes.T_OUTER_HTML; }
  "{"                         { return AntlersTypes.T_OUTER_HTML; }
```

The run alternative `"<" [^?{@<]` matches a `<` only when it is **not** the start of `<?`, `<{`, `<@`, or
`<<`, so the run crosses ordinary tags (`<div>…</div>` stays a single `T_OUTER_HTML` token) but halts
before `<?`. JFlex longest-match then lets `"<?php"` (5 chars) / `"<?="` (3) win over the 1-char `"<"`
fallback. Bare `<?xml` → `"<"` fallback then `?xml` via the run, both `T_OUTER_HTML` (not claimed as PHP).
A `<` immediately before `{{`/`@`/EOF falls to the `"<"` fallback, leaving the following tag intact. Because
normal markup is unchanged, existing parser `.txt` fixtures are largely unaffected (only inputs containing
a literal `<?` boundary change).

New tokens (declared in the grammar, see B): `T_PHP_TAG_OPEN`, `T_PHP_ECHO_TAG_OPEN`, `T_PHP_TAG_CLOSE`.
The body token is the existing `T_PHP_TEXT`.

### B. Grammar — `src/main/grammar/Antlers.bnf`

Add the three tokens to the `tokens` block:
```
T_PHP_TAG_OPEN="<?php"
T_PHP_ECHO_TAG_OPEN="<?="
T_PHP_TAG_CLOSE="?>"
```
Extend `phpBlock` and add the two block rules (sharing the existing `phpBlockBody` host; close optional):
```
phpBlock ::= phpRawBlock | phpEchoBlock | phpTagBlock | phpEchoTagBlock
phpTagBlock     ::= T_PHP_TAG_OPEN      phpBlockBody? T_PHP_TAG_CLOSE?
phpEchoTagBlock ::= T_PHP_ECHO_TAG_OPEN phpBlockBody? T_PHP_TAG_CLOSE?
```
This generates new PSI interfaces `AntlersPhpTagBlock` and `AntlersPhpEchoTagBlock` (plain
`ASTWrapperPsiElement`, like `AntlersPhpRawBlock`/`AntlersPhpEchoBlock`). `phpBlockBody` is unchanged.

### C. Injection — `src/main/kotlin/.../injection/AntlersPhpInjector.kt`

No change to `elementsToInjectIn()` (still `AntlersPhpBlockBody`) — the new blocks contain that host, so
they are injected automatically. Generalize `prefixFor`:
```kotlin
fun prefixFor(body: AntlersPhpBlockBody): String =
    if (body.parent is AntlersPhpEchoBlock || body.parent is AntlersPhpEchoTagBlock) "<?= " else "<?php "
```
So a literal `<?php … ?>` body injects as `<?php <body>` and a `<?= … ?>` body as `<?= <body>` — the same
mechanism already used for `{{? ?}}` / `{{$ $}}`.

### D. Highlighting — `src/main/kotlin/.../highlighting/AntlersSyntaxHighlighter.kt`

Add the three new delimiter tokens to the existing PHP-delimiter (`BRACES_KEYS`) branch alongside
`T_PHP_RAW_OPEN`/etc.:
```kotlin
AntlersTypes.T_PHP_TAG_OPEN, AntlersTypes.T_PHP_ECHO_TAG_OPEN, AntlersTypes.T_PHP_TAG_CLOSE -> BRACES_KEYS
```

### E. Hardening (part 3)

The prefix unification above already makes all four block kinds consistent. Add tests (no new production
code expected) covering:
- Empty body (`<?php ?>`, `<?= ?>`, `{{? ?}}`) → `phpBlockBody` absent → no injection host → graceful.
- Unclosed `<?php` at EOF → parses (close optional), body still an injection host.
- Multi-statement raw body → injected as one PHP fragment.
- Write-back: editing the injected PHP of a literal `<?php … ?>` round-trips through
  `AntlersPhpBlockBodyManipulator` (the same host/manipulator as `{{? ?}}`).

## Codegen discipline

Follow the recipe in memory `antlers-codegen-regen-recipe`:
1. Regenerate lexer and parser from the **unchanged** sources first; confirm byte-identical to the
   committed generated files (proves the toolchain is reproducible).
2. Apply the grammar token + rule changes; regenerate the parser (grammar-kit) so `AntlersTypes` gains the
   new constants and the new PSI classes appear.
3. Apply the lexer changes; regenerate the lexer (JFlex). It references the new `AntlersTypes` constants,
   so the parser must be regenerated first.

## Data flow

`<?php … ?>` in CONTENT → lexer `PHP_TAG` tokens → grammar `phpTagBlock` with a `phpBlockBody` host →
`AntlersPhpInjector` injects PHP with the `<?php ` prefix → PhpStorm renders/completes real PHP inside the
fragment; edits round-trip via `AntlersPhpBlockBodyManipulator`. `<?= … ?>` is identical with the `<?= `
prefix.

## Error handling / edge cases

- **Bare `<?` / `<?xml` / `<? `** — not matched by the `<?php`/`<?=` rules → stay outer HTML.
- **Unclosed `<?php`** (no `?>`) — lexer consumes to EOF as `T_PHP_TEXT`; grammar close is optional.
- **Empty tag** (`<?php ?>`) — no `T_PHP_TEXT`, so no `phpBlockBody`, so no injection (intended).
- **`<?php` inside `{{ }}` / comments / noparse** — those lexer states never transition to `PHP_TAG`, so
  it is not claimed there (correct).
- **PHP plugin absent** (IntelliJ Community) — injection is a graceful no-op (existing behavior); the tags
  are still lexed/highlighted as delimiters.
- **`<` heavy markup / `?` in attributes** (e.g. `href="x?y"`) — only a literal `<?` sequence ends the
  outer-HTML run, so ordinary tags and query-string attributes stay a single `T_OUTER_HTML` token. The
  rare exception is a `<?` literally inside markup (e.g. `href="<?"`), which splits the run; harmless
  (tokens concatenate in the HTML data document). Parser `.txt` fixtures are re-verified regardless.

## Testing

- **Lexer** (`LexerTest`): `<?php echo 1; ?>` → `T_PHP_TAG_OPEN, T_PHP_TEXT, T_PHP_TAG_CLOSE`;
  `<?= $x ?>` → `T_PHP_ECHO_TAG_OPEN, T_PHP_TEXT, T_PHP_TAG_CLOSE`; unclosed `<?php x` → open + text, no
  close; `<?xml ?>` and a plain `<div>{{ x }}</div>` keep their outer-HTML tokenization (assert no PHP
  tokens; assert the `{{` tag still opens).
- **Parser** (`ParsingTestCase` `.txt` fixture): a file mixing `<?php … ?>`, `<?= … ?>`, `{{ x }}`, and
  HTML parses into `phpTagBlock` / `phpEchoTagBlock` with a `phpBlockBody`.
- **Injection** test: `AntlersPhpInjector.getLanguagesToInject` (or `InjectedLanguageManager`) injects PHP
  into a `<?php ?>` body and a `<?= ?>` body; `prefixFor` returns `<?php ` and `<?= ` respectively for the
  new block kinds and the existing ones (regression).
- **Highlighting** test: the three new delimiter tokens map to `BRACES`.
- **Manipulator** round-trip test: editing the injected PHP body of a literal `<?php ?>` writes back
  correctly (mirrors the existing `{{? ?}}` round-trip test).
- **Hardening** tests: empty body, unclosed tag, multi-statement.
- **Codegen round-trip:** regen unchanged → byte-identical; then regen with changes.
- **Full-suite gate.** Re-verify/refresh any existing parser `.txt` fixtures affected by the catch-all
  change.

## Out of scope

- PHP as the template **data** language for `.antlers.php` (the larger "option 2"; not chosen).
- Folding, brace matching, and `<?php ?>`-aware commenting for the new blocks (possible later polish).
- Recognizing bare `<?` / `<?xml` processing instructions.
- Any change to Statamic runtime semantics — this is editor support only.
