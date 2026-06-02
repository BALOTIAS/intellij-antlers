# Antlers Editor Polish (I) — Design

**Date:** 2026-06-02
**Branch:** `antlers-grammar-completion`
**Status:** Approved, ready for implementation plan

## Goal

Close the remaining IDE-experience gaps for the Antlers plugin. The language intelligence
(parsing, highlighting, completion, navigation, scoping, diagnostics, structure, formatting,
refactoring) is already complete; this sub-project adds the editor conveniences a polished language
plugin is expected to have:

1. A **ColorSettingsPage** — *Settings → Editor → Color Scheme → Antlers* with a live demo and
   recolorable token groups.
2. **Live templates** — a small high-value set of `{{ }}` snippets, scoped to Antlers files.
3. A **file template** — *New → Antlers Template* creating a starter `.antlers.html` file.

Explicitly **out of scope** (decided during brainstorming): `ParameterInfoHandler` (Antlers params
are space-separated `key="value"` pairs, a poor fit for the positional-arg API; completion already
surfaces them) and breadcrumbs (the HTML breadcrumbs already show the tag path for these templates).

## Background — current state

- `highlighting/AntlersSyntaxHighlighter` already defines six `TextAttributesKey`s and maps tokens
  to them: `BRACES`, `IDENTIFIER`, `STRING`, `NUMBER`, `COMMENT`, `OPERATOR`. Highlighting works;
  the keys are simply not yet surfaced in the color-settings UI.
- `AntlersIcons.FILE` (`/icons/antlers.svg`) is the file-type icon.
- `AntlersFileType`: name `"Antlers"`, default extension `antlers.html`, language `AntlersLanguage.INSTANCE`.
- No `<colorSettingsPage>`, `<defaultLiveTemplates>`, `<liveTemplateContext>`, or New-file action is
  registered today.

The three components are independent and share no state; each is its own task.

## Component A — `AntlersColorSettingsPage`

`highlighting/AntlersColorSettingsPage` implementing `com.intellij.openapi.options.colors.ColorSettingsPage`.

- `getIcon()` → `AntlersIcons.FILE`
- `getHighlighter()` → `AntlersSyntaxHighlighter()` (reuse; no new highlighter)
- `getDemoText()` → a snippet exercising every group:
  ```
  {{# Featured posts #}}
  {{ collection:blog limit="3" as="posts" }}
    {{ title | upper }}
    {{ if count > 0 }}{{ price }}{{ /if }}
  {{ /collection }}
  ```
- `getAdditionalHighlightingTagToDescriptorMap()` → `null` (no `<tags>` in the demo text)
- `getAttributeDescriptors()` → one `AttributesDescriptor` per key:
  - "Braces & delimiters" → `AntlersSyntaxHighlighter.BRACES`
  - "Identifier" → `IDENTIFIER`
  - "String" → `STRING`
  - "Number" → `NUMBER`
  - "Comment" → `COMMENT`
  - "Operator" → `OPERATOR`
- `getColorDescriptors()` → `ColorDescriptor.EMPTY_ARRAY`
- `getDisplayName()` → `"Antlers"`

Registered: `<colorSettingsPage implementation="com.github.balotias.intellijantlers.highlighting.AntlersColorSettingsPage"/>`.

## Component B — Live templates

Two parts.

**`template/AntlersTemplateContextType`** extending `com.intellij.codeInsight.template.TemplateContextType`:
- Constructor passes the presentable name `"Antlers"` (context id resolves from the class).
- `isInContext(templateActionContext)` → `true` when the context file is an Antlers file
  (`file.fileType is AntlersFileType` / `file.language is AntlersLanguage`). This scopes the
  templates and creates the "Antlers" group in *Settings → Editor → Live Templates*.
- Registered: `<liveTemplateContext contextId="ANTLERS" implementation="...AntlersTemplateContextType"/>`.

**`resources/liveTemplates/Antlers.xml`** — bundled defaults, each with the Antlers context
(`<context><option name="ANTLERS" value="true"/></context>`):

| Abbrev | Expands to |
|--------|------------|
| `if`     | `{{ if $COND$ }}$END${{ /if }}` |
| `ife`    | `{{ if $COND$ }}$SEL$$END${{ else }}{{ /if }}` |
| `unless` | `{{ unless $COND$ }}$END${{ /unless }}` |
| `coll`   | `{{ collection:$HANDLE$ }}$END${{ /collection }}` |
| `partial`| `{{ partial:src="$PATH$" }}` |
| `pair`   | `{{ $TAG$ }}$END${{ /$TAG$ }}` |

