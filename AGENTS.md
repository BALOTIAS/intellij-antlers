# AGENTS.md — intellij-antlers

Guide for AI agents (and humans) working in this repo. Read this before making changes.

`intellij-antlers` is an IntelliJ Platform plugin (Kotlin) providing language support for
**Statamic Antlers** templates (`*.antlers.html`): parsing, highlighting, completion, navigation,
diagnostics, refactoring, and editor UX. Antlers is parsed as a **template language layered over HTML**
(HTML is the data language; `{{ }}` regions are Antlers).

---

## Build & test

```bash
./gradlew build                 # compile + verify plugin
./gradlew compileKotlin         # fast compile check
./gradlew --rerun-tasks test    # run the full test suite
```

**Critical testing rules (these have bitten us repeatedly — follow them):**

- **Always pass `--rerun-tasks` when running tests.** Gradle's cache returns stale "passed" results
  otherwise. The test runner output can also under-report; **gate on the result XML**, not the console:
  ```bash
  ./gradlew --rerun-tasks test
  grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml   # must print NOTHING
  ```
- Tests are `BasePlatformTestCase` (JUnit 3 style: methods named `test…`, no annotations).
- **`myFixture.file.text` LAGS** after `myFixture.type(...)` — typing updates the document but not the
  committed PSI, so `file.text` shows stale text (e.g. `from` for `from="x"`). Editor/handler code
  operates on `editor.document`, so **assert against `myFixture.editor.document.text`**. `caretOffset`
  is reliable.
- `myFixture.doHighlighting()` returns **annotator** highlights only (those with a
  `forcedTextAttributesKey`); lexer-level coloring produces **no** `HighlightInfo`. To assert semantic
  coloring, count highlights whose `forcedTextAttributesKey == <KEY>` over a token (e.g. expect 2 for
  opener+closer).
- **Completion tests are filtered by IDEA's prefix matcher.** A lookup string that doesn't contain the
  typed prefix letters won't appear regardless of what the provider offers (e.g. `if` can't surface
  under an `e` prefix). Query with a matching prefix; assert *suppression* with a non-matching one.
- Live-template inserts need `TemplateManagerImpl.setTemplateTesting(testRootDisposable)` +
  `getTemplateState(editor)`, `myFixture.type("…")`, `state.nextTab()` (plain `type` doesn't route into
  the template field).
- In `assertEquals(expected, enter(text))`, Kotlin evaluates `expected` **first** — if it reads
  `myFixture.file`, that runs before a helper configures the fixture → NPE. Compute the action result
  into a `val` first, then assert.

The version is in `gradle.properties` (`version`); the target platform is IntelliJ IDEA ~2025.2
(`build.gradle.kts`). Design docs (specs + plans) live under `docs/superpowers/{specs,plans}/`.

---

## Project layout (`src/main/kotlin/com/github/balotias/intellijantlers/`)

| Package | Responsibility |
|---|---|
| (root) | `AntlersLanguage`, `AntlersFileType` (`*.antlers.html`), `AntlersIcons` |
| `grammar/` (resources) | `Antlers.bnf` (Grammar-Kit) + `AntlersLexer.flex` (JFlex) — **source of truth** |
| `gen/` (generated) | Parser, PSI interfaces/impls, lexer — generated from the grammar; **don't hand-edit** |
| `parser/` | `AntlersParserDefinition`, `AntlersASTFactory`, `AntlersParserUtil` (external rule predicates, e.g. `atConditionKeyword` / `CONDITION_KEYWORDS`) |
| `psi/` | `AntlersMixins` (`AntlersNamePathMixin`, `AntlersConditionMixin`, `AntlersModifierMixin`, `AntlersClosingTagMixin`, `AntlersStatement`), element/token types |
| `template/` | `AntlersFileViewProvider(Factory)` (layers Antlers over HTML), `AntlersTemplateContextType` (live-template scope: outside `{{ }}`) |
| `lexer/` (gen), `highlighting/` | `AntlersSyntaxHighlighter` (token→`TextAttributesKey`), `AntlersColorSettingsPage`, editor highlighter |
| `catalog/` | Tag/modifier knowledge — see **Catalog** below |
| `completion/` | Completion pipeline — see **Completion** below |
| `editor/` | Typed/Tab/Enter handlers, annotators, folding, brace matcher, commenter — see **Editor** below |
| `formatter/` | `AntlersHtmlFormattingModelBuilder` (reuses HTML formatter), `AntlersSpacingPostFormatProcessor` |
| `scope/` | `AntlersScopeResolver`, `AntlersNestingTreeBuilder`, `AntlersFieldContext`, `AntlersMemberResolver`, `LoopVariables`, `NavVariables` |
| `blueprint/` | `BlueprintService`/`Scanner`, `BlueprintField`/`Namespace`, `CollectionConfig*`, `PageBlueprintResolver`, `SystemVariables` |
| `references/` | Go-to-declaration, Find Usages, Rename for partials / blueprint fields / PHP classes |
| `structure/` | Structure view (tag/condition nesting) |
| `documentation/` | `AntlersDocumentationProvider` (hover docs) |
| `inspection/` | `AntlersUnknownModifierInspection` |
| `actions/` | `CreateAntlersFileAction` (New → Antlers Template) |

Extensions are registered in `src/main/resources/META-INF/plugin.xml`, **except** `@Service`-annotated
project services (`AntlersCatalogService`, `StatamicVersionService`, `BlueprintService`, …), which
auto-register via the annotation.

---

## Catalog (tags & modifiers)

`AntlersCatalogService` (project `@Service`) is the source of truth for completion/highlighting:

