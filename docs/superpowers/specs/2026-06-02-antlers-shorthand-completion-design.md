# Antlers Shorthand Completion — Design

**Date:** 2026-06-02
**Branch:** `antlers-shorthand-completion`
**Status:** Approved approach, pending spec review

## Goal

Refine tag completion around the colon-shorthand (`{{ collection:blog }}`):

1. **Don't make the colon form the default.** Completing a tag the normal way (typing `coll`) inserts
   the parameter form; the shorthand is opt-in via a separate `:`-prefixed completion.
2. **Mirror the closer with a synced handle.** Per the Statamic docs, the colon shorthand closes with
   `{{ /collection:blog }}` (mirrors the full opener), not `{{ /collection }}`. Since the handle isn't
   known at insert time, the shorthand inserts a live-template with a synced `HANDLE` variable so the
   handle, typed once, fills both opener and closer.
3. **Warn on a shorthand-handle mismatch** (`{{ collection:blog }}…{{ /collection:news }}`).

## Background (current state)

- `AntlersTagInsertHandler` currently makes the colon form the DEFAULT for a curated
  `colonHandleTags = {collection,taxonomy,nav,foreach,partial}` set → `{{ collection:<caret> }}{{ /collection }}`.
  (This is what we're reverting/replacing.)
- `AntlersCompletionContext.classify`: the `T_COLON` branch returns `TAG_METHOD` (via `headOf`) or
  `NONE` when there's no head — so `{{ :coll }}` is currently `NONE`.
- `AntlersBalanceAnnotator` + `AntlersNestingTreeBuilder` already flag **unclosed** openers and
  **stray** closers (head-level, matching on `closedName.substringBefore(':')`), so a shorthand pair
  that is never closed, or a stray shorthand closer, is **already warned**. What's NOT detected is a
  **handle mismatch** (the nesting matches on the head and ignores the handle).
- Catalog tags supporting the colon-handle shorthand (verified present): `collection`, `taxonomy`,
  `nav`, `form`, `foreach`, `section`, `dictionary` (pair) and `partial` (single). Pair-ness is the
  tag's existing `isPair`.

## Components

### 1. Revert the default (no colon as default) — `AntlersTagInsertHandler`

Remove the `colonHandleTags` colon-form branch. Completing `collection` (typed `coll`) →
`{{ collection <caret> }}{{ /collection }}` (the existing pair param-slot path); `partial` →
`{{ partial<caret> }}` (single). No colon by default.

### 2. New `TAG_SHORTHAND` completion context — `AntlersCompletionContext`

Add `TAG_SHORTHAND` to `AntlersCompletionKind`. In the `T_COLON` branch, when the colon sits
**immediately after `{{`** (`prevSignificantLeaf(colon) == T_LDOUBLE`, i.e. no tag head) → return
`TAG_SHORTHAND`. So `{{ :<caret> }}` and `{{ :coll<caret> }}` trigger it. A colon AFTER a head
(`{{ collection:meth }}`) is unchanged (`TAG_METHOD`).

### 3. Shorthand completions + synced-template insert — provider + `ShorthandTagInsertHandler`

The provider, for `TAG_SHORTHAND`, offers each shorthand tag (the verified set ∩ catalog) as a lookup
element whose lookup string is the bare tag name (so the prefix `coll` typed after the `:` matches)
and whose presentable text shows the colon form (e.g. `:collection`). Its insert handler
`ShorthandTagInsertHandler(tag, isPair)`:

1. Finds the enclosing `{{ … }}` statement range around the caret and deletes it.
2. Starts a `TemplateManager` template at that offset:
   - pair → `{{ <tag>:$HANDLE$ }}$END${{ /<tag>:$HANDLE$ }}`
   - single → `{{ <tag>:$HANDLE$ }}$END$`
   with one `HANDLE` variable used in both places → IntelliJ auto-mirrors it, so the handle typed once
   fills the opener AND the closer (`/collection:blog`), and `$END$` is the final caret.

`SHORTHAND_TAGS = setOf("collection","taxonomy","nav","form","foreach","section","dictionary","partial")`
lives as a shared top-level val in the completion package (extensible).

### 4. Mirror the `coll` live template closer — `liveTemplates/Antlers.xml`

Change the `coll` template from `…{{ /collection }}` to `{{ collection:$HANDLE$ }}$END${{ /collection:$HANDLE$ }}`
(synced `$HANDLE$`) so the `coll`+Tab path matches the completion-shorthand behavior.

### 5. Handle-mismatch diagnostic (WARNING) — `AntlersBalanceAnnotator`

The balance annotator already walks the `NestingTree`. Add: for each `NestingNode` with a non-null
closer where the **opener has an explicit `:handle` AND the closer has an explicit `:handle` AND they
differ**, register a WARNING on the closer ("closing tag handle 'news' does not match the opening
'collection:blog'"). Handle = the name-path text after the head:
- opener: `(opener.namePath as AntlersNamePathMixin)` segments after the head, joined (`blog`, or
  `collection:blog` for `nav:collection:blog`).
- closer: `closedName` after the head.
Only warn when BOTH sides have an explicit handle and they differ — a head-only closer
(`{{ /collection }}`) is valid Statamic and must NOT be flagged. (Unclosed + stray cases are already
warned by the existing logic; this adds only the mismatch.)

## Architecture

Additive/local: an insert-handler revert, a new completion kind + classify branch, a new
`ShorthandTagInsertHandler` (uses `TemplateManager`), a shared `SHORTHAND_TAGS` val, one XML tweak,
and a handle-mismatch check in the balance annotator. No parser/grammar/dependency change.

## Testing (`BasePlatformTestCase`)

- **C1** (`AntlersTagInsertTest`): completing `collection`/`partial` now yields the param-slot/single
  default (update the colon-form tests that assert the old default).
- **C2** (`AntlersCompletionContextTest`): `{{ :coll<caret> }}` → `TAG_SHORTHAND`; `{{ :<caret> }}` →
  `TAG_SHORTHAND`; `{{ collection:meth<caret> }}` still `TAG_METHOD`.
- **C3** (`AntlersShorthandInsertTest`, new): drive the real shorthand completion with the template-
  testing harness (`TemplateManagerImpl.setTemplateTesting(testRootDisposable)`); complete `:coll`,
  type a handle (`blog`), assert the document is `{{ collection:blog }}…{{ /collection:blog }}` — i.e.
  the closer mirrored the handle. Single `:partial` → `{{ partial:blog }}` (no closer).
- **C4**: `liveTemplates/Antlers.xml` `coll` closer is the mirrored `{{ /collection:$HANDLE$ }}`
  (resource-text assertion).
- **C5** (`AntlersBalanceAnnotatorTest`): `{{ collection:blog }}…{{ /collection:news }}` →
  one WARNING (handle mismatch); `{{ collection:blog }}…{{ /collection }}` (head-only closer) → NO
  warning; `{{ collection:blog }}…{{ /collection:blog }}` → NO warning.

## Out of scope

- Offering the shorthand for tags whose colon is a fixed method (`glide:generate`) — those stay in the
  existing `TAG_METHOD` completion.
- Making the `{{ /` nearest-unclosed completion offer the full `collection:blog` (it offers the head,
  which is valid); separate, not requested.
- Escalating the existing unclosed/stray warnings to errors (kept at WARNING, per decision).
