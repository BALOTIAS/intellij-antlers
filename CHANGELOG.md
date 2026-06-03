<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-antlers Changelog

## [Unreleased]
### Added
- Antlers language support for `*.antlers.html` (parsed as a template language over HTML/CSS): lexer,
  grammar, PSI, and file type.
- Syntax highlighting (tags, variables, strings, numbers, comments, operators, PHP/noparse blocks) with
  a configurable color settings page, plus semantic highlighting of tag heads, condition keywords,
  modifier names, and closing-tag heads. Antlers interpolation inside string literals
  (`"…{logo:focus_css}…"`) is highlighted as Antlers, and a view's `---`…`---` front matter is
  highlighted YAML-style (configurable fence/key/value colors).
- Brace matching, line/block commenting, `{{ }}` auto-insert, and code folding for paired tags,
  conditions, comments, noparse, and PHP blocks.
- Structure view of tag/condition nesting and partial includes.
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
- Navigation: go-to-declaration for variables → blueprint fields, partials → files, and custom
  tag/modifier names → PHP classes; quick documentation (hover) for tags, modifiers, parameters, and
  variables.
- Diagnostics: tag-balance annotator (unclosed/stray/mismatched-handle) that ignores unknown/addon tags,
  and an unknown-modifier inspection.
- Refactoring: Rename and Find Usages for partials and blueprint field handles (across shared
  fieldsets).
- Convenience: bundled Antlers live templates, a *New → Antlers Template* action, and a formatter that
  normalizes spacing inside `{{ }}` and indents multi-line tag parameters one level under the `{{` line
  on *Reformat Code*.
- **Statamic 5 & 6 compatibility**: the bundled catalog is a 5∪6 superset, tailored to the major version
  detected from `composer.json` / `composer.lock` (default: latest).
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
