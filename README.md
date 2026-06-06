# intellij-antlers

![Build](https://github.com/BALOTIAS/intellij-antlers/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/32139-antlers.svg)](https://plugins.jetbrains.com/plugin/32139-antlers)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/32139-antlers.svg)](https://plugins.jetbrains.com/plugin/32139-antlers)

<!-- Plugin description -->
Language support for **[Statamic](https://statamic.dev) Antlers** templates (`*.antlers.html` and
`*.antlers.php`) in IntelliJ-based IDEs.

The plugin parses Antlers as a template language layered over HTML/CSS, so you get full Antlers
intelligence inside `{{ }}` alongside the regular markup tooling around it. It reads your project's
blueprints, fieldsets, collections, taxonomies, navigations, forms, and a view's YAML front matter,
so completion, navigation, and documentation are blueprint- and view-aware — and **scope-aware**:
the variables offered and resolved are the ones actually available where your cursor is (inside a
`{{ collection }}` loop, a related entry, a `{{ nav }}` tree, a `{{ form }}`, a page-mapped template,
or an `@collection`-hinted view).

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
- **Template IDE hints**: `{{# @… #}}` directive comments (`@name`, `@desc`, `@param`, `@entry`,
  `@collection`, `@blueprint`, `@set`) are highlighted and completed after `@`. A leading
  `{{# @collection|@entry|@blueprint <handle> #}}` hint tells the plugin which blueprint a view's
  variables come from, making completion and navigation field-aware even when there's no collection
  mapping for the file.

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
  partial file (including `partials/`, underscored, and dotted-nested partials), from `{{ view:foo }}`
  to its front-matter key, and from a custom tag/modifier name to its PHP class — including tags used
  in the inline form (`{{ x = {your_tag …} }}`).
- Quick documentation (hover) for tags, modifiers, parameters, and variables (including `view:` keys),
  and **parameter info** (Ctrl/⌘P) for modifier arguments.

**Diagnostics**
- A tag-balance annotator (unclosed/stray conditions and paired tags) that leaves unknown/addon tags
  alone, and an inspection for unknown modifiers.

**Refactoring**
- **Rename** and **Find Usages** for partials (file ↔ every include) and for blueprint field
  handles (the YAML `handle:` ↔ every `{{ … }}` usage, across collections that import a shared
  fieldset).

**Formatting**
- On *Reformat Code*: one space inside `{{ }}` delimiters and around `|`; paired-tag and condition
  bodies indented one level per nesting, with `{{ else }}`/`{{ elseif }}` dedented back to the
  `{{ if }}`; multi-line arrays inside `{{ }}` bracket-nested; multi-line tag parameters indented under
  the `{{` line; and template-named HTML tags (`<{{ html_tag }} … >` … `</{{ html_tag }}>`) indented too.
- **Indentation-only — designed to coexist with Prettier** (it never reflows or breaks lines). Indent
  size and tabs are configurable in *Settings → Editor → Code Style → Antlers*, and a master toggle in
  *Settings → Languages & Frameworks → Antlers* turns Antlers formatting off entirely so you can defer
  to Prettier.

**Convenience**
- A small set of Antlers live templates (`if`, `unless`, `coll`, `partial`, …) and a
  *New → Antlers Template* file action.
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