Registered: `<defaultLiveTemplates file="liveTemplates/Antlers"/>` (note: no `.xml` suffix in the
attribute; the file on disk is `Antlers.xml`).

The context-id string used in the XML (`ANTLERS`) MUST match the `contextId` declared in the
`<liveTemplateContext>` registration.

## Component C — File template ("New → Antlers Template")

Two parts.

**`resources/fileTemplates/internal/Antlers Template.antlers.html.ft`** — a minimal starter:
```
<!DOCTYPE html>
<html lang="en">
<head>
    <title>{{ title }}</title>
</head>
<body>
    {{ template_content }}
</body>
</html>
```

**`actions/CreateAntlersFileAction`** extending
`com.intellij.ide.actions.CreateFileFromTemplateAction`:
- `buildDialog(project, dir, builder)` → title "New Antlers Template", one entry using
  `AntlersIcons.FILE` and the bundled template name `"Antlers Template"`.
- `getActionName(...)` → `"Create Antlers Template"`.
- `createFileFromTemplate(name, template, dir)` → default behavior via
  `FileTemplateManager.getInstance(project).getInternalTemplate("Antlers Template")`.

Registered as an `<action>` inside group `NewGroup` (anchor `before`/`last`), so it appears under
right-click *New*. Also register the internal template:
`<internalFileTemplate name="Antlers Template"/>`.

## Architecture notes

- All three components are independent, register through `plugin.xml` (+ two resource files), and
  add no dependency. None touches existing language code.
- New packages: `highlighting/` gains the color page (alongside the highlighter it reuses);
  `template/` (new) for the live-template context type; `actions/` (new) for the New-file action.

## Testing (`BasePlatformTestCase`, runnable via `./gradlew test`)

- **ColorSettingsPage** (`AntlersColorSettingsPageTest`):
  - `getAttributeDescriptors()` covers exactly the six highlighter keys (assert the set of
    `AttributesDescriptor.getKey()` equals the six `AntlersSyntaxHighlighter` keys).
  - `getDemoText()` is non-blank; `getHighlighter()` is an `AntlersSyntaxHighlighter`;
    `getDisplayName() == "Antlers"`.
- **Context type** (`AntlersTemplateContextTest`):
  - `isInContext` true for a `.antlers.html` file element; false for a plain `.txt` element.
    (Build a `TemplateActionContext.expandTemplate(file, ...)` / use the file + offset overload that
    this SDK exposes; assert the boolean.)
- **Live templates XML** (`AntlersLiveTemplatesTest`):
  - Load `liveTemplates/Antlers.xml` from the classpath and assert it parses and contains the six
    expected abbreviations (`if`, `ife`, `unless`, `coll`, `partial`, `pair`), each carrying the
    `ANTLERS` context option set to true.
- **File template** (`AntlersFileTemplateTest`):
  - The internal template `"Antlers Template"` content (loaded from the classpath resource) contains
    the expected starter markup (e.g. `template_content`).
  - `CreateAntlersFileAction.getActionName(...)` / dialog title are the expected strings (whatever is
    unit-reachable without the New dialog).

## Risks

- **Context-id matching:** the XML `ANTLERS` option must equal the `<liveTemplateContext contextId>`
  and the context-type class's id, or the bundled templates won't be Antlers-scoped. The XML test
  guards the option presence; manual sanity is that the templates appear under "Antlers" in settings.
- **SDK shape of `TemplateContextType` / `CreateFileFromTemplateAction`:** constructor and method
  signatures have shifted across platform versions (e.g. `TemplateContextType(id, name)` vs
  `TemplateContextType(name)`; `isInContext(PsiFile, int)` vs `isInContext(TemplateActionContext)`).
  The implementer matches whichever overload compiles against this SDK; behavior (true only in
  Antlers files) is the contract.
- File-template New-dialog flow isn't unit-testable end-to-end; tests cover the template content and
  the action's reachable attributes only.

## Out of scope

- `ParameterInfoHandler`, breadcrumbs.
- A bespoke `.svg` redesign of the file icon (reuse `AntlersIcons.FILE`).
- Postfix templates, structural search templates.
