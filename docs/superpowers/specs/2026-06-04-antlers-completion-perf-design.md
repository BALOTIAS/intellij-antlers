# Antlers Completion Performance — Cache the Statement List — Design

**Date:** 2026-06-04
**Branch:** `antlers-completion-perf`
**Status:** Approved approach, pending spec review
**Source:** perf check of Konafets #118/#173 ("typing lag on large files").

## Goal

Cut the O(file-size)-per-keystroke cost in completion. A behavior-invisible performance change — no
user-facing behavior changes; existing tests must stay green.

## Background (measured root cause)

A benchmark on large templates showed completion latency growing with file size (133 ms at 4.5k lines →
240 ms at 9k lines). The cause: each completion calls — uncached — `AntlersNestingTreeBuilder.nearestUnclosedAt`
(twice, in the `TAG_NAME` branch), `AntlersScopeResolver.scopesAt`, and `AntlersFieldContext.fieldsInScope`
(which routes through `scopesAt`). All three independently run:

```kotlin
PsiTreeUtil.findChildrenOfType(file, AntlersStatement::class.java).sortedBy { it.textRange.startOffset }
```

— a **full-PSI-tree traversal** (visits every node), once per call, ~4× per completion. The subsequent
stack replays are cheap arithmetic over the collected list. The traversal is the dominant, cacheable cost.
The three call sites are `AntlersNestingTreeBuilder.kt:46` (`build`), `:86` (`nearestUnclosedAt`), and
`AntlersScopeResolver.kt:44` (`scopesAt`). None of the `scope/` results are cached.

## Component — `scope/AntlersStatements` (new)

A single memoized helper returning the file's `AntlersStatement`s sorted by start offset, cached per-file
on `PsiModificationTracker.MODIFICATION_COUNT` (the project's standard idiom, same as
`ViewFrontMatterService`):

```kotlin
object AntlersStatements {
    /** All AntlersStatements in [file], sorted by start offset; cached per-file on the PSI mod count. */
    fun sortedIn(file: PsiFile): List<AntlersStatement> =
        CachedValuesManager.getCachedValue(file) {
            val list = PsiTreeUtil.findChildrenOfType(file, AntlersStatement::class.java)
                .sortedBy { it.textRange.startOffset }
            CachedValueProvider.Result.create(list, PsiModificationTracker.MODIFICATION_COUNT)
        }
}
```

(The no-explicit-key `getCachedValue(file) { … }` overload keys by the provider lambda's class, so it does
not collide with other cached values on the same file — consistent with `ViewFrontMatterService`.)

### Consumers (replace the inline traversal with the cached list)

The cached list is already sorted, so each site drops its own `.sortedBy { … }` and keeps only its cheap
per-position filter:

- `AntlersNestingTreeBuilder.build(root, project)` (`:46-47`): the local
  `val statements = findChildrenOfType(root, …).sortedBy { … }` becomes
  `val statements = (root as? PsiFile)?.let { AntlersStatements.sortedIn(it) } ?: findChildrenOfType(root, AntlersStatement::class.java).sortedBy { it.textRange.startOffset }`.
  (`root` is the file in every caller, but a non-`PsiFile` root falls back to the direct traversal so the
  function stays robust.)
- `AntlersNestingTreeBuilder.nearestUnclosedAt(root, beforeOffset, project)` (`:86-88`): the
  `findChildrenOfType(root, …).filter { startOffset < beforeOffset }.sortedBy { … }` becomes
  `cachedFor(root).filter { it.textRange.startOffset < beforeOffset }` (same `PsiFile`-or-fallback helper).
- `AntlersScopeResolver.scopesAt(element)` (`:44-46`): `file` is `element.containingFile` (a `PsiFile`),
  so `findChildrenOfType(file, …).filter { endOffset <= caret }.sortedBy { … }` becomes
  `AntlersStatements.sortedIn(file).filter { it.textRange.endOffset <= caret }`.

A small private `cachedFor(root: PsiElement): List<AntlersStatement>` in the nesting builder encapsulates
the "`PsiFile` → cached, else direct" fallback so both nesting functions share it.

## Data flow

Completion (or the balance annotator) → `build` / `nearestUnclosedAt` / `scopesAt` → `AntlersStatements.sortedIn(file)`
(first call traverses + caches; the rest of the ~4 calls in the same completion hit the cache) → each
applies its cheap offset filter → stack replay. Between PSI edits, repeat calls on the same file instance
are O(1) for the traversal.

## Correctness / edge cases

- Cache invalidates on any PSI change (`MODIFICATION_COUNT`), so no staleness; a completion copy is a fresh
  `PsiFile` per keystroke → its cache fills once, then the ~4 sub-calls reuse it.
- `build`/`nearestUnclosedAt` accept a `PsiElement root`; in practice it is always the file, but the
  `PsiFile`-or-fallback keeps them correct for any root (and identical output — the fallback is the exact
  prior expression).
- The cached list is pre-sorted by start offset; every consumer's filter preserves order, so dropping the
  per-site `.sortedBy` is output-equivalent.
- Empty file → empty list (cached); no special-casing.

## Testing

- **Behavior unchanged (the safety net):** the full existing suite stays green — in particular every
  `AntlersCompletionTest` / scope / `AntlersBalanceAnnotatorTest` / `AntlersFoldingTest` case (they exercise
  `nearestUnclosedAt`, `scopesAt`, and `build` and would break on any output change).
- **Cache unit test** (`AntlersStatementsTest`): `sortedIn(file)` returns the statements in start-offset
  order; two calls within one modification generation return the **same list instance** (proving it's
  cached); after an edit (`myFixture.type(...)` + commit) the returned list reflects the new statement count.
- **Perf re-check (manual, documented):** re-run the benchmark from the perf check; expect completion
  latency to drop and stop scaling steeply with file size (the ~4 traversals collapse to 1 per completion).
  Not a CI assertion (timing is environment-dependent) — recorded in the commit / backlog.

## Out of scope

- Caching the full nesting tree or per-position scope frames (position-dependent; not the dominant cost).
- Optimizing the per-element annotators or the per-edit full-file highlight (separate concern; the
  benchmark showed those are viewport-incremental in the real editor).
- Incremental/blueprint-scan caching beyond what already exists.
