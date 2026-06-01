# Antlers Grammar & Structural Completion — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Sub-projects **A** (lexer + grammar + PSI) and **B** (structural completion) of the
"full Statamic 6 Antlers compatibility" effort.

## 1. Background & Goal

`intellij-antlers` is a from-scratch JetBrains IDE plugin for Statamic **Antlers** templates
(`*.antlers.html`). The current grammar is a placeholder: inside `{{ }}` it lexes a flat token
soup (`tag_content ::= (IDENTIFIER | STRING | … )*`) with no notion of tags, parameters,
modifiers, conditions, or the non-`{{ }}` delimiter types. Completion is a single contributor that
dumps every native tag name regardless of caret position.

This spec covers replacing that foundation with:

- **A.** A lexer/grammar/PSI that recognises the real Antlers syntax surface *structurally*.
- **B.** Position-aware completion for **tags, tag methods, parameters, and modifiers**, backed by a
  hybrid catalog (bundled Statamic 6 data + project scan for custom tags/modifiers).

Variable resolution, go-to-definition, documentation, and editor niceties are **out of scope** here
and become their own specs (sub-projects C and D).

### Antlers syntax surface (reference)

The grammar must account for (sourced from <https://statamic.dev/antlers>):

- Delimiters: `{{ }}` (expression), `{{# #}}` (comment), `{{? ?}}` (raw PHP), `{{$ $}}` (PHP echo),
  `@{{ }}` (escaped/literal), and `{{ noparse }}…{{ /noparse }}` (raw text region).
- Variables with three access syntaxes: `array:0`, `array.0`, `array[0]`; dynamic keys
  `data[field_var]`; disambiguation `$variable`; `next:`/`prev:`.
- Tags: paired `{{ tag:method params }}…{{ /tag:method }}`, self-closing `{{ tag:method /}}`,
  disambiguation `%tag`.
- Parameters: static `limit="5"`, interpolated `from="{var}"`, bound `:param="expr"`, shorthand
  `:$param`, void values.
- Modifiers: pipe `| upper`, chained `| a | b`, with args `| replace('a','b')`.
- Conditions: `if`/`elseif`/`else`/`unless`/`endif`/`endunless`, `switch(...)`, the full operator
  family (`== === != !== > >= < <= <=>`, `&& || ! xor and or not`, `?? ??? ?= ?:` ternary),
  arithmetic (`+ - * / % **`), assignment + compound assignment, `=>` lambdas.
- Data operators: `where`, `orderby`, `groupby`, `take`, `skip`, `pluck`, `merge`, `as`.
- Literals: strings (`'`/`"`), arrays `['a', 1, true]`, numbers, `true`/`false`/`null`.
- Statement terminator `;`, sub-expressions `( … )`.

## 2. Design Principles

1. **Structural, not evaluative.** The parser recognises *where you are* (tag head, parameter slot,
   after a pipe, inside a condition) without modelling full operator precedence. This is robust on
   the half-typed input that completion always sees, and is far cheaper to build and maintain.
2. **Permissive & error-recovering.** Incomplete/invalid expressions must still produce a usable
   tree. Deep expression interiors fall back to a permissive token sequence.
3. **Don't decide tag-vs-variable in the grammar.** Antlers resolves that at runtime (registered tag
   name → tag, else variable). Both share one head shape; the completion/semantic layer interprets
   intent.
4. **Flat tag-pairing.** Open statements and `{{ /name }}` closers are siblings, not nested
   parent/child — any variable might be a pair and you cannot know statically.
5. **Preserve the HTML template-data contract.** The recently fixed `TemplateDataElementType` split
   (outer HTML → HTML parser, `{{ }}` → hidden `ANTLERS_FRAGMENT` placeholders) must keep working;
   HTML tag + CSS class completion is protected by a regression test.
6. **Platform-only.** No PHP-plugin dependency; works in every JetBrains IDE. Project scanning uses
   `FilenameIndex` + regex, not the PHP PSI.

## 3. Sub-project A — Lexer, Grammar, PSI

### 3.1 Lexer (`src/main/grammar/AntlersLexer.flex`, rewrite)

State-based JFlex lexer. States:

