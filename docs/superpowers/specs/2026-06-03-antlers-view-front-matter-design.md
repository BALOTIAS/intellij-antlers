# Antlers View Front Matter — Design

**Date:** 2026-06-03
**Branch:** `antlers-view-front-matter`
**Status:** Approved approach, pending spec review
**Feedback item:** #5 ("front-matter / view variables")

## Goal

Support Statamic Antlers **YAML front matter in views**. A view may define variables in a `---`-fenced
block at the very top of the file, accessed in the body with the `view:` prefix:

```
---
foo: bar
title: My Page
---
{{ view:foo }}  {{ view:title }}
```

Deliver two things:

- **A — `view:` variable intelligence:** completion of the front-matter keys after `view:`, go-to-
  definition from `{{ view:foo }}` onto the front-matter line, hover docs, and discoverability of the
  `view` namespace in head completion. (Unknown variables are already never diagnosed, so no false flag.)
- **B-visual — YAML-style highlighting** of the `---…---` block (fences / keys / values colored), via an
  annotator overlay. This is *coloring only* — see Out of scope for **B-real** (true YAML injection).

Per Statamic, front matter must be at the **very top** of the file (before even Antlers comments) and is
read with the `view:` prefix. v1 resolves `view:` against **the current file's** front matter (correct
for both plain views and partials-as-defaults).

## Background (current state, verified)

- No front-matter handling exists anywhere in the plugin.
- The leading `---…---` text is lexed as `T_OUTER_HTML` and handed to the HTML template-data language as
  plain text (harmless — it renders as uncolored text; HTML is lenient about it). The Antlers PSI covers
  the same range with an `outerHtml`/`T_OUTER_HTML` element.
- `{{ view:<caret> }}` classifies (verified in `AntlersCompletionContext.classify`) as
  `TAG_METHOD` with `tagHead = "view"`, `pathPrefix = ["view"]`. Since `view` is not a catalog tag, the
  provider falls into the member-resolve `else` branch (`AntlersCompletionProvider.kt:163`) →
  `AntlersMemberResolver.resolveField(..., ["view"], ...)` → null → nothing offered. That `else` is the
  completion seam.

## Components

This mirrors the existing **blueprint-variables** feature (`BlueprintScanner` → `BlueprintService` →
completion + soft reference + docs). One scanner feeds both halves (A and B-visual).

### 1. `view/ViewFrontMatterScanner` (pure, new)

`scan(text: String): FrontMatter?` — IntelliJ-free, tolerant, never throws. Returns `null` unless:

- the text **starts** with a line that is exactly `---` (optional trailing whitespace; a leading UTF-8
  BOM is stripped first), **and**
- a later line is exactly `---` (the closing fence).

Otherwise returns:

```kotlin
data class TextSpan(val start: Int, val end: Int)   // half-open [start, end), → TextRange(start, end)
data class FrontMatter(
    val block: TextSpan,                  // [0, closeFenceEnd)
    val openFence: TextSpan,              // the opening `---`
    val closeFence: TextSpan,             // the closing `---`
    val entries: List<FmEntry>            // every `key:` line between the fences (any indent)
)
data class FmEntry(
    val name: String,                     // the key
    val indent: Int,                      // leading-space count (0 == top level)
    val key: TextSpan,                    // span of the key text
    val value: TextSpan?,                 // span of the inline value, or null if none
    val valuePreview: String              // trimmed inline value (may be "")
)
```

Parsing rules (line-based, offsets accumulated like `BlueprintScanner`):
- A key line matches `^(\s*)([A-Za-z_][A-Za-z0-9_-]*):(.*)$`; `name` = group 2, `indent` =
  `group1.length`, `key` = group-2 span, `value`/`valuePreview` from group 3 trimmed (null span when
  empty).
- Non-key lines (list items `- x`, comments `#…`, blanks, scalars) contribute no entry (uncolored in
  v1).
- **A** consumes `entries.filter { it.indent == 0 }` (only top-level keys are `view:` variables). **B-
  visual** paints all entries plus the fences.

Offsets are returned as document offsets (the scan starts at file offset 0, so relative == absolute).

### 2. `view/ViewFrontMatterService` (`@Service(PROJECT)`, new)

`frontMatter(file: PsiFile): FrontMatter?` — `CachedValuesManager.getCachedValue(file) { scan(file.text) }`,
dependency `PsiModificationTracker.MODIFICATION_COUNT`. Not registered in `plugin.xml` (project service,
auto-registered). A convenience `topLevelNames(file): List<FmEntry>` returns `entries.filter { indent == 0 }`.

### 3. Completion (`AntlersCompletionProvider` / classifier unchanged)

- **`view:` keys** — in the `TAG_METHOD` branch, before the member-resolve `else`, add: when
  `info.tagHead == "view"`, offer each top-level `FmEntry` as a plain `LookupElementBuilder.create(name)`
  with `withTypeText("View")` and a tail of the value preview; then return (do not fall through to member
  resolve). Only fires when `pathPrefix == ["view"]` (i.e. `view:<key>`); nested `view:foo.bar` is out of
  scope.
- **`view` head** — in the `TAG_NAME` branch, after the `SystemVariables` loop, when
  `ViewFrontMatterService.getInstance(project).frontMatter(file) != null` and `"view"` not already in
  `seen`, add a `view` lookup with `withTypeText("Namespace")` for discoverability. (Plain element; the
  user types `:` themselves.)

