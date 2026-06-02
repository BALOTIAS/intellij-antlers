# Antlers Partial Refactoring H1 — Rename + Find-Usages — Design

**Date:** 2026-06-02
**Status:** Approved
**Scope:** Sub-project **H1** of "H (Refactoring)". Make Statamic partials a first-class refactoring
target: renaming/moving a partial file updates every `{{ partial:… }}` include, and Find Usages on a
partial lists its includes. H2 (blueprint-variable refactoring, needs the YAML-plugin dependency) is a
separate follow-up.

## 1. Background & Goal

`AntlersPartialReference` already resolves a `{{ partial:src="blog/card" }}` / `{{ partial:blog/card }}`
include to its template file under `resources/views`. But it has no rename hooks, so renaming or moving a
partial leaves the includes stale, and rename-from-an-include doesn't propagate.

H1 adds rename support (`handleElementRename` + `bindToElement`) so the references update on file rename/
move, and verifies that Find Usages on a partial file lists its includes (the resolving reference makes
this work without extra machinery). Navigation/completion are unchanged.

## 2. Principles

1. **Reuse the existing reference.** The reference already resolves to the file; H1 only adds the rename
   hooks. No new EP for find-usages (file targets are platform-handled).
2. **Both syntaxes for rename.** `src="…"` and `partial:blog/card` both rename via a uniform
   last-segment swap on the reference's range text.
3. **Safe, leaf-based edits.** Rewrite via `LeafPsiElement.replaceWithText` (same-type leaf) — no
   `ElementManipulator` registration; preserves quotes/surrounding text.
4. **Degrade gracefully.** Unresolvable/dynamic partials are not rename targets; colon-form *moves* fall
   back to a segment swap (full path is a documented follow-up).

## 3. Components

### 3.1 Reference shape (existing — context)

`references/AntlersPartialReferenceHelper`:
- **String form** `{{ partial:src="blog/card" }}` → `AntlersPartialReference(element = the T_STRING leaf,
  range = the inner path range, path = "blog/card")`. The range holds the **whole path**.
- **Colon form** `{{ partial:blog/card }}` → `AntlersPartialReference(element = the last path T_IDENT leaf
  (`card`), range = (0, leaf.length), path = "blog/card")`. The range holds **only the last segment**.

Both elements extend `LeafPsiElement` (custom `AntlersStringLeaf`/`AntlersIdentLeaf`). The reference is
non-soft and resolves to the partial `PsiFile`.

### 3.2 Rename hooks (`references/AntlersPartialReference.kt`)

Add two overrides plus a private rewrite helper:

```kotlin
override fun handleElementRename(newElementName: String): PsiElement {
    val newSegment = stripPartialExtensions(newElementName)        // "tile.antlers.html" -> "tile"
    val rangeText = rangeInElement.substring(element.text)         // "blog/card" (string) or "card" (colon)
    val newRangeText =
        if (rangeText.contains('/')) rangeText.substringBeforeLast('/') + "/" + newSegment
        else newSegment
    return rewrite(newRangeText)
}

override fun bindToElement(targetElement: PsiElement): PsiElement {
    val file = targetElement as? PsiFile ?: return element
    val root = StatamicProject.viewsRoot(element) ?: return element
    val newFullPath = relativePathMinusExt(root, file.virtualFile) ?: return element
    val rangeText = rangeInElement.substring(element.text)
    // String form: the range holds the whole path -> write the full new path (handles dir-change moves).
    // Colon form: the range holds only the last segment -> best-effort last-segment swap.
    val newRangeText = if (rangeText == path) newFullPath else newFullPath.substringAfterLast('/')
    return rewrite(newRangeText)
}

private fun rewrite(newRangeText: String): PsiElement {
    val leaf = element as? LeafPsiElement ?: return element
    val old = leaf.text
    val newText = old.substring(0, rangeInElement.startOffset) + newRangeText + old.substring(rangeInElement.endOffset)
    return leaf.replaceWithText(newText)
}
```