| State | Entered by | Emits |
|-------|-----------|-------|
| `YYINITIAL` | — | `T_OUTER_HTML` for outer content; `@{{` recognised here and emitted as literal outer text (escape) |
| `EXPR` | `{{` | expression tokens (below) |
| `COMMENT` | `{{#` | comment text until `#}}` |
| `PHP` | `{{?` / `{{$` | one opaque raw-PHP text token until `?}}` / `$}}` (not parsed in this spec) |
| `NOPARSE` | `{{ noparse }}` | raw text until `{{ /noparse }}` |

**Expression tokens:** open/close delimiters; `IDENT` = `[A-Za-z_][A-Za-z0-9_-]*`; punctuation
`: . [ ] ( ) | , ; / = @ $`; `=>`; `STRING` (single/double, with escapes); `NUMBER` (int/float);
operator family (`== === != !== > >= < <= <=>`, `&& || ! ?? ??? ?= ?`, `+ - * / % **`, compound
assigns). Keywords (`if elseif else unless endif endunless and or not xor where orderby groupby take
skip pluck merge as switch true false null`) are **not** distinct tokens — they lex as `IDENT` and
are recognised contextually by the parser, so they never shadow variable names.

**Hard constraint:** every expression token type must be distinct from `T_OUTER_HTML` so the
template-data split continues to hide `{{ }}` from the HTML parser.

### 3.2 Grammar (`src/main/grammar/Antlers.bnf`, rewrite)

GrammarKit BNF, permissive, with pin/recover rules for error recovery:

```
antlersFile  ::= node*
node         ::= outerHtml | comment | phpBlock | noparseBlock | statement
statement    ::= '{{' body '}}'
body         ::= closingTag | keywordStmt | exprStmt
closingTag   ::= '/' namePath                       // {{ /collection:blog }}
keywordStmt  ::= ('if' | 'elseif' | 'unless') expr   // condition heads
             |   'else' | 'endif' | 'endunless'
exprStmt     ::= namePath tail*                      // tag OR variable, same shape
tail         ::= parameter | modifier | exprToken    // exprToken = permissive operator soup
namePath     ::= IDENT ( (':' | '.') IDENT | '[' expr ']' )*
parameter    ::= ':'? ('$'? IDENT) ('=' paramValue)?
modifier     ::= '|' IDENT ( '(' argList? ')' )?
paramValue   ::= STRING | '{' expr '}' | primary     // {interpolation} captured as a child
argList      ::= expr (',' expr)*
```

`exprToken` absorbs operators/literals/sub-expressions that aren't a parameter or modifier, keeping
deep expressions parseable without precedence modelling.

### 3.3 PSI

Generated PSI plus thin mixins exposing IDE-relevant accessors:

| Node | Accessors |
|------|-----------|
| `AntlersStatement` | the whole `{{ … }}` |
| `AntlersTag` | `getName()`, `getMethod()`, `getParameterList()`, `isClosing()` |
| `AntlersVariable` | `getPath()` segments, `getModifiers()` |
| `AntlersParameter` | `getName()`, `isBound()` (`:`), `getValue()` |
| `AntlersModifier` | `getName()`, `getArguments()` |
| `AntlersCondition` | `getKeyword()`, condition body |
| `AntlersClosingTag` | `getName()`, `getMethod()` |
| `AntlersComment`, `AntlersPhp`, `AntlersNoparse` | raw spans |

Mixins live in `psi/` alongside generated sources (configured via `mixin`/`implements` in the BNF).

## 4. Sub-project B — Structural Completion

### 4.1 Providers

`AntlersCompletionContributor` registers four providers, each gated by a `PsiElementPattern` over the
new PSI. The old `AntlersCompletionProvider` (inside-`{{ }}` text gate + unconditional tag dump) is
**deleted**.

| Provider | Fires when caret is… | Offers / insert behaviour |
|----------|----------------------|---------------------------|
| `TagNameCompletionProvider` | head identifier of a statement, incl. right after `{{ ` or `{{ /` | core + custom tag names; reuses existing `AntlersTagInsertHandler` (pair vs single) |
| `TagMethodCompletionProvider` | right after `knowntag:` (e.g. `collection:`, `nav:`, `partial:`) | that tag's methods/handles |
| `ParameterCompletionProvider` | a name slot inside a known tag's head (whitespace boundary, not inside a value) | that tag's parameters → inserts `name="<caret>"`; also offers `:name` bound form |
| `ModifierCompletionProvider` | right after a `\|` | modifier names → inserts `name`, or `name(<caret>)` if it takes args |

