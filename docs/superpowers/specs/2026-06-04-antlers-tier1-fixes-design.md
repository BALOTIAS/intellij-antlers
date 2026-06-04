# Antlers Tier-1 Fixes — Design

**Date:** 2026-06-04
**Branch:** `antlers-tier1-fixes`
**Status:** Approved approach, pending spec review
**Source:** Konafets/antlers-idea issue triage — #138 (`-=` mis-lexing, reproduced in our lexer) and #132
(formatter overrules Prettier). Two small, independent fixes bundled as one "Tier 1" cycle.

## Goal

1. **Fix `-=` (and other `-`-prefixed operator) mis-lexing (#138).** Today `{{ foo-=3 }}` lexes as
   `foo-` `=` `3` because the `IDENT` rule greedily eats a trailing `-`.
2. **Add an opt-out so Reformat Code can defer to Prettier (#132).** A project setting that, when off,
   makes Reformat Code leave `.antlers.html` entirely untouched.

## Component 1 — `-=` lexing fix (#138)

### Background (reproduced)

`AntlersLexer.flex`: `IDENT=[a-zA-Z_][a-zA-Z_0-9\-]*`. A hyphen is allowed anywhere in the tail, including
as a trailing char, so the identifier swallows the `-` of a following `-=`/`-` operator. Verified via a
throwaway lexer probe:

| input | today | wanted |
|---|---|---|
| `{{ foo-=3 }}` | `foo-` `=` `3` | `foo` `-=` `3` |
| `{{ foo- }}` | `foo-` | `foo` `-` |
| `{{ a-b }}` | `a-b` (one IDENT) | **unchanged** — hyphenated names are valid Antlers |
| `{{ count-1 }}` | `count-1` (one IDENT) | **unchanged** |
| `{{ meta-title }}` | `meta-title` (one IDENT) | **unchanged** |

So the fix must only release a hyphen that is **not** followed by an identifier character; hyphens
*between* identifier chars (kebab-case handles) must stay part of the identifier.

### Change

In `AntlersLexer.flex`, change the `IDENT` macro to:
```
IDENT=[a-zA-Z_]([a-zA-Z_0-9]|-[a-zA-Z_0-9])*
```
A `-` is part of the identifier only when immediately followed by `[a-zA-Z_0-9]`. The `OP` rule already
includes both `-=` and `-`, so once `IDENT` stops eating the trailing `-`, JFlex's longest-match emits
the operator correctly. Regenerate `_AntlersLexer.java` (JFlex recipe; round-trip the unchanged `.flex`
to byte-identical first, then edit/regen/copy).

### Consequences (all intended)

- `foo-=3` → `foo` `-=` `3` (fixed). `foo-=`/`foo -= 3` likewise.
- `foo-` (trailing hyphen at end of expression) → `foo` `-` — the degenerate trailing hyphen becomes the
  operator, which is the correct reading.
- `a-b`, `count-1`, `meta-title`, `my-field-name` → still a single `T_IDENT` (kebab names preserved).
- `a--b` (double hyphen, rare) → `a` `-` `-b` — acceptable edge; not a real Antlers construct.

## Component 2 — formatter opt-out / Prettier coexistence (#132)

### Components

1. **`settings/AntlersFormatterSettings`** — a project-level `PersistentStateComponent`
   (`@Service(PROJECT)`, `@State(name="AntlersFormatterSettings", storages=[Storage("antlers.xml")])`)
   holding `reformatEnabled: Boolean = true`. Default `true` = exactly today's behavior (no change for
   existing users). `getInstance(project)`.
2. **`settings/AntlersSettingsConfigurable`** — the plugin's first Settings page: a `Configurable`
   registered `<projectConfigurable parentId="language" id="antlers.settings" displayName="Antlers" …>`
   (under *Settings → Languages & Frameworks → Antlers*) with a single checkbox: *"Reformat Antlers code
   on Reformat Code (uncheck to defer to Prettier / an external formatter)."* Standard
   `createComponent`/`isModified`/`apply`/`reset` wired to `AntlersFormatterSettings`.
3. **Gating (scope (b) — full opt-out):** all three formatter entry points check the setting and become
   no-ops when `reformatEnabled == false`:
   - `AntlersSpacingPostFormatProcessor.processText` and `AntlersMultilineTagIndentProcessor.processText`
     → `if (!AntlersFormatterSettings.getInstance(source.project).reformatEnabled) return rangeToReformat`
     at the top.
   - `AntlersHtmlFormattingModelBuilder.createModel(formattingContext)` → when disabled, return a no-op
     formatting model so Reformat Code leaves the file untouched:
     `FormattingModelProvider.createFormattingModelForPsiFile(file, AntlersNoopBlock(file.node), settings)`,
     where `AntlersNoopBlock` is a tiny `AbstractBlock` (`buildChildren()=emptyList`, `isLeaf()=true`,
     `getSpacing()=null`, `getIndent()=Indent.getNoneIndent()`) spanning the whole file. Otherwise it
     delegates to `super.createModel(...)` (today's HTML-reuse model).

When `reformatEnabled == false`, Reformat Code on a `.antlers.html` file does nothing — Prettier (or any
external formatter the user runs) fully owns formatting.

## Data flow

Reformat Code → `AntlersHtmlFormattingModelBuilder.createModel` (no-op model if disabled) → post-format
processors (each early-returns if disabled). The setting is read fresh on each reformat, so toggling it
takes effect immediately.

## Error handling / edge cases

- Setting absent/first run → default `true` (current behavior).
- The two components are independent; neither touches the other's code.
- Tests must reset `reformatEnabled` to `true` in `tearDown` (the project-level service persists across
  tests in a `BasePlatformTestCase` class), so a disabling test can't leak into others.

## Testing

**Component 1 (`LexerTest` additions):**
- `{{ foo-=3 }}` → `…T_IDENT('foo') T_OP('-=') T_NUMBER('3')…` (the fix).
- `{{ foo - 3 }}`/`{{ foo-3 }}` sanity: `foo-3` stays one `T_IDENT` (hyphen-before-digit), `foo - 3`
  spaced is `foo` `-` `3`.
- Regression: `{{ a-b }}`, `{{ meta-title }}` → single `T_IDENT` (kebab preserved); every existing
  `LexerTest`/`AntlersParsingTest` stays green (the round-trip + corpus guard the rest).

**Component 2:**
- `AntlersFormatterSettings` round-trips `reformatEnabled` via `getState`/`loadState`.
- With `reformatEnabled = false`: `reformat("{{x}}")` stays `"{{x}}"` (spacing pass off); a multi-line tag
  is not reindented (multiline pass off); HTML indentation is not applied (model no-op). With the default
  `true`, existing `AntlersSpacingFormatterTest` / `AntlersMultilineFormatTest` / `AntlersHtmlFormatTest`
  stay green (proves the gate defaults to on).
- A light `AntlersSettingsConfigurable` test: `apply()` writes the checkbox state, `reset()` reads it,
  `isModified()` reflects divergence.
- Full-suite gate.

## Out of scope

- Auto-detecting a project's Prettier + `prettier-plugin-antlers` config to flip the setting
  automatically (explicit toggle only for v1).
- A "format with Prettier" action / running Prettier from the plugin (that's IntelliJ's Prettier plugin's
  job; we only step out of the way).
- App-level (IDE-wide) scope for the setting — it is per-project (Prettier config is per-project).
