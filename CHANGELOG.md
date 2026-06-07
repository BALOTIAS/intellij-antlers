<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-antlers Changelog

## [Unreleased]

## [1.0.3] - 2026-06-07

### Changed

- **Reformat Code** now indents to one combined nesting depth computed from HTML elements, Antlers
  pairs/conditions, template-named `<{{ }}>` tags, and multi-line `{{ }}` params together — replacing the
  previous two-pass approach that mis-indented templates mixing HTML and Antlers. Whitespace-significant
  and opaque regions (`<pre>`/`<textarea>`, multi-line strings, comment/noparse/PHP blocks) are preserved;
  the pass is idempotent. Indentation-only / Prettier-safe as before.

### Fixed

- Nested `{{ else }}` / `{{ elseif }}` no longer dedent past their own `{{ if }}` (they kept the indent
  from enclosing pairs).
- Loop bodies over arbitrary variables (`{{ buttons }} … {{ /buttons }}`, not just catalog pair tags) are
  now indented.
- Attribute **names and values** inside template-named tags (`<{{ as }} class="…">`) are colored to match
  normal HTML tags; the interpolations inside attribute values stay Antlers-colored.

## [1.0.2] - 2026-06-06

### Added

- **Partial parameter hints**: at a partial include (`{{ partial:components/button … }}` or the
  `{{ partial src="…" … }}` form), the parameters the partial declares with `{{# @param* label … #}}`

  directive comments now power three IDE surfaces, all sourced from the included partial file:
  - **Autocomplete** of parameter names (required ones marked `*`, the `@param` description shown as
    tail text); for the colon form the catalog `src` parameter is suppressed so only the component's
    own parameters are offered.
  - **Quick documentation** (hover / Ctrl-Q) on a parameter name, showing its `@param` description and
    required/optional status.
  - **Parameter info** (Ctrl/⌘P) listing the partial's parameters with the one at the caret in bold.

### Changed

- README now leads with the Statamic mark and an "Antlers" title.

## [1.0.1] - 2026-06-06

### Fixed

- Closing tags with slash-separated paths (`{{ /partial:components/notification }}`) no longer report a
  parse error; the closer now accepts the same `/segment` path tail as the opening form.
- The `%` tag-disambiguation prefix (`{{ %form:fields }}` / `{{ /%form:fields }}`) parses correctly —
  bare `%` is now its own token (modulo `%`/`%=` are unaffected) and is allowed before a tag name in
  expressions and closing tags.
- Paired tags such as `{{ if … }}` and `{{ form:create … }}` are no longer falsely flagged as "never
  closed" when their body contains a slash-path closer or a `%`-prefixed tag (those parse errors used to
  cascade and corrupt tag-balance recovery).
- Interpolated HTML tag names (`<{{ as or 'h2' }}> … </{{ as or 'h2' }}>`) no longer raise a spurious
  "Closing tag matches nothing" error; genuine unmatched closing tags are still reported.

### Known limitations

- When an HTML tag name is itself an Antlers interpolation, HTML attribute/value coloring on that element
  can be lost (the layered HTML highlighter re-lexes each segment independently). The `{{ }}` themselves
  are still parsed and highlighted. See the README for details.

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

[Unreleased]: https://github.com/BALOTIAS/intellij-antlers/compare/1.0.3...HEAD
[1.0.3]: https://github.com/BALOTIAS/intellij-antlers/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/BALOTIAS/intellij-antlers/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/BALOTIAS/intellij-antlers/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/BALOTIAS/intellij-antlers/commits/1.0.0