- **Bundled** static data: `CatalogTags.ALL` + `CatalogModifiers.ALL` (Kotlin literals — no runtime JSON;
  surfaced via `CatalogData`/`CatalogLoader`). `TagDef`/`ModifierDef` carry `introducedIn`/`removedIn`
  (Statamic major versions) + `appliesTo(major)`.
- **Dynamic** scan: `TagScanner`/`ModifierScanner` find `class … extends Tags`/`Modifier` PHP classes in
  project scope (custom + vendor), so installed tags surface automatically.
- **Version tailoring:** `StatamicVersionService.majorVersion()` resolves the project's Statamic major
  from `composer.json` (`statamic/cms` constraint) → `composer.lock` exact → default `LATEST_MAJOR = 6`.
  `tags()`/`modifiers()` filter the **bundled** lists by `appliesTo(major)`; scanned customs are always
  kept. Antlers **syntax is identical across Statamic 5 & 6** — only catalog entries differ (e.g.
  `relate` is 5-only; `urlencode_except_slashes` is 6-only).
- Results are cached via `CachedValuesManager` on `PsiModificationTracker.MODIFICATION_COUNT`.

**To add a tag/modifier:** add a `TagDef`/`ModifierDef` to `CatalogTags`/`CatalogModifiers` (set
`isPair`, `methods`, `parameters`, and `introducedIn`/`removedIn` if version-specific).

---

## Completion

Flow: `AntlersCompletionContributor` (registered) → `AntlersCompletionContext.classify(position)` returns
an `AntlersCompletionInfo(kind, …)` → `AntlersCompletionProvider` emits lookups per kind →
`InsertHandler`s shape the inserted text.

- **Kinds** (`AntlersCompletionKind`): `TAG_NAME` (+ `isClosing`), `TAG_METHOD`, `TAG_SHORTHAND`,
  `PARAMETER`, `PARAMETER_VALUE`, `MODIFIER`, `FIELD_PATH`, `NONE`. Classification is driven by the
  previous significant leaf (`T_LDOUBLE`, `T_PIPE`, `T_COLON`, `T_EQUALS`, `T_DOT`, `T_SLASH`, …).
- **Logic keywords** (`if`/`unless`/`else`/`elseif`/`endif`/`endunless`) are **not** catalog tags; they're
  offered in the `TAG_NAME` arm via `AntlersLogicKeywords` + `AntlersKeywordInsertHandler`,
  context-aware via `AntlersNestingTreeBuilder.nearestUnclosedAt` (followers only inside the matching
  open block).
- **Insert handlers:** `AntlersTagInsertHandler` (param tags → centred slot `{{ name  }}` + closer +
  arms a param session; **no-param** tags → caret in the block / after the tag, no session),
  `ParameterInsertHandler` (`name="<caret>"`), `ModifierInsertHandler`, `ShorthandTagInsertHandler`
  (`:coll` → live template with synced handle).

---

## Editor behaviors (`editor/`)

- `AntlersTypedHandler` — auto-inserts `  }}` when you type `{{`.
- `AntlersParamTabHandler` (`EditorTab`) + `AntlersParamSession` — repeating param Tab-stops: after a tag
  completion, Tab opens a fresh param slot (advancing past a quoted value) or, on an empty slot, jumps
  the caret to the terminal stop (block for pairs, after the tag for singles). Session lives in editor
  user-data, validated lazily at Tab time (no caret listener).
- `AntlersEnterHandler` (`enterHandlerDelegate`) — one Enter inside an empty matched block
  (`{{ x }}<caret>{{ /x }}`) expands it to opener / indented caret line / closer; indent from
  `com.intellij.application.options.CodeStyle.getIndentOptions(file)`. Returns `Result.Stop` (else
  `Continue`).
- `AntlersBalanceAnnotator` (unclosed/stray/mismatched-handle diagnostics; ignores unknown/addon tags),
  `AntlersSemanticHighlightAnnotator` (paints tag heads, condition keywords, modifier names, and
  closing-tag heads), `AntlersFoldingBuilder`, `AntlersBraceMatcher`, `AntlersCommenter`.
- The diagnostics/folding/structure-view all share **`AntlersNestingTreeBuilder`** (the one source of
  truth for paired-tag/condition nesting; the flat AST is reconstructed via a stack).

---

## Scope & blueprints

- `BlueprintService` scans `resources/blueprints` + fieldsets; `BlueprintField` has a
  `BlueprintNamespace(kind, handle)`. `PageBlueprintResolver` maps a template to its blueprint.
- `AntlersScopeResolver.scopesAt(element)` reconstructs the enclosing iterating-tag scope (collection,
  taxonomy, nav, form, foreach…) by replaying top-level statements through a stack; handles `as=`
  aliasing and condition transparency. `AntlersFieldContext.fieldsInScope(...)` returns the in-scope
  fields (`null` = top level → global fallback).
- `references/` provides go-to-declaration / Find Usages / Rename for partials, blueprint field handles,
  and custom tag/modifier PHP classes.

---

## Conventions

- Match the surrounding Kotlin style (terse, expression bodies, KDoc on non-obvious units). Keep files
  focused — one clear responsibility each.
- Don't hand-edit `src/main/gen/` — change `Antlers.bnf`/`AntlersLexer.flex` and regenerate (the
  generated sources are committed).
- The README's `<!-- Plugin description -->` block is the **marketplace listing** — keep it accurate
  when adding user-facing features, and add a `CHANGELOG.md` `[Unreleased]` entry.
- For non-trivial work, write a design doc under `docs/superpowers/specs/` then a plan under
  `docs/superpowers/plans/` before implementing (that's the workflow this project uses).
- Be permissive in the parser: it accepts a superset of valid Antlers (both Statamic 5 and 6 idioms).
  Diagnostics, not parse errors, flag the questionable cases.
