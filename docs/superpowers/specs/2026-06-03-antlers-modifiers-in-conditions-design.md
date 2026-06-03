# Antlers Modifiers in Conditions — Design

**Date:** 2026-06-03
**Branch:** `antlers-modifiers-in-conditions`
**Status:** Approved approach, pending spec review

## Goal

Parse modifiers inside conditions so `{{ if code | contains("<style") }}`, `{{ if title | lower == 'x' }}`,
`{{ unless items | count > 0 }}`, etc. parse cleanly (today they produce `PsiErrorElement`s, which is
what "fancy modifiers like `contains()` break the syntax highlighting" reported — the `<style` string is
a red herring; the real cause is the `|`). As a bonus, modifiers in conditions get highlighted like
modifiers elsewhere.

## Background (root cause, verified)

`Antlers.bnf`:
```
condition ::= <<atConditionKeyword>> conditionKeyword exprToken_* { mixin=AntlersConditionMixin }
private exprToken_ ::= T_OP | T_STRING | T_NUMBER | T_ARROW | T_DOT | T_COLON | T_COMMA | T_SEMICOLON
                     | T_EQUALS | T_DOLLAR | T_AT | T_LPAREN | T_RPAREN | T_LBRACKET | T_RBRACKET
                     | T_LBRACE | T_RBRACE | T_IDENT | T_SLASH
```
`exprToken_` does **not** include `T_PIPE`, so a `| modifier` in a condition cannot be consumed and the
parse fails. Verified: `{{ if x | upper }}` → 2 errors; `{{ if x | contains("style") }}` → 2 errors (no
`<` needed); `{{ x | contains("<style") }}` (modifier outside a condition) → **0** errors. So the gap is
modifiers in conditions, not the `<` or `contains`.

## Components

### 1. Grammar — one rule

```
condition ::= <<atConditionKeyword>> conditionKeyword (modifier | exprToken_)* {
  mixin="com.github.balotias.intellijantlers.psi.AntlersConditionMixin"
}
```

`modifier` (`T_PIPE T_IDENT ((T_LPAREN argList? T_RPAREN) | (T_COLON modifierArg_)+)?`) is the existing
rule; `T_PIPE` only appears via `modifier`, and `exprToken_` has no `T_PIPE`, so the alternation is
unambiguous. `AntlersConditionMixin.keyword` (the first `T_IDENT`) is unchanged; the modifier becomes a
child `AntlersModifier` node (no new PSI type → only `AntlersParser.java` regenerates).

### 2. Regenerate the parser (standalone grammar-kit; round-trip first)

Use the documented recipe — the in-project grammarkit plugin conflicts with IPGP:
```
GK=~/.gradle/caches/modules-2/files-2.1/org.jetbrains/grammar-kit/2022.3.2/*/grammar-kit-2022.3.2.jar
IDELIB=~/.gradle/caches/9.5.0/transforms/*/transformed/ideaIC-2025.2.6.2/lib
java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main <out-dir> src/main/grammar/Antlers.bnf
```
**Round-trip first** (regen from the unchanged `.bnf` → byte-identical `AntlersParser.java`), then edit,
regen to a temp dir, verify **only** `AntlersParser.java` differs under `gen/` (PSI interfaces + impls +
lexer identical → confirms no new PSI node / no lexer change), copy it in, compile.

## Downstream (verify, expect none)

- `AntlersConditionMixin.keyword` = first `T_IDENT` → unchanged (the keyword still precedes the modifier).
- `AntlersNestingTreeBuilder` / `AntlersBalanceAnnotator` read `condition.keyword` (if/unless/endif…) →
  unchanged.
- `AntlersSemanticHighlightAnnotator` paints `AntlersModifierMixin` first-ident as MODIFIER → a modifier
  inside a condition is now highlighted too (a bonus, not a regression).
- `AntlersCompletionContext` is leaf-based (`T_PIPE` → MODIFIER) → already classifies a `|` in a
  condition as a modifier; unaffected.

## Testing

- **Parse (zero `PsiErrorElement`s)** — new `AntlersConditionModifierTest`: `{{ if x | upper }}`,
  `{{ if code | contains("<style") }}` (the reported case), `{{ unless items | count }}`,
  `{{ elseif title | lower == "x" }}`, `{{ if a | upper | lower }}` (chained). Assert each parses with
  zero errors, and that a `PsiTreeUtil.findChildOfType(..., AntlersModifier)` exists inside the
  condition with `modifierName == "contains"` (etc.).
- **Corpus** — add a `ConditionModifier.antlers.html` + generated `.txt` to `AntlersParsingTest`
  (re-run-to-generate, inspect the tree shows the modifier inside the condition).
- **Regression** — plain conditions (`{{ if x > 0 }}`), modifiers outside conditions
  (`{{ x | contains("<style") }}`), and the existing balance/scope/highlight tests stay green.
- Full-suite gate (grammar change touches the foundation).

## Out of scope

- String interpolation highlighting (`{ … }` inside strings) — the separate 4b sub-project (#3/#11).
- Structured PSI for modifier arguments / a `condition` expression AST beyond the token stream.
