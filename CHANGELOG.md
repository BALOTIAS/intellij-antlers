<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-antlers Changelog

## [Unreleased]

## [1.0.0] - 2026-06-06
First public release.

### Added
- Antlers language support for `*.antlers.html` (parsed as a template language over HTML/CSS): lexer,
  grammar, PSI, and file type.
- Syntax highlighting (tags, variables, strings, numbers, comments, operators, PHP/noparse blocks) with
  a configurable color settings page, plus semantic highlighting of tag heads, condition keywords,
  modifier names, and closing-tag heads. Antlers interpolation inside string literals
  (`"…{logo:focus_css}…"`) is injected as real Antlers (with completion, navigation, and docs inside
  it, scope-aware), and a view's `---`…`---` front matter is a real
  **YAML** language island (injected) — comments, highlighting, and YAML errors/warnings/completion
  inside the block.
- Brace matching, line/block commenting, `{{ }}` auto-insert, and code folding for paired tags,
  conditions, comments, noparse, and PHP blocks.
- Structure view of tag/condition nesting and partial includes.
- **Template IDE hints**: `{{# @name / @desc / @param / @entry / @collection / @blueprint / @set #}}`
  directive comments are highlighted and completed after `@`; a leading `@collection`/`@entry`/`@blueprint`
  hint drives blueprint field scoping for the view.
- Completion backed by a bundled Statamic catalog merged with custom tags/modifiers scanned from the
  project: tag names, tag methods/sub-tags, collection/taxonomy/form/nav handles after the colon
  shorthand (`{{ collection:… }}`), parameter names and **values**, modifiers and their arguments
  (including modifiers used inside conditions, e.g. `{{ if x | contains("…") }}`), and
  blueprint/system/loop/nav/form variables (scope-resolved), plus member/relationship access.
- **View front matter** (`---`…`---` at the top of a view): top-level keys are completed after
  `{{ view: }}`, resolve (go-to-declaration) to their front-matter line, and show hover docs.
- Context-aware in-brace completion of logic keywords (`if`/`unless`/`else`/`elseif`/`endif`/`endunless`).
- Colon-shorthand tag completion (`:collection` …) inserting a synced opener/closer.
- Smart insertion UX: repeating parameter Tab-stops after tag completion (skipping past quoted values),
  no-param tags placing the caret in the block / after the tag, and single-Enter expansion of an empty
  `{{ tag }}{{ /tag }}` block into an indented body with the closer on its own line.
- Navigation: go-to-declaration for variables → blueprint fields, partials → files (incl. `partials/`,
  underscored, and dotted-nested partials), and custom tag/modifier names → PHP classes (including the
  inline `{{ x = {your_tag … } }}` form); quick documentation (hover) for tags, modifiers, parameters,
  and variables, plus parameter info (Ctrl/⌘P) for modifier arguments.
- Diagnostics: tag-balance annotator (unclosed/stray/mismatched-handle) that ignores unknown/addon tags,
  and an unknown-modifier inspection.
- Refactoring: Rename and Find Usages for partials and blueprint field handles (across shared
  fieldsets).
- Formatter (*Reformat Code*, indentation-only / Prettier-safe): normalizes spacing inside `{{ }}` and
  around `|`; indents paired-tag and condition bodies, dedenting `{{ else }}`/`{{ elseif }}` to the
  `{{ if }}`; bracket-nests multi-line arrays; indents multi-line tag parameters; and indents
  template-named HTML tags (`<{{ html_tag }} … >`). Indent size/tabs are configurable in
  *Settings → Editor → Code Style → Antlers*.
- Convenience: bundled Antlers live templates and a *New → Antlers Template* action.
- **Statamic 5 & 6 compatibility**: the bundled catalog is a 5∪6 superset, tailored to the major version
  detected from `composer.json` / `composer.lock` (default: latest).
- `*.antlers.php` is recognized as an Antlers view, and partials/views/blueprints resolve across both the
  `.antlers.html` and `.antlers.php` extensions.
- Real PHP is injected into `{{$ … $}}`, `{{? … ?}}`, and native `<?php … ?>` / `<?= … ?>` blocks —
  highlighting, errors/warnings, and PHP completion inside them (requires the JetBrains PHP plugin, i.e.
  PhpStorm or IDEA Ultimate; no-ops cleanly elsewhere).
- Formatter opt-out: *Settings → Languages & Frameworks → Antlers* has a "Reformat Antlers code" toggle
  (default on); unchecking it makes Reformat Code leave `.antlers.html` untouched so an external formatter
  (e.g. Prettier with `prettier-plugin-antlers`) can own formatting.
- Plugin icon, author metadata (Matthias Balota), and an MIT license.
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

### Fixed
- Identifiers no longer swallow a trailing hyphen, so compound operators lex correctly (`{{ foo-=3 }}`
  is `foo` `-=` `3`); kebab-case names like `meta-title` / `count-1` are still single identifiers.
