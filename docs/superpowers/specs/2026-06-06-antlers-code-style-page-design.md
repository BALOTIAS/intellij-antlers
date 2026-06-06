# Antlers Code Style Settings Page — Design

**Date:** 2026-06-06
**Branch:** `antlers-code-style-page`
**Status:** Approved approach, pending spec review

## Goal

Add **Settings → Editor → Code Style → Antlers** with the standard Indent panel (indent size, tab size,
use-tab) and a preview, so users can configure Antlers indentation — which the formatter already consumes
via `CodeStyle.getIndentOptions`. (Konafets "Code-Style settings page".)

## Current state

- A custom `AntlersSettingsConfigurable` under **Languages & Frameworks → Antlers** exposes only the
  reformat-on/off toggle (defer-to-Prettier).
- No `LanguageCodeStyleSettingsProvider` is registered, so Antlers has no Code Style page; the formatter's
  `CodeStyle.getIndentOptions(file)` falls back to defaults and users can't set the Antlers indent size.

## Component

### `settings/AntlersLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider`

- `getLanguage(): Language = AntlersLanguage.INSTANCE`
- `getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()` — the standard tabs/indent
  editor.
- `customizeSettings(consumer, settingsType)`: for `SettingsType.INDENT_SETTINGS` call
  `consumer.showStandardOptions("INDENT_SIZE", "TAB_SIZE", "USE_TAB_CHARACTER")`; do nothing for other
  settings types (indent-only — the formatter uses no spacing/wrapping/blank-line options).
- `getCodeSample(settingsType): String` — a representative Antlers snippet (a `{{ collection }}` loop with a
  nested `{{ if }}`) shown in the Code Style preview pane.
- Registered in `plugin.xml`: `<langCodeStyleSettingsProvider implementation="…AntlersLanguageCodeStyleSettingsProvider"/>`.

### Effect

With the provider registered, `CodeStyle.getIndentOptions(antlersFile)` returns the user-configured Antlers
indent options. The existing formatter passes (`AntlersBlockIndentProcessor`, `AntlersMultilineTagIndentProcessor`)
already call `CodeStyle.getIndentOptions(source)`, so they automatically honor the configured indent size /
tab settings — e.g. setting 2-space indent reformats with 2 spaces; enabling tabs uses tabs.

## Decisions

- **Reformat-on/off toggle stays** in the existing `AntlersSettingsConfigurable` (Languages & Frameworks →
  Antlers) — it is a defer-to-Prettier switch, not a code-style concern. Not moved/duplicated.
- **Indent panel only** — no Spacing / Wrapping / Blank-Lines panels (the formatter doesn't consume them).

## Error handling / edge cases

- No project changes to defaults: the Antlers indent options default to the IDE's defaults (4 spaces), so
  existing behavior and tests are unchanged until a user customizes them.
- The provider is read-only configuration plumbing; no parsing/formatting logic changes.

## Testing

- **Provider basics:** `getLanguage()` is `AntlersLanguage.INSTANCE`; `getCodeSample(INDENT_SETTINGS)` is
  non-blank and parses as Antlers (contains `{{` / `}}`).
- **Integration (the real value):** set the Antlers indent size to 2 via
  `CodeStyle.getSettings(project).getCommonSettings(AntlersLanguage.INSTANCE).indentOptions` (or
  `getIndentOptions` for the file type), reformat a nested `{{ if }}` block, and assert the body is indented
  by **2 spaces** (not the default 4) — proving the Code Style page drives the formatter. A second case with
  `USE_TAB_CHARACTER = true` asserts a tab.
- **No regression:** existing formatter tests (which rely on the default 4-space indent) stay green;
  full-suite gate.

## Out of scope

- Spacing / wrapping / blank-lines / arrangement code-style panels.
- Moving or duplicating the reformat-on/off toggle.
- Any change to the formatter's indentation logic (it already reads `getIndentOptions`).
