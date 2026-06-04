# intellij-antlers

![Build](https://github.com/BALOTIAS/intellij-antlers/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
Language support for **[Statamic](https://statamic.dev) Antlers** templates (`*.antlers.html`) in IntelliJ-based IDEs.

The plugin parses Antlers as a template language layered over HTML/CSS, so you get full Antlers
intelligence inside `{{ }}` alongside the regular markup tooling around it. It understands your
project's blueprints, fieldsets, collections, and a view's YAML front matter, so completion and
navigation are blueprint- and view-aware.

## Features

**Editing**
- Syntax highlighting for tags, variables, strings, numbers, comments, operators, and PHP/noparse
  blocks — with customizable colors (*Settings → Editor → Color Scheme → Antlers*). Antlers
  interpolation inside strings (`"object-position: {logo:focus_css}"`) is highlighted as real Antlers,
  and a view's `---` … `---` front matter is a real **YAML** island — comments, highlighting, and YAML
  errors/warnings/completion all work inside it.
- Brace matching (`{{ }}`, comments, noparse, PHP), commenting (`{{# … #}}`), and `{{ }}` auto-insert.
- **Smart block editing**: completing a tag drops the caret where you'll actually type — a parameter
  slot for tags that take parameters, or straight into the block for tags that don't. **Tab** walks
  through further parameter slots (jumping past quoted values) and then into the block, and a single
  **Enter** inside an empty `{{ tag }}{{ /tag }}` expands it to an indented body with the closing tag on
  its own line.
- Code folding for paired tags, conditions, comments, noparse and PHP blocks.
- A **Structure view** outline of the template's tag/condition nesting and partial includes.

**Completion** (backed by a bundled Statamic 5 & 6 catalog — tailored to the version detected in your `composer.json` — plus custom tags/modifiers discovered in your project)
- Tags, tag methods/sub-tags, parameter names, and **parameter values** — partial paths for
  `partial:src=`, collection/taxonomy handles for `from=`/`in=`/…, field names for `sort=`, and
  `true`/`false` for boolean params.
- Logic keywords (`if`, `unless`, `else`, `elseif`, `endif`) offered inside `{{ }}`, context-aware —
  the followers (`else`/`elseif`/`endif`) appear only inside the matching open block.
- Modifiers (after `|`) with their arguments — including modifiers used inside conditions
  (`{{ if code | contains("…") }}`).
- Collection/taxonomy/form/nav handles after the colon shorthand (`{{ collection:<caret> }}`).
- Variables: blueprint fields, system variables, loop, nav-tree, and form variables — resolved
  for the current scope (inside `{{ collection }}`, `{{ nav }}`, `{{ form }}`, page-mapped templates, etc.).
- **View front matter**: keys declared in a view's `---` … `---` block are completed after `{{ view: }}`.
- Member/relationship completion when dotting into grid/group fields and related entries.

**Navigation & docs**
- Go-to-declaration from a `{{ variable }}` to its blueprint field, from `{{ partial:… }}` to the
  partial file, from `{{ view:foo }}` to its front-matter key, and from custom tag/modifier names to
  their PHP class.
- Quick documentation (hover) for tags, modifiers, parameters, and variables (including `view:` keys).

**Diagnostics**
- A tag-balance annotator (unclosed/stray conditions and paired tags) that leaves unknown/addon tags
  alone, and an inspection for unknown modifiers.

**Refactoring**
- **Rename** and **Find Usages** for partials (file ↔ every include) and for blueprint field
  handles (the YAML `handle:` ↔ every `{{ … }}` usage, across collections that import a shared
  fieldset).

**Convenience**
- A small set of Antlers live templates (`if`, `unless`, `coll`, `partial`, …) and a
  *New → Antlers Template* file action.
- A formatter that, on *Reformat Code*, normalizes spacing inside `{{ }}` delimiters and indents a
  multi-line tag's parameters one level under the `{{` line (with `}}` on its own line).
<!-- Plugin description end -->

## Compatibility

- **Statamic 5 and 6.** Antlers syntax is shared across both versions; the bundled tag/modifier catalog
  is tailored to the version detected in your project's `composer.json` (falling back to the latest when
  none is found), and custom/addon tags are discovered by scanning your project.
- Both `*.antlers.html` and `*.antlers.php` views.
- IntelliJ-based IDEs on the **2025.2** platform (IntelliJ IDEA, PhpStorm, WebStorm, and friends). PHP
  inside `{{$ … $}}` / `{{? … ?}}` blocks is highlighted where the JetBrains PHP plugin is available
  (PhpStorm / IDEA Ultimate); everything else works everywhere.

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "intellij-antlers"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/BALOTIAS/intellij-antlers/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


## License

Released under the [MIT License](LICENSE) © Matthias Balota.

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
