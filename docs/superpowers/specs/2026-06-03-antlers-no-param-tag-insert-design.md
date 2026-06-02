# Antlers No-Param Tag Insert — Design

**Date:** 2026-06-03
**Branch:** `antlers-no-param-tag-insert`
**Status:** Approved approach, pending spec review

## Goal

When completing a tag that takes **no parameters** (e.g. `nocache`, `markdown`, `yield`), don't drop
the caret into an empty parameter slot — there is nothing to type there, and for a pair tag it forces a
pointless "double Tab" to reach the block. Instead place the caret where the user actually works:

- no-param **pair** → `{{ nocache }}‹caret›{{ /nocache }}` (caret in the block)
- no-param **single** → `{{ yield }}‹caret›` (caret right after the tag)

Tags that **do** take params (`collection`, `cache`, `partial`, …) are unchanged: centred param slot
`{{ collection ‹caret› }}…` plus the repeating-param `AntlersParamSession`.

## Background (current state)

- `AntlersCompletionProvider` offers each catalog tag with `AntlersTagInsertHandler(tag.isPair)`.
- `AntlersTagInsertHandler` always builds a centred slot `{{ name  }}` (two spaces, caret centred),
  appends `{{ /name }}` for pairs, and **arms** `AntlersParamSession` (the repeating-param Tab flow).
- The catalog `TagDef.parameters: List<ParamDef>` is the signal: it is empty for content/marker tags
  (pairs: `section, markdown, obfuscate, children, nocache, parent, scope`; singles: `user, yield,
  redirect, increment, session, mix, vite, svg, trans, link, dump, get_content, 404`) and non-empty for
  param tags (`collection, cache, nav, partial, asset, …`).
- Logic keywords (`if`/`unless`/…) use a **different** handler (`AntlersKeywordInsertHandler`) and are
  out of scope — they keep their condition slot.

## Components

### 1. Pass the param signal — `AntlersCompletionProvider`

Change the construction to `AntlersTagInsertHandler(tag.isPair, tag.parameters.isNotEmpty())`.

### 2. No-param branch — `AntlersTagInsertHandler`

Add a `hasParams: Boolean` constructor arg. At the top of `handleInsert`, after computing `nameEnd`,
`nextClose`, `nextOpen`, `alreadyClosed` (existing logic), branch:

- **`!hasParams`:** normalize the opening tag to a single space `{{ name }}` (replace a blank gap with
  `" "`, or insert ` }}` / `}}` when not already closed), then:
  - pair → append `{{ /name }}` right after the opening `}}` and move the caret to **just after the
    opening `}}`** (inside the block).
  - single → move the caret to **just after `}}`** (after the tag).
  - **Do not arm** `AntlersParamSession`.
- **`hasParams`:** the existing centred-slot + closer + `AntlersParamSession` path, unchanged.

This mirrors the PLAIN branch of `AntlersKeywordInsertHandler` (single-space normalization, caret past
`}}`) for the single/opening-tag handling, plus the pair closer.

## Architecture

Local and additive: one constructor arg + one early branch in `AntlersTagInsertHandler`, one call-site
change in the provider. No catalog/grammar/parser change. The `hasParams` decision is data-driven from
`TagDef.parameters`, so adding params to a tag later automatically restores its slot.

## Edge cases

- **Already-closed with existing params in the opener** (completing into a populated tag): only the
  `hasParams` path can meaningfully hit this; for `!hasParams` the opener has no params by definition,
  so the blank-gap normalization applies. If a non-blank gap is somehow present, leave it and place the
  caret after `}}` (don't mangle user text).
- **`nocache` typed as `{{ nocache  }}`** (the typed-handler auto-inserted `  }}`): blank gap → collapse
  to `{{ nocache }}`, caret in block.

## Testing (`BasePlatformTestCase`)

Update `AntlersTagInsertTest`:
- **Change** `testSingleTagStartsInParamSlot` (yield, no params): now expects `{{ yield }}` with the
  caret **after** `}}` (`"{{ yield }}".length`), not the centred slot.
- **Keep** `testPartialStartsInParamSlot` (partial has params → centred slot `{{ partial  }}`),
  `testCollectionDefaultIsParamSlot`, `testNonHandleTagKeepsParamSlot` (cache) unchanged.
- **Add** `testNoParamPairCaretInBlock`: completing `nocache` in `{{ nocache<caret> }}` yields
  `{{ nocache }}{{ /nocache }}` with the caret at `"{{ nocache }}".length` (inside the block), and
  `AntlersParamSession.of(editor) == null`.
- **Add** `testNoParamSingleCaretAfterTag`: completing `yield` yields `{{ yield }}` caret after `}}`,
  no session.
- **Add** `testParamTagStillArmsSession`: completing `collection` still yields the centred slot and
  `AntlersParamSession.of(editor) != null` (guard that param tags are untouched).

Before committing, grep the test tree for other assertions completing a no-param tag (e.g. `yield`,
`nocache`, `markdown`) that expect a slot, and reconcile.

## Out of scope

- Colon-handle positioning for tags like `yield:section` / `svg:icon` (those take an argument via the
  `:` shorthand, not a param slot; the `:`-shorthand completion path handles that separately).
- Logic keywords (separate handler, keep their condition slot).
