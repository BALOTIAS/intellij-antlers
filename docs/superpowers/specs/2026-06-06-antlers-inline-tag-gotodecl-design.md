# Go-to-Declaration for Inline-Tag-Call Custom Tags — Design

**Date:** 2026-06-06
**Branch:** `antlers-inline-tag-gotodecl`
**Status:** Approved approach, pending spec review

## Goal

Cmd/Ctrl+B (go-to-declaration) on a custom tag used as an **inline tag call** — `{{ href = {obfuscate_link
… } }}` — navigates to its PHP `Tags/` class, exactly like the standalone `{{ obfuscate_link … }}` form
already does.

## Root cause

`AntlersDefinitionReferenceHelper.refsForIdent` attaches the soft `AntlersPhpClassReference` only to (a)
modifier names and (b) `AntlersNamePathMixin` heads. In `{{ x = {obfuscate_link …} }}` the parser leaves
`obfuscate_link` as a **bare `T_IDENT`** (not a NamePath head), so no reference is attached → no
navigation. This is the same shape the inline-tag-head *coloring* fix already handles in
`AntlersSemanticHighlightAnnotator.isInlineTagHead`.

## Components

### A. Shared `AntlersInlineTags.isInlineTagHead` (extract)

Create `psi/AntlersInlineTags.kt`:
```kotlin
package com.github.balotias.intellijantlers.psi

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * An inline tag-call head: a bare T_IDENT opening `{tag …}` — preceded by `{`, not followed by `:` (so it
 * is not an array key like `{collection: 'x'}`), whose text is a known catalog tag. Shared by the semantic
 * highlighter (coloring) and the go-to-declaration reference so both stay consistent.
 */
object AntlersInlineTags {
    fun isInlineTagHead(element: PsiElement): Boolean {
        if (element.node?.elementType != AntlersTypes.T_IDENT) return false
        if (PsiTreeUtil.skipWhitespacesBackward(element)?.node?.elementType != AntlersTypes.T_LBRACE) return false
        if (PsiTreeUtil.skipWhitespacesForward(element)?.node?.elementType == AntlersTypes.T_COLON) return false
        return AntlersCatalogService.getInstance(element.project).isTag(element.text)
    }
}
```
`AntlersSemanticHighlightAnnotator` drops its private `isInlineTagHead` and calls
`AntlersInlineTags.isInlineTagHead(element)` (behavior-preserving — the existing coloring test stays green).

### B. Attach the reference in `refsForIdent`

In `AntlersDefinitionReferenceHelper.refsForIdent`, after the modifier-name block and before the NamePath
lookup, add:
```kotlin
        if (AntlersInlineTags.isInlineTagHead(element)) {
            return arrayOf(AntlersPhpClassReference(element, name, isModifier = false))
        }
```
This reuses the existing soft, text-scan-based `AntlersPhpClassReference` (resolves via `TagScanner.find`,
no PHP plugin required). Go-to-declaration finds it via the multi-reference path.

## Data flow

inline tag head ident → `AntlersIdentLeaf.getReferences()` → `refsForIdent` → (inline-tag-head) →
`AntlersPhpClassReference` → `TagScanner.find(project, name)` → the `Tags/` PHP class file + class-name
offset → Cmd+B navigates there.

## Scope / non-goals

- **Modifiers need no change** — modifiers only ever appear as `| modifier` (an `AntlersModifierMixin`,
  including inside string interpolation's injected fragment), which already gets the reference. There is no
  inline-modifier-call syntax. So this is tags-only.
- Built-in/bundled tags (classes not in the project), non-`/Tags/` class locations, and `$handle`-based
  mapping are existing `TagScanner` limitations, out of scope here.

## Error handling / edge cases

- **Array key** `{collection: 'x'}` → next sibling is `:` → not an inline tag head → no reference.
- **Standalone NamePath head** `{{ obfuscate_link … }}` → its ident has no preceding `{` sibling → not an
  inline tag head here → still handled by the existing NamePath-head branch (unchanged).
- **Unknown name** after `{` → `isTag` false → no reference.
- The reference is **soft**, so an unresolved inline tag is never flagged as an error.

## Testing

- **Inline tag resolves:** with a fixture `app/Tags/ObfuscateLink.php` containing
  `class ObfuscateLink extends Tags`, the `obfuscate_link` ident in `{{ href = {obfuscate_link href="x"} }}`
  has a reference that resolves into `ObfuscateLink.php` (the scanner is text-based, so it resolves in tests
  without the PHP plugin).
- **Standalone regression:** `{{ obfuscate_link href="x" }}` still resolves to the same class.
- **Array-key negative:** in `{{ x = {collection: 'y'} }}`, the `collection` ident attaches no
  `AntlersPhpClassReference` (it's an array key).
- **Coloring stays green:** the existing inline-tag-head coloring test passes (shared helper).
- Full-suite gate.

## Out of scope

- Modifier go-to-decl changes (already complete); inline-modifier syntax (doesn't exist).
- Widening `TagScanner` coverage (other directories / `$handle`).