### 4. Reference (`references/AntlersViewVariableReference`, new + wired)

A soft `PsiReferenceBase<PsiElement>` whose `resolve()` returns the element at the front-matter key's
offset (`file.findElementAt(entry.key.start) ?: file`). Wired in
`AntlersDefinitionReferenceHelper.refsForIdent`: for a namePath whose head ident text is `view`, the
ident at **index 1** (the key) gets this reference, resolving the key name against
`ViewFrontMatterService.topLevelNames(file)`. (Index-based, consistent with the existing blueprint-member
wiring; the head `view` ident gets no new reference.)

### 5. Docs (`documentation/AntlersDocumentationProvider`)

In the non-head member branch: when the namePath head is `view` and the target ident is the key
(index 1), return a doc string `View variable · <name>` plus the value preview (when non-empty), instead
of falling through to blueprint/tag docs.

### 6. B-visual annotator (`editor/AntlersFrontMatterHighlightAnnotator`, new + registered)

An `Annotator` gating on `element is AntlersFile` (runs once per file, like `AntlersBalanceAnnotator`).
It pulls `ViewFrontMatterService.frontMatter(file)`; if non-null, it paints (via
`holder.newSilentAnnotation(INFORMATION).range(...).textAttributes(key).create()`, the same overlay
mechanism as `AntlersSemanticHighlightAnnotator` / 4b):

- both fences (`openFence`, `closeFence`) → `FRONTMATTER_FENCE`
- every `entry.key` → `FRONTMATTER_KEY`
- every `entry.value` (when non-null) → `FRONTMATTER_VALUE`

Registered in `plugin.xml` alongside the other annotators. The block currently flows to HTML as plain
text; the `INFORMATION` overlay re-colors those ranges on top (override mechanism proven in 4b).

### 7. Color keys (`highlighting/AntlersSyntaxHighlighter` + `AntlersColorSettingsPage`)

Three new `TextAttributesKey`s on `AntlersSyntaxHighlighter`, mapped to YAML-ish platform defaults:

| key | default |
|---|---|
| `FRONTMATTER_FENCE` | `DefaultLanguageHighlighterColors.METADATA` |
| `FRONTMATTER_KEY` | `DefaultLanguageHighlighterColors.KEYWORD` |
| `FRONTMATTER_VALUE` | `DefaultLanguageHighlighterColors.STRING` |

Registered in `AntlersColorSettingsPage`: add the three to the attribute-descriptor list and extend the
demo snippet with a `---`-fenced block whose fence/key/value are tagged so they preview. These keys are
overlay-only (the syntax highlighter's `getTokenHighlights` is unchanged — front matter is not a lexer
token).

## Data flow

`AntlersFile` highlight pass → annotator → `ViewFrontMatterService.frontMatter(file)` (cached scan) →
paint fences/keys/values. Completion/reference/docs → same cached `FrontMatter` → top-level entries.

## Error handling / edge cases

- No leading `---`, or opening `---` with no closing `---` → `scan` returns `null`; every consumer no-ops
  (no coloring, no `view:` keys, no reference). Strict, so a stray `---` mid-file never colors the file.
- File with front matter but `view:` key not defined → completion offers the defined keys; a reference on
  an undefined key resolves to `null` (soft → no error underline).
- Nested / list / comment / blank lines inside the block → no entry, uncolored (v1).
- `valuePreview` is the raw inline scalar trimmed of surrounding quotes for display only (extraction does
  not interpret YAML types).
- Performance: the scan is one O(n) line pass over the file text, cached per-file on mod count; the
  annotator runs once per file.

## Testing

- **Scanner (pure unit tests)** — leading-block detection, top-level vs nested keys, unclosed→null, no-
  front-matter→null, key offsets, value previews, a key with no value, fence ranges.
- **Completion** — `{{ view:<caret> }}` offers the top-level keys (typeText "View"); a file *without*
  front matter offers none; `{{ <caret> }}` offers `view` (typeText "Namespace") only when front matter
  exists.
- **Reference** — `{{ view:foo }}` resolves to the front-matter `foo:` key offset in the same file; an
  undefined `view:nope` resolves to null.
- **Docs** — `view:foo` documentation contains `View variable` and the value.
- **B-visual highlight** — in a file with front matter, the `---` fence, a key, and a value carry
  `FRONTMATTER_FENCE` / `FRONTMATTER_KEY` / `FRONTMATTER_VALUE` respectively (asserted via
  `myFixture.doHighlighting()` `forcedTextAttributesKey`); body text (e.g. an `{{ view:foo }}` usage) is
  not front-matter-colored.
- **Full-suite gate** (`./gradlew --rerun-tasks test`, failures/errors grep empty).

## Out of scope (explicit follow-ups)

- **B-real** — true YAML *language injection* into the block (in-block YAML completion / validation /
  folding). Requires carving the front matter out of the HTML outer-stream at the lexer level (JFlex
  regen) + a grammar rule + parser regen + a `PsiLanguageInjectionHost` + a `MultiHostInjector` + a view-
  provider tweak. Deliberately deferred (foundation-touching; B-visual delivers the highlighting).
- **Partial front-matter as default param values** — front matter in a partial supplying fallback values
  for the partial's parameters. v1 only resolves `view:` to the current file's front matter.
- Nested / dotted `view:foo.bar` access; YAML type interpretation; multi-document YAML.