- `stripPartialExtensions(name)` removes `.antlers.html` then `.html` (mirrors `PARTIAL_EXTENSIONS`).
- `relativePathMinusExt(root, vf)` = the file's path relative to `root` with the partial extension
  stripped (reuses the same logic as `getVariants`/`getRelativePath`).
- `rewrite` splices `newRangeText` into the reference range and replaces the leaf with a same-type leaf;
  the returned element is the new leaf (per the `bindToElement`/`handleElementRename` contract).

**Effect:** Rename a partial file → the platform calls `handleElementRename(newName)` on every include →
each include's path updates (last segment for both forms). Move a partial → `bindToElement(newFile)` →
the `src="…"` form updates its full path; the colon form gets a best-effort segment swap. Rename invoked
*from* an include renames the resolved file and propagates the same way.

### 3.3 Find-usages (no new code expected)

`AntlersPartialReference` is non-soft and resolves to the partial `PsiFile`; `PsiReferenceBase.isReferenceTo`
returns true when `resolve()` equals the searched file. So Find Usages on a partial file runs
`ReferencesSearch`, scans templates containing the file's name, and lists each include — no
`FindUsagesProvider` (that EP describes custom *symbols*, not files). H1 adds a **test** confirming this;
the only fallback (if the search misses our leaf references) is a minimal `lang.findUsagesProvider` +
word-scanner, which the test will reveal as necessary or not.

## 4. File / Package Layout

```
references/AntlersPartialReference.kt   (add handleElementRename, bindToElement, rewrite helper)
references/AntlersPartialReferenceHelper.kt  (no change — reference shape is reused)
```

No `plugin.xml`, grammar, or lexer changes expected (unless the find-usages test shows a word-scanner is
needed).

## 5. Testing

`BasePlatformTestCase` (`myFixture.renameElement`, `myFixture.findUsages`).

- **Rename, string form:** partial `resources/views/blog/card.antlers.html` + template
  `{{ partial:src="blog/card" }}`; `renameElement(partialFile, "tile.antlers.html")` → template becomes
  `{{ partial:src="blog/tile" }}`.
- **Rename, colon form:** `{{ partial:blog/card }}` → `{{ partial:blog/tile }}`.
- **Rename from include:** rename the resolved file via the include's reference → file renamed + template
  updated.
- **Move, string form:** assert `bindToElement(newFile)` (the moved file) rewrites the `src` path to the
  new full relative path (unit-level if a programmatic move is fiddly in the harness).
- **Find-usages:** `findUsages(partialFile)` returns the include occurrence(s) across one or more
  templates.
- **No-op safety:** renaming an unrelated file doesn't touch templates; an unresolvable `{{ partial:x }}`
  is left alone.
- **Regression:** existing partial nav/completion tests stay green; full suite green.

## 6. Out of Scope (H2 / follow-ups)

- Blueprint-variable find-usages + rename (H2 — needs the bundled YAML-plugin dependency + cross-language
  rename).
- Colon-form *move* across directories (the path spans multiple leaves); only the segment swap is done.
- A `FindUsagesProvider` for custom symbols (only if the find-usages test proves the default insufficient).

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `replaceWithText` on a custom leaf misbehaves | Leaves extend `LeafPsiElement`; `replaceWithText` is the standard same-type-leaf replace; rename tests guard it |
| Colon-form move can't rewrite the multi-leaf path | Documented follow-up; rename (segment) works; string form fully works |
| Find-usages misses our references | Verified by test; fallback is a minimal word-scanner `findUsagesProvider` only if needed |
| Rewriting the wrong range | `rangeInElement` precisely covers the path/segment; the splice preserves quotes and surrounding text |
| Unresolvable / dynamic partial path | The reference resolves to null → not a rename target; left untouched |
| Programmatic move test fragility | Assert `bindToElement` output directly (unit-level) plus the rename integration test |
| `bindToElement` returning the wrong element | Returns the new leaf per contract; the reference re-attaches to it on the next pass |
