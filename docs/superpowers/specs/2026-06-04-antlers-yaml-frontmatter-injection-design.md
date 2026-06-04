# Antlers View Front Matter — Real YAML Language Injection — Design

**Date:** 2026-06-04
**Branch:** `antlers-yaml-frontmatter-injection`
**Status:** Approved approach, pending spec review
**Supersedes:** the "B-visual" annotator from `2026-06-03-antlers-view-front-matter-design.md` (this is the
deferred **B-real** follow-up).

## Goal

Make a view's `---…---` front matter a **real YAML editing island**, so:

```
---
name: '' # The name of the icon
filled: false # Whether the icon should be filled or outlined
class: 'inline-block' # Additional classes to be added to the container
fill: false
---
```

YAML comments (`# …`) are colored, scalars/keys are highlighted by the real YAML highlighter, and the
IDE surfaces YAML **errors/warnings** (and YAML completion) inside the block — by injecting the
`YAMLLanguage` into the front-matter content.

This is the largest single change in the project (lexer + parser regen + a language-injection host).
The existing `{{ view:foo }}` completion/navigation/docs and the `ViewFrontMatterScanner` are **text-
based and stay unchanged**; only the *rendering/parsing* of the block becomes real YAML. The B-visual
front-matter annotator from #5 is **retired** (real YAML supersedes it).

## Why a lexer carve-out is required

`AntlersFileViewProvider` splits the document via a `TemplateDataElementType` that copies **only**
`T_OUTER_HTML` token text into the HTML data tree and replaces every other token with an
`ANTLERS_FRAGMENT` outer-language placeholder. Front matter is currently `T_OUTER_HTML`, so the HTML tree
claims its text — which makes a clean YAML injection impossible (two languages over one range). The
moment the front matter is lexed as its **own** tokens (not `T_OUTER_HTML`), the template-data split
automatically excludes it from HTML (it becomes a fragment placeholder there), leaving a clean Antlers-
side host to inject YAML into.

(Approach B — overriding `TemplateDataElementType` segmentation to hide the front matter without a lexer
change — was considered and rejected: it lives in the exact template-data machinery the project has been
burned by, and still needs a conditional injection host.)

## Components

### 1. Lexer (`src/main/grammar/AntlersLexer.flex` → JFlex regen)

New tokens: `T_FRONTMATTER_FENCE`, `T_FRONTMATTER_TEXT`. New states: `CONTENT`, `FRONTMATTER`.

- `YYINITIAL` becomes a **one-shot START dispatcher** (only re-entered via `reset()`, i.e. once per
  file):
  - `"---" [ \t]* \R` → `yybegin(FRONTMATTER); return T_FRONTMATTER_FENCE;` (the opening fence + its
    newline).
  - any other character → `yybegin(CONTENT); yypushback(1);` (no token emitted — hand the char to
    `CONTENT` to lex normally). This guarantees front matter is recognized **only at offset 0**.
- All current `<YYINITIAL>` content rules move verbatim into a new `<CONTENT>` state. Every transition
  that today does `yybegin(YYINITIAL)` (after `}}` in `EXPR`, and after the comment / PHP-raw / PHP-echo
  / noparse closers) now does `yybegin(CONTENT)`.
- `<FRONTMATTER>`, rules in priority order:
  1. `"---" [ \t]* (\R | $)` → `yybegin(CONTENT); return T_FRONTMATTER_FENCE;` (the closing fence).
  2. `[^\n]* \R` → `T_FRONTMATTER_TEXT` (a content line incl. its newline).
  3. `[^\n]+` → `T_FRONTMATTER_TEXT` (a final line without a trailing newline, at EOF).

  Per-line content tokens are deliberate: JFlex (DFA) cannot non-greedily match "everything up to the
  next `---` line," so we tokenize line-by-line with the closing-fence rule taking priority.

**Unclosed front matter (documented limitation):** the lexer commits to `FRONTMATTER` on the opening
fence and cannot look ahead for a closing `---`. A file that starts with `---` and never closes it is
therefore lexed as front-matter text to EOF (YAML is injected over the remainder, which will surface
YAML errors). This matches Statamic's requirement that front matter be closed; the well-formed case is
unaffected. (Current #5 behavior for an unclosed block — treat as plain HTML — is the regression we
accept here.)

### 2. Grammar + PSI host (`src/main/grammar/Antlers.bnf` → parser regen)

```
antlersFile ::= frontMatter? node_*
frontMatter ::= T_FRONTMATTER_FENCE frontMatterBody? T_FRONTMATTER_FENCE?
frontMatterBody ::= T_FRONTMATTER_TEXT+ {
  mixin="com.github.balotias.intellijantlers.psi.AntlersFrontMatterBodyMixin"
  implements="com.intellij.psi.PsiLanguageInjectionHost"
}
```

(The closing fence is optional in the rule so the unclosed case still parses without error elements.)

`AntlersFrontMatterBodyMixin` (new, in `psi/AntlersMixins.kt` or its own file) implements
`PsiLanguageInjectionHost`:
- `isValidHost(): Boolean = true`
- `createLiteralTextEscaper(): LiteralTextEscaper<…> = LiteralTextEscaper.createSimple(this)` (YAML body
  is verbatim — no escaping)
- `updateText(text: String): PsiLanguageInjectionHost = ElementManipulators.handleContentChange(this, text)`

A registered `AbstractElementManipulator<AntlersFrontMatterBody>`
(`editor/AntlersFrontMatterBodyManipulator`, `<lang.elementManipulator forClass=…>`) implements
`handleContentChange` by replacing the body element's text (drives injected-fragment write-back).
`getRangeInElement` = the whole element.

