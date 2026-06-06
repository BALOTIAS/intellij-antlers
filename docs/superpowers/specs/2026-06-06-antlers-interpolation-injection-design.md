# Real PSI in String Interpolation via Antlers Self-Injection — Design

**Date:** 2026-06-06
**Branch:** `antlers-interpolation-injection`
**Status:** Approved approach, pending spec review

## Goal

Give the `{ … }` interpolation inside Antlers string literals **real PSI**, so completion, go-to-def,
hover, and modifier docs/Ctrl+P work inside interpolation — the features that already work in `{{ }}`
but were missing inside strings (the `obfuscate_link` `href="{view:href | replace(...)}"` example).

Today interpolation is highlighting-only: `AntlersStringInterpolationAnnotator` overlays colors on the
`{ … }` sub-spans of a flat `T_STRING` leaf; there is no PSI, so nothing resolves or completes inside it.

## Approach (chosen in brainstorming)

**Inject the Antlers language into each `{ … }` span, wrapped as `{{ … }}`**, so the full Antlers parser
produces real PSI and every existing Antlers IDE feature runs on it natively. This follows the codebase's
proven injection pattern (PHP `{{? ?}}`, YAML front matter) and needs **no lexer/grammar/parser change**,
because `T_STRING` already has a custom leaf PSI class (`AntlersStringLeaf`) via `AntlersASTFactory`.

Rejected: the lexer/grammar interpolation-tokenization rework (full loop-scope awareness, but large and
high-risk); and the "inject + make the scope resolver injection-aware" variant (extra scoping plumbing).

## Components

### A. `AntlersStringLeaf` becomes a `PsiLanguageInjectionHost`

File: `psi/AntlersLeafElements.kt`. The class already exists as the custom leaf for `T_STRING`
(`LeafPsiElement`, overriding `getReferences()` for partial-path resolution). Add the host interface:

```kotlin
class AntlersStringLeaf(type: IElementType, text: CharSequence) :
    LeafPsiElement(type, text), PsiLanguageInjectionHost {

    override fun getReferences(): Array<PsiReference> = AntlersPartialReferenceHelper.refsForString(this)
    override fun getReference(): PsiReference? = references.firstOrNull()

    override fun isValidHost(): Boolean = true
    override fun updateText(text: String): PsiLanguageInjectionHost =
        ElementManipulators.handleContentChange(this, text) as PsiLanguageInjectionHost
    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}
```

The existing `getReferences()` partial-path behavior is preserved (a regression check guards it).

### B. `editor/AntlersStringLeafManipulator`

`AbstractElementManipulator<AntlersStringLeaf>`, registered `<lang.elementManipulator forClass=...>`.
Handles write-back of edits made in an injected fragment: splice `newContent` into the leaf's range and
rebuild the leaf via `replaceWithText`. **Round-trip guard** (mirrors the PHP / front-matter
manipulators): if the rebuilt text does not re-lex to a single `T_STRING` (e.g. the new content
introduced a `}` that closes the interpolation early, or an unescaped quote that ends the string), return
the element unchanged rather than corrupting the source.

### C. `injection/AntlersStringInterpolationInjector`

`MultiHostInjector`, registered `<multiHostInjector>`:
- `elementsToInjectIn() = listOf(AntlersStringLeaf::class.java)`.
- `getLanguagesToInject`: reuse `AntlersInterpolationScanner.scan(host.text)` to find the `{ … }` spans
  (it already handles brace nesting, inner `'…'`, and `\{`). For each span with non-empty content
  (`contentStart < contentEnd`), run **one** injection session injecting `AntlersLanguage.INSTANCE` into
  the content range `TextRange(span.contentStart, span.contentEnd)` with prefix `"{{ "` and suffix
  `" }}"`. Each span gets its own `{{ <expr> }}` injected fragment (separate
  `startInjecting → addPlace → doneInjecting` per span).

So `"{view:href | replace('mailto:', '')}"` injects the fragment `{{ view:href | replace('mailto:', '') }}`,
which the existing parser/completion/docs handle exactly as a real tag expression.

### D. Slim `AntlersStringInterpolationAnnotator`

The injected fragment now colors the interpolation **contents** itself (it is a real Antlers file with
the normal highlighter + semantic/pipe annotators). So the annotator drops its inner re-lexing
(`lexInner` / `colorFor`) and keeps only painting the interpolation `{` and `}` delimiters with
`HighlighterColors.TEXT` (the structural color from the recent bleed fix) so they don't revert to
string-green. `AntlersInterpolationScanner` stays — shared by the slim annotator and the injector.

## Data flow

`AntlersStringLeaf` → injector scans `{ … }` spans → injects `{{ <expr> }}` per span → IntelliJ parses
each as a real Antlers fragment → completion / go-to-def / hover / modifier docs+Ctrl+P run on it via the
existing language registrations; edits in the fragment round-trip through `AntlersStringLeafManipulator`;
the slim annotator colors the surrounding `{`/`}`.

## Error handling / edge cases

- **Empty interpolation `{}`** → `contentStart == contentEnd` → no injection.
- **Multiple `{ … }` in one string** → independent injections, one fragment each.
- **Nested braces / inner `'…'` / `\{`** → handled by the existing scanner.
- **Edit that would break the string** (`}` mid-content, unescaped matching quote) → manipulator's
  round-trip guard bails, leaving the source intact.
- **Nested interpolation** (a string inside the injected expression that itself interpolates) → the
  injector re-runs on that inner `AntlersStringLeaf`; real text nesting is finite, so no infinite loop.
- **Single-quoted host with escaped inner quotes** (`'…\'…'`) → content passed verbatim
  (`createSimple`); may mis-parse. Documented v1 limitation.
- **No paired-feature regression:** `AntlersStringLeaf.getReferences()` (partial paths) keeps working
  alongside the host interface.

## Known limitation (accepted)

Field completion/resolution inside interpolation uses the injected fragment's own context → **global /
page-blueprint scope, not the enclosing `{{ collection }}` loop scope**. In practice interpolation
references `view:`/global vars + modifiers, so this rarely bites; loop-scope-aware completion is the
deferred "inject + loop scope" follow-up.

## Testing

- **Injection present:** `InjectedLanguageManager.findInjectedElementAt` inside a `{ … }` returns an
  Antlers element; a `{{ "plain string" }}` with no interpolation injects nothing.
- **Parses as Antlers:** the injected fragment for `"{title | upper}"` resolves `upper` as a modifier
  (e.g. quick-doc on it returns the modifier signature, proving real PSI + the doc provider firing in the
  injected fragment).
- **Modifier docs/Ctrl+P inside interpolation:** quick-doc over `replace` in
  `"{view:href | replace('a','b')}"` returns the modifier signature.
- **Multiple spans:** `"a {one} b {two} c"` yields two injected fragments.
- **Nested/quoted spans:** `"{x | replace('a','b')}"` injects with the inner `'a'`/`'b'` intact.
- **Empty:** `"{}"` injects nothing.
- **Write-back:** editing the injected fragment of `"{title}"` to `"{name}"` round-trips; an edit
  introducing `}` bails (source unchanged).
- **Slim annotator:** the `{`/`}` of an interpolation are `HighlighterColors.TEXT`-colored (not green).
- **No regression:** `AntlersStringLeaf` partial-path references still resolve (the existing partial
  refactoring / reference tests stay green); full-suite gate.

## Out of scope

- Loop-scope-aware completion inside interpolation (the deferred follow-up).
- Any lexer/grammar/parser change.
- Interpolation outside string literals.
- Decoding string escapes in the injected content (verbatim `createSimple`).