Variable-name completion is **deferred** to sub-project C.

### 4.2 Catalog (`catalog/`)

Project-level `AntlersCatalogService` is the single source of truth:

```kotlin
data class TagDef(name, description, docUrl, isPair, methods: List<String>,
                  parameters: List<ParamDef>)
data class ParamDef(name, description, type, required, acceptsBinding)
data class ModifierDef(name, description, docUrl, parameters: List<ParamDef>)
```

- **Bundled core:** `resources/catalog/tags.json` + `modifiers.json`, authored from the Statamic 6
  docs. Loaded once and cached. `description` + `docUrl` are carried so the future documentation
  provider (sub-project C) reuses the same data with no new plumbing.
- **Project scan:** `catalog/scan/TagScanner` (the relocated, extended `AntlersCustomTagFinder`,
  regex over `**/Tags/*.php`) plus a new `ModifierScanner` (`**/Modifiers/*.php`). Results merge over
  the bundled set, cached with PSI-modification-tracker invalidation so new custom tags/modifiers
  appear without restart.

**v1 catalog breadth:** ship the complete list of all core tag **names** and modifier **names** (full
name-completion coverage immediately), with rich parameter + description data for the high-value tags
first (`collection`, `nav`, `form`, `partial`, `asset`/`assets`, `taxonomy`, `glide`, conditions,
`cache`, …). The JSON format makes filling in the rest pure data-authoring — coverage grows
incrementally without code changes.

## 5. File / Package Layout

```
src/main/grammar/      AntlersLexer.flex (rewrite)   Antlers.bnf (rewrite) → src/main/gen/
psi/                   generated PSI + mixins (AntlersTagMixin, AntlersVariableMixin, …)
completion/            AntlersCompletionContributor (pattern-based)
                       providers/ {TagName, TagMethod, Parameter, Modifier}CompletionProvider
                       AntlersTagInsertHandler (kept as-is)
catalog/               AntlersCatalogService, models (TagDef/ParamDef/ModifierDef)
                       scan/ {TagScanner, ModifierScanner}
resources/catalog/     tags.json, modifiers.json
```

Removals: `completion/AntlersCompletionProvider.kt` (replaced by the four providers);
`completion/AntlersCustomTagFinder.kt` moves to `catalog/scan/TagScanner.kt`.

## 6. Testing

| Layer | Tooling | Coverage |
|-------|---------|----------|
| Lexer | token-sequence asserts (extend `LexerTest`) | each delimiter type, `@{{` escape, `noparse`, operators, edge cases |
| Parser | `ParsingTestCase` + golden PSI dumps | tags, params, modifiers, conditions, comments, php, access syntaxes |
| Completion | `BasePlatformTestCase` | each provider fires in the right spot; negative (no Antlers in plain HTML); regression (HTML tag + CSS class completion still works) |
| Catalog | plain unit tests | JSON loads/parses; custom tag/modifier scan merges correctly |

Tests execute in the developer's environment via `./gradlew test`. The Claude sandbox cannot fetch
the IntelliJ test runtime, so changes are verified there with `./gradlew compileTestKotlin` +
golden-file review.

## 7. Out of Scope (future specs)

- **C — Semantic layer:** variable-name/value completion from blueprints, partial-path resolution,
  go-to-definition, documentation provider wiring, PHP-PSI-powered discovery.
- **D — Editor niceties:** brace matching, auto-close `}}`, commenter, folding, formatter, structure
  view.
- Full operator-precedence expression evaluation / semantic analysis.

## 8. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Lexer rewrite breaks the HTML template-data split | Regression test asserting HTML tag + CSS class completion; keep `T_OUTER_HTML` semantics and ANTLERS_FRAGMENT contract |
| Structural grammar mis-detects parameter vs. operator expression | Permissive `tail*` + error recovery; completion keys on coarse position, not exact parse; golden parser tests pin behaviour |
| `noparse` / escaped `@{{` edge cases in the lexer | Dedicated lexer states + explicit tests for each |
| Catalog drifts from Statamic releases | JSON data is easy to update; names-complete-first strategy limits blast radius of missing parameter data |
| Catalog scan performance on large projects | Cache with modification-tracker invalidation; restrict scan globs to `Tags/`/`Modifiers/` dirs |