The body element's text is exactly the YAML source (the fences are separate sibling tokens), so the
injection covers `TextRange(0, host.textLength)`.

### 3. Injector — `injection/AntlersYamlInjector : MultiHostInjector`

- `elementsToInjectIn(): List<Class<out PsiElement>> = listOf(AntlersFrontMatterBody::class.java)`
- `getLanguagesToInject(registrar, context)`: when `context` is an `AntlersFrontMatterBody`,
  `registrar.startInjecting(YAMLLanguage.INSTANCE).addPlace(null, null, host, TextRange(0, host.textLength)).doneInjecting()`.
- Registered: `<multiHostInjector implementation="com.github.balotias.intellijantlers.injection.AntlersYamlInjector"/>`.
- `YAMLLanguage` is `org.jetbrains.yaml.YAMLLanguage` from the bundled YAML plugin.

### 4. YAML plugin dependency

- `build.gradle.kts` → `intellijPlatform { … bundledPlugin("org.jetbrains.plugins.yaml") }`.
- `plugin.xml` → `<depends>org.jetbrains.plugins.yaml</depends>`.

### 5. Syntax highlighter (`highlighting/AntlersSyntaxHighlighter`)

`getTokenHighlights`: `T_FRONTMATTER_FENCE` → `FRONTMATTER_FENCE` (the existing key, retained). The body
is colored by the injected YAML highlighter, so no Antlers coloring is applied there.

### 6. Retire the B-visual annotator

- Delete `editor/AntlersFrontMatterHighlightAnnotator.kt`, its `<annotator>` registration, and
  `editor/AntlersFrontMatterHighlightTest.kt`.
- Remove the `FRONTMATTER_KEY` and `FRONTMATTER_VALUE` keys from `AntlersSyntaxHighlighter` and their
  `AttributesDescriptor`s + demo tags (`fmkey`/`fmval`) from `AntlersColorSettingsPage`; keep
  `FRONTMATTER_FENCE` (now a real lexer-token color). Update `AntlersColorSettingsPageTest` (it gates the
  exact key/tag sets).

### 7. Unchanged

`ViewFrontMatterScanner` (reads `file.text`), `ViewFrontMatterService`, `AntlersViewVariableReference` /
`AntlersViewVarDeclaration`, the `view:` completion branch, and the docs branch — all text-based and
independent of how the block is tokenized. Their tests stay green.

## Data flow

`.antlers.html` → lexer: leading `---…---` → `T_FRONTMATTER_FENCE` / `T_FRONTMATTER_TEXT*` /
`T_FRONTMATTER_FENCE`; rest → `CONTENT` as before. Template-data split: front-matter tokens are not
`T_OUTER_HTML` → become an `ANTLERS_FRAGMENT` placeholder in the HTML tree (HTML ignores them). Antlers
tree: `frontMatter` → `frontMatterBody` (injection host). `AntlersYamlInjector` injects `YAMLLanguage`
into the body → YAML highlighting + annotator (errors/warnings) + completion inside the block.

## De-risking (fail-fast, before the real changes)

**Task 0:** (a) **JFlex round-trip** — regenerate `_AntlersLexer.java` from the *unchanged* `.flex` with
the cached `org.jetbrains.intellij.deps.jflex:jflex:1.9.2` jar (`java -cp <jflex.jar> jflex.Main -d
<tmp> src/main/grammar/AntlersLexer.flex`) and diff against the committed file → must be byte-identical
(modulo a header banner). If not, STOP — the toolchain differs from how the lexer was generated.
(b) **Minimal injection probe** (throwaway) — prove that injecting `YAMLLanguage` into a trivial host
yields YAML highlighting + a YAML error inside a `---` block in a `BasePlatformTestCase`. If either
fails, STOP and reassess (fall back to Approach B or report).

## Testing

- **Lexer** (`LexerTest`): leading `---\n…\n---\n` → `T_FRONTMATTER_FENCE`, `T_FRONTMATTER_TEXT`+,
  `T_FRONTMATTER_FENCE`; a file with no leading `---` → unchanged `T_OUTER_HTML` stream; a `---` line
  *not* at offset 0 → plain outer HTML (no front-matter tokens); unclosed → text-to-EOF.
- **Parsing corpus** (`AntlersParsingTest`): `FrontMatter.antlers.html` → tree shows
  `frontMatter`/`frontMatterBody`; existing corpus files unchanged.
- **Injection** (`AntlersYamlInjectionTest`): inside the block,
  `InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, offset)` (or
  `getInjectedPsiFiles`) resolves to a `YAMLLanguage` PSI element; a position outside the block does not;
  HTML/CSS completion in the body still works (smoke).
- **Regression:** the #5 `view:` completion / reference / docs / scanner / service tests stay green; the
  full suite passes; the B-visual highlight test is removed (superseded). Confirm folding / balance /
  structure ignore the `frontMatter` node (it is not an `AntlersStatement`).

## Out of scope

- Driving `view:` completion/resolution from the injected YAML PSI (it stays text-based via the scanner;
  a future enhancement could read YAML keys for nested/typed access).
- YAML *schema* validation of front-matter keys (no Statamic-specific schema; the plain YAML annotator's
  syntax errors/warnings are what we surface).
- Front matter anywhere but the very top of the file; multiple front-matter blocks.
- Graceful handling of an unclosed opening fence (documented limitation above).
