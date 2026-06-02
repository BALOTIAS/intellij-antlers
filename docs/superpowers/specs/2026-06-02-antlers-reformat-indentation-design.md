# Antlers Reformat Indentation — Design

**Date:** 2026-06-02
**Branch:** `antlers-editor-ux-fixes`
**Status:** Approved approach (spike-proven), pending spec review
**Spike evidence:** `spike/reformat-indentation` (commits `b244961`, `82553c8`)

## Goal

Make "Reformat Code" indent Antlers templates correctly: each pair tag
(`{{ collection }}…{{ /collection }}`) and condition (`{{ if }}…{{ /if }}`) indents its content one
level, **additively on top of HTML tag indentation**. Today no formatting model exists (only the
spacing post-processor), so Reformat indents nothing.

## Why this approach (spike-proven)

Two findings from the spike settled the architecture:

1. **The Antlers AST is flat** — pair tags are sibling `AntlersStatement`s, not nested. So the vanilla
   `TemplateLanguageFormattingModelBuilder` produces a flat block tree; its `DataLanguageBlockWrapper`
   is `final` and **enforces a uniform child indent**, which flattens HTML indentation wherever an HTML
   tag wraps an Antlers tag. Synthetic-nesting under that builder is a **dead end** (proven).

2. **`com.intellij.xml.template.formatter.AbstractXmlTemplateFormattingModelBuilder`** (the XML-aware
   template formatter) makes the **HTML/XML formatter the PRIMARY block tree** and merges the Antlers
   ("template") blocks into it. HTML depth is therefore real, and an **absolute-mode** Antlers indent
   composes **additively** on top of it. The spike asserted pixel-perfect output for the dominant
   pattern (Antlers wrapping HTML, arbitrarily nested in HTML):
   ```
   <div>
       <ul>
           {{ collection:blog }}
               <li>{{ title }}</li>
           {{ /collection }}
       </ul>
   </div>
   ```

## Architecture

### `formatter/AntlersXmlTemplateFormattingModelBuilder` (new) — extends `AbstractXmlTemplateFormattingModelBuilder`

Registered `<lang.formatter language="Antlers" implementationClass="…"/>`. Implements:
- `isTemplateFile(file)` → `file is AntlersFile`
- `isOuterLanguageElement(el)` → `el is OuterLanguageElement` (the `{{ }}` placeholder in the HTML data PSI)
- `isMarkupLanguageElement(el)` → `el is XmlElement && el !is OuterLanguageElement`
- `createTemplateLanguageBlock(node, settings, xmlPolicy, indent, alignment, wrap)` → an
  `AntlersXmlTemplateBlock` with the indent **bumped** by the Antlers depth.
- `createXmlTagBlock(...)` / `createXmlBlock(...)` overrides → call `super` with the indent bumped by
  the Antlers depth at the node, so HTML blocks *inside* a pair/condition body gain the Antlers level
  on top of their native HTML indent.

**Antlers depth** comes from the existing `AntlersNestingTreeBuilder.build(file, project)`: for each
`NestingNode` with a non-null `closer`, the body span is `opener.endOffset until closer.startOffset`;
`antlersDepthAt(offset)` = number of spans strictly containing `offset`. The span list is cached per
file text (recomputed when the text identity changes).

