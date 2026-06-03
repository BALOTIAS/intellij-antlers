# Antlers Grammar Fixes: Bound Params & Colon-Arg Modifiers — Design

**Date:** 2026-06-03
**Branch:** `antlers-grammar-param-modifier-fixes`
**Status:** Approved approach, pending spec review

## Goal

Fix two grammar limitations surfaced by the parser-corpus expansion (both produce error-free but
structurally-wrong PSI today):

1. **Bound parameter after a tag name** — `{{ partial :src="hero" }}`: the `:src` is swallowed into the
   `namePath` (`partial:src`) and `="hero"` falls out as loose tokens, instead of parsing as a
   `boundParameter_`.
2. **Colon-argument modifier** — `{{ x | truncate:10 }}`: the modifier rule only accepts `(…)` args, so
   `:10` falls out as loose tokens instead of being part of the modifier.

Both are real Antlers syntax that should parse into the correct PSI (affecting parameter/modifier-arg
recognition for any structure-aware feature). The fixes are **grammar-only** edits to `Antlers.bnf`,
needing a regen of **only** `AntlersParser.java` (no new tokens, no new PSI node types → no lexer or PSI
class changes).

## Background (current grammar — `src/main/grammar/Antlers.bnf`)

```
private expr_ ::= namePath tail_* | tail_+
private tail_ ::= parameter | modifier | exprToken_
namePath ::= pathSegment ((T_COLON | T_DOT) pathSegment | bracketAccess)* { mixin=AntlersNamePathMixin }
parameter ::= boundParameter_ | staticParameter_ { mixin=AntlersParameterMixin }
private boundParameter_ ::= T_COLON T_DOLLAR? T_IDENT (T_EQUALS paramValue)? | T_DOLLAR T_IDENT
modifier ::= T_PIPE T_IDENT (T_LPAREN argList? T_RPAREN)? { mixin=AntlersModifierMixin }
```

- For `partial :src="hero"`, `namePath`'s `(T_COLON pathSegment)*` consumes `:src` before `parameter`
  can apply (whitespace is skipped, so it can't disambiguate by the space).
- For `| truncate:10`, `modifier` stops after `truncate` (next is `:`, not `(`); `:10` is left to
  `tail_` → `exprToken_`.

## Components

### 1. Toolchain round-trip gate (validate regen BEFORE any change)

`grammar-kit-2022.3.2` + `gradle-grammarkit-plugin-2022.3.2.2` are cached locally and produced the
current `gen/`. The grammarkit gradle plugin is intentionally NOT applied (it breaks `:test` platform
resolution — see `build.gradle.kts`). So:

- **Round-trip first:** temporarily apply grammarkit + a `GenerateParserTask`, regenerate
  `AntlersParser.java` from the **unchanged** `.bnf` (with `--no-configuration-cache`), and `git diff` it.
  Expect **no material parser-logic differences** (ignore a differing generated header/version banner or
  whitespace-only churn). If the only diff is the banner, the cached toolchain matches → proceed (and we
  accept that the regenerated header may change). If parser *logic* differs from the committed file →
  STOP and report (the committed `gen/` was produced by a different toolchain; regenerating would be
  unsafe / noisy).
- After validating, revert the build.gradle change and clear the configuration cache; confirm
  `./gradlew --rerun-tasks test` still passes (proves the temporary apply left no residue).

### 2. Grammar edit — bound param disambiguation

```
namePath ::= pathSegment ((T_COLON pathSegment !T_EQUALS) | T_DOT pathSegment | bracketAccess)* {
  mixin="com.github.balotias.intellijantlers.psi.AntlersNamePathMixin"
}
```

The trailing `!T_EQUALS` not-predicate (non-consuming) makes the colon-segment alternative fail when the
token after `pathSegment` is `=`, so `:src=` is not absorbed and `boundParameter_` matches
`:src="hero"`. `{{ collection:blog }}`, `{{ tag:method }}`, `{{ foo:bar:baz }}` (no following `=`) are
unchanged. The `T_DOT pathSegment` and `bracketAccess` branches are unchanged.

### 3. Grammar edit — colon-arg modifier

```
modifier ::= T_PIPE T_IDENT ((T_LPAREN argList? T_RPAREN) | (T_COLON modifierArg_)+)? {
  mixin="com.github.balotias.intellijantlers.psi.AntlersModifierMixin"
}
private modifierArg_ ::= T_STRING | T_NUMBER | T_DOLLAR? T_IDENT
```

`modifierArg_` is **private** (no new PSI node). Supports `truncate:10`, `format:'Y-m-d'`, `:$var`, and
multi-arg `truncate:10:20`. The existing `(…)` arg form is kept as the first alternative.
`AntlersModifierMixin.modifierName` (first `T_IDENT`) still returns `truncate`. Dotted colon-args
(`:a.b`) are out of scope — they degrade as before (rare).

### 4. Regenerate & reconcile

Regenerate `AntlersParser.java` from the edited `.bnf`. Confirm only `AntlersParser.java` changed under
`gen/` (no PSI interface/impl/lexer diffs — proving the private-rule assumption). Compile.

## Architecture

Two localized `.bnf` rule edits + a regenerated `AntlersParser.java`. No tokens, no PSI node types, no
lexer, no runtime-Kotlin changes. The mixins/classifier/annotators are leaf-based and unaffected.

## Downstream impact (verify, expect none)

- `AntlersCompletionContext.classify` is leaf-based (previous significant leaf), not PSI-structure based;
  the `T_EQUALS`/bound-param and `T_PIPE`/modifier branches still classify the same. **Spot-check** the
  param-value classifier on `{{ partial :src="<caret>" }}` still yields `PARAMETER_VALUE`/handled.
- `AntlersModifierMixin.modifierName`, `AntlersNamePathMixin` head/segments, the balance annotator, and
  the semantic highlighter all read the first/relevant `T_IDENT` — unchanged.

## Testing

- **Corpus before/after (the proof):** the `BoundParam.txt` and `ModifierChain.txt` expected PSI dumps
  (pinned in the coverage work as the *degraded* parse) are regenerated to the *correct* trees —
  `BoundParam` now contains an `AntlersParameter` for `:src="hero"`; `ModifierChain`'s `:10` is inside
  the `AntlersModifier`. Re-run `AntlersParsingTest` green.
- **Focused PSI assertions** (`AntlersGrammarParamModifierTest`, new): parse `{{ partial :src="hero" }}`
  and assert a `PsiTreeUtil.findChildOfType(file, AntlersParameter::class.java)` exists with the `:src`
  bound name; parse `{{ title | truncate:10 }}` and assert the single `AntlersModifier` spans
  `truncate:10` (modifierName == "truncate"); assert zero `PsiErrorElement`s for both. Regression:
  `{{ collection:blog }}{{ /collection:blog }}` still parses `collection:blog` as one name-path
  (shorthand unaffected).
- **Full-suite gate** — the grammar change touches the foundation; the existing 333 tests (parsing,
  completion, scope, annotators, shorthand) must stay green.

## Out of scope

- Dotted colon-modifier args (`| x:a.b`).
- Any new completion/highlighting for colon-modifier args (the fix is structural correctness; value
  completion for `:`-args can be a later enhancement).
- Lexer/token changes.
