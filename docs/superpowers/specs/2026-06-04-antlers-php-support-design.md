# Antlers `.antlers.php` Support + PHP Injection — Design

**Date:** 2026-06-04
**Branch:** `antlers-php-support`
**Status:** Approved approach, pending spec review
**Source:** Konafets/antlers-idea issues #20 (`.antlers.php` files) and #166 (`{{$ … $}}` PHP highlighting).

## Goal

1. **A — make `.antlers.php` first-class:** register the second Statamic view extension so the plugin
   activates on `.antlers.php`, and resolve partials/views across both extensions.
2. **B — inject real PHP into `{{$ … $}}` (echo) and `{{? … ?}}` (raw) blocks (#166):** the block body
   becomes a `PsiLanguageInjectionHost` and a `MultiHostInjector` injects the PHP language — so PHP gets
   syntax highlighting, errors/warnings, and completion inside those blocks (in both `.antlers.html` and
   `.antlers.php`).

**Out of scope (deferred):** native `<?php … ?>` outside Antlers blocks in `.antlers.php` (i.e. PHP as the
*template data language*) — the largest, nichest piece; left for a future cycle.

## Key constraint — PHP is optional, reached dependency-free

The JetBrains PHP language ships in the PHP plugin (PhpStorm / IDEA Ultimate + PHP), **not** in IDEA
Community / WebStorm, and **not** in our test IDE (`ideaIC`). So we take **no compile-time PHP
dependency**. The injector resolves PHP at runtime via `com.intellij.lang.Language.findLanguageByID("PHP")`
— a non-null `Language` when the plugin is loaded, `null` otherwise. PHP injection therefore lights up in
PhpStorm/Ultimate and **gracefully no-ops everywhere else** (no crash, no injection). Base Antlers tooling
on `.antlers.php` works in every IDE.

## Component A — `.antlers.php` is first-class

### A1. File-type pattern (`plugin.xml`)

Change the `<fileType … patterns="*.antlers.html">` registration to:
```xml
patterns="*.antlers.html;*.antlers.php"
```
Everything keyed off the Antlers `LanguageFileType` (lexer, parser, highlighting, completion, folding,
structure, formatter, injection) then applies to `.antlers.php` automatically. The template-data language
stays HTML (the existing `AntlersFileViewProvider` default) — native `<?php ?>` is rendered as HTML text
for now (that is the deferred Component C). `AntlersFileType.getDefaultExtension()` stays `antlers.html`
(the New-file default).

### A2. Partial & view resolution across both extensions

Five sites hardcode `.antlers.html`/`html`; each gains `.antlers.php`/`antlers.php`:

- `references/StatamicProject.kt`: the partial-resolution `exts = listOf("antlers.html", "html")` →
  `listOf("antlers.html", "antlers.php", "html")`; and the views dir-walk
  `endsWith(".antlers.html") || endsWith(".html")` + the matching `removeSuffix(...)` chain gain the
  `.antlers.php` cases.
- `references/AntlersPartialReference.kt`: the two `…removeSuffix(".antlers.html").removeSuffix(".html")`
  chains gain `.removeSuffix(".antlers.php")`.
- `references/AntlersPartialReferenceSearcher.kt`: the guard
  `if (!vf.name.endsWith(".antlers.html") && !vf.name.endsWith(".html")) return` also accepts
  `.antlers.php`.
- `blueprint/PageBlueprintResolver.kt`: the view-path `rel.removeSuffix(".antlers.html")` gains
  `.removeSuffix(".antlers.php")`.

Result: `{{ partial:foo }}` resolves to `foo.antlers.php`, Find-Usages/Rename span both extensions, and a
`.antlers.php` view maps to its collection blueprint. (Precedence on a name clash — `.antlers.html` vs
`.antlers.php` for the same handle — keeps the existing ordering: the list/`removeSuffix` order puts
`.antlers.html` first.)

## Component B — PHP injection into `{{$ … $}}` / `{{? … ?}}` (#166)

### B1. Grammar + host (parser regen)

Today: `phpBlock ::= T_PHP_RAW_OPEN T_PHP_TEXT? T_PHP_RAW_CLOSE | T_PHP_ECHO_OPEN T_PHP_TEXT? T_PHP_ECHO_CLOSE`.
Restructure so the body is a host node, and accept **multiple** `T_PHP_TEXT` tokens (the lexer emits the
body in pieces whenever it contains a `?`/`$`, which today's `T_PHP_TEXT?` cannot hold — a latent bug this
also fixes):
```
phpBlock ::= phpRawBlock | phpEchoBlock
phpRawBlock  ::= T_PHP_RAW_OPEN  phpBlockBody? T_PHP_RAW_CLOSE
phpEchoBlock ::= T_PHP_ECHO_OPEN phpBlockBody? T_PHP_ECHO_CLOSE
phpBlockBody ::= T_PHP_TEXT+ {
  mixin="com.github.balotias.intellijantlers.psi.AntlersPhpBlockBodyMixin"
  implements="com.intellij.psi.PsiLanguageInjectionHost"
}
```
`AntlersPhpBlockBodyMixin` mirrors `AntlersFrontMatterBodyMixin`: `isValidHost()=true`,
`createLiteralTextEscaper()=LiteralTextEscaper.createSimple(this)`,
`updateText(...)=ElementManipulators.handleContentChange(this, …)`. A registered
`editor/AntlersPhpBlockBodyManipulator` (`<lang.elementManipulator>`) rebuilds the body by parsing a
synthetic `{{? <new body> ?}}` and splicing it, with the same round-trip guard the front-matter
manipulator uses (bail if a `?}}` in the new content would re-close the synthetic block early).

The delimiters `T_PHP_*_OPEN`/`_CLOSE` keep their existing `BRACES` highlight; the body is colored by the
injected PHP.

### B2. Injector — `injection/AntlersPhpInjector : MultiHostInjector`

- `elementsToInjectIn() = listOf(AntlersPhpBlockBody::class.java)`.
- A **pure helper** (unit-testable without the PHP plugin) decides the injection shape from the host:
  `phpInjectionPrefix(host): String` → `"<?= "` when the host's block is an **echo** block
  (`{{$ … $}}`, a PHP expression), else `"<?php "` (raw block `{{? … ?}}`, statements). Raw vs echo is read
  from the host's parent rule type (`AntlersPhpEchoBlock` vs `AntlersPhpRawBlock`).
- `getLanguagesToInject(registrar, context)`:
  ```
  if (context !is AntlersPhpBlockBody) return
  val php = Language.findLanguageByID("PHP") ?: return         // PHP plugin absent → no-op
  registrar.startInjecting(php)
      .addPlace(phpInjectionPrefix(context), "", context, TextRange(0, context.textLength))
      .doneInjecting()
  ```
- Registered: `<multiHostInjector implementation="…AntlersPhpInjector"/>`.

(The `<?= ` / `<?php ` prefixes make the PHP parser enter PHP mode and read the body as an expression /
statements respectively; suffix is empty — injection handles end-of-fragment. The exact prefixes are
verified manually in PhpStorm; they are isolated in the pure helper so they are easy to adjust.)

## Data flow

`.antlers.php` or `.antlers.html` → lexer emits `{{?`/`{{$` … `T_PHP_TEXT+` … `?}}`/`$}}` → grammar:
`phpBlockBody` (injection host) → `AntlersPhpInjector` resolves PHP via `findLanguageByID` → PHP injected
with the right prefix → PHP highlighting/analysis inside the block (when the PHP plugin is present).

## Error handling / edge cases

- PHP plugin absent (Community/WebStorm/CI) → injector returns early; the block body is a plain (uncolored)
  host; no crash.
- Empty body (`{{? ?}}` / `{{$ $}}`) → no `T_PHP_TEXT` → no `phpBlockBody` → injector never fires.
- A `?`/`$` inside the body → multiple `T_PHP_TEXT` tokens, all grouped under one `phpBlockBody` (the
  `T_PHP_TEXT+` fix); the injected text is their concatenation = the verbatim body.
- Manipulator write-back where new content contains `?}}` → round-trip guard bails (no truncation).
- Name clash between `foo.antlers.html` and `foo.antlers.php` → `.antlers.html` wins (existing list order).

## Testing

**Component A (Community-testable):**
- A `.antlers.php` file is recognized as Antlers (`myFixture.configureByText("p.antlers.php", …)` →
  `file.language == AntlersLanguage` / `fileType is AntlersFileType`), and Antlers completion/lexing works
  in it.
- `{{ partial:foo }}` resolves when the partial is `foo.antlers.php` (reference resolve test, mirroring the
  existing partial tests but with a `.antlers.php` target); Find-Usages/Rename pick it up.
- A `.antlers.php` view maps to its blueprint (page-blueprint resolver test) like the `.antlers.html` case.

**Component B:**
- **Grammar/corpus:** `{{? $x = 1; ?}}` and an echo `{{$ $x ?: $y $}}` (body containing `?`) parse into
  `phpRawBlock`/`phpEchoBlock` + `phpBlockBody` with **no `PsiErrorElement`**; existing `testPhpBlock`
  stays green (updated golden if the node names changed).
- **Pure helper (no PHP plugin needed):** `phpInjectionPrefix` returns `"<?= "` for an echo-block body and
  `"<?php "` for a raw-block body.
- **Graceful no-op:** in our Community CI (no PHP), `InjectedLanguageManager.findInjectedElementAt(...)`
  inside a `{{? … ?}}` body returns `null` and nothing throws (proves the `findLanguageByID` guard).
- **Manual / documented (PhpStorm only):** real PHP highlighting + errors inside the blocks — not in CI.
- Full-suite gate.

## Out of scope

- Native `<?php … ?>` outside Antlers blocks in `.antlers.php` (PHP as template-data language) — deferred
  Component C.
- A hard PHP-plugin dependency or a separate optional `config-file` module (not needed — `findLanguageByID`
  covers it).
- Antlers-aware analysis of the PHP (variable bridging between Antlers and the injected PHP).