**The bump (the load-bearing discovery):**
```kotlin
Indent.getSpaceIndent(extraLevels * indentSize, /* relativeToDirectParent = */ false)
```
Absolute-mode (`relativeToDirectParent=false`) is what composes additively; `true` and the
`(1+depth)` variants double-count (spike-measured). `indentSize` is read from the code-style settings
(NOT hardcoded — the spike prototype hardcoded 4; production reads
`settings.getIndentSize(AntlersFileType.INSTANCE)` or the policy's indent).

### `AntlersXmlTemplateBlock` (new) — extends `com.intellij.xml.template.formatter.TemplateLanguageBlock`

The XML merge calls `setIndent(...)` with the markup-derived indent, clobbering the constructor
indent. Override `setIndent` so that when this Antlers tag sits inside ≥1 pair/condition span, the
inherited indent is replaced with the absolute additive indent (`depth * indentSize`, abs). This makes
a nested `{{ if }}` indent under its enclosing `{{ collection }}`.

### Coexistence with the spacing post-processor

`AntlersSpacingPostFormatProcessor` (in-`{{ }}` spacing) **stays** — the formatting model defines no
`SpacingBuilder` for Antlers interiors, so it doesn't normalize inside `{{ }}`; the post-processor
runs after the model and still does. Spike-verified: `AntlersSpacingFormatterTest` passes unchanged
with the new `<lang.formatter>` registered. They are complementary (model = indentation, processor =
`{{ }}` interior spacing).

## Known limitation (first cut — documented, not a blocker)

**Pure Antlers-on-Antlers nesting:** when a condition/pair sits *directly* inside another pair/
condition with **no HTML element between them** (e.g. `{{ collection }}{{ if }}…{{ /if }}{{ /collection }}`
with nothing but the inner tags), the inner `{{ if }}`/`{{ /if }}` lines stay at the parent level and
HTML inside them double-counts. Root cause: those Antlers tag blocks are `IndentInheritingBlock`s
whose indent is clobbered by the XML merge in a way the absolute-bump doesn't fully correct (the spike
tried several `setIndent`/`relativeToDirectParent` combinations without moving the inner tag's own
line). In practice conditions almost always wrap markup, which formats correctly. This is recorded as
a **known limitation** of the first cut; a follow-up can revisit the `setIndent`/`IndentInheritingBlock`
composition. (Decision point for spec review: accept as documented limitation now, or attempt the fix
in this cut.)

## Data flow

```
Reformat Code → AbstractXmlTemplateFormattingModelBuilder builds the HTML/XML block tree (primary)
  → Antlers {{ }} regions become TemplateLanguageBlocks merged in
  → each markup/Antlers block inside a pair/condition body gets +depth*indentSize (absolute)
  → result: HTML indent + Antlers indent, additive
  → AntlersSpacingPostFormatProcessor then normalizes spacing inside {{ }}
```

## Edge cases

- **Unclosed pair** (`NestingNode.closer == null`) → no body span → no Antlers indent for that body
  (correct; don't indent under an unbalanced opener).
- **Stray closers** (`unmatchedClosers`) → not spans → ignored.
- **Single-line** `{{ if x }}…{{ /if }}` → the body span may be empty/one line → no spurious indent.
- **`{{ noparse }}` / PHP blocks** → their interior should not be reindented (treat as `NONE`/leave;
  the model's getSpacing/indent must not touch noparse/php content).
- Non-Statamic project (`project.isDefault`) → `AntlersNestingTreeBuilder` already null-guards the
  catalog; spans still compute from condition keywords + (no catalog) → degrade gracefully.

## Testing (`BasePlatformTestCase`)

Reformat-and-assert (the spike harness):
```kotlin
myFixture.configureByText("t.antlers.html", input)
WriteCommandAction.runWriteCommandAction(project) {
    CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
}
PsiDocumentManager.getInstance(project).commitAllDocuments()
assertEquals(expected, file.text)
```
Pin deterministic indentation via `com.intellij.application.options.CodeStyle.getSettings(file)`
(set indent size = 4, continuation as needed) so expected strings are stable.

Cases:
- `testHtmlOnlyStillIndents` — `<div>\n<p>x</p>\n</div>` → `<p>` at one level (HTML delegation intact).
- `testPairTagWrapsHtml` — `{{ collection:blog }}\n<article>{{ title }}</article>\n{{ /collection }}`
  → `<article>` at one level; opener/closer at column 0.
- `testHtmlWrapsPairTagAdditive` — the GOAL `<ul>…{{ collection }}…<li>…{{ /collection }}…</ul>` → `<li>`
  at two levels (HTML + Antlers). **The key additive assertion.**
- `testTwoHtmlLevels` — `<div><ul>…` wrapping a pair tag → three levels deep.
- `testUnclosedPairNoIndent` — `{{ collection }}\n<p>x</p>` (no closer) → `<p>` not indented under it.
- `testSpacingProcessorStillRuns` — reformat also normalizes `{{x}}` → `{{ x }}` (the post-processor).
- **Known-limitation marker:** a `// KNOWN LIMITATION` test (or comment) pinning the
  Antlers-on-Antlers behavior so it's explicit, OR `@Suppress`/skip with a clear note — per the spec-
  review decision.

## Out of scope

- Wrapping/alignment of `else`/`elseif` branches, blank-line policy, attribute wrapping.
- A code-style settings panel for Antlers (use the platform/HTML defaults).
- The Antlers-on-Antlers residual fix (documented limitation; possible follow-up).
- Replacing the spacing post-processor with a model `SpacingBuilder`.
