# Antlers Completion Insert UX — Design

**Date:** 2026-06-02
**Branch:** `antlers-completion-insert-ux`
**Status:** Approved approach, pending spec review

## Goal

Fix and improve the *as-you-insert* completion experience — four small, independent improvements:

1. **Caret-position bug:** completing a parameter/modifier leaves the caret past the `""`/`()` (in
   the `}}`) instead of inside it.
2. **Smart tag prefills:** completing a handle-taking tag inserts the idiomatic colon form with the
   caret ready for the handle.
3. **Auto-popup:** completion should pop up as you type inside `{{ }}` (verify; fix if suppressed).
4. **Closing-tag auto-fill:** typing `{{ /` offers the nearest unclosed tag, pre-selected (one Enter).

These all live in the live-editor insert/typed pipeline that PSI-level unit tests don't exercise —
each gets a test that drives the real handler (`completeBasic()`/`finishLookup`, asserting the caret).

## Component 1 — Caret-position fix (`ParameterInsertHandler`, `ModifierInsertHandler`)

**Root cause:** both read `context.tailOffset` twice; `tailOffset` *advances* when you insert at it,
so the second read is already past the inserted text. `ParameterInsertHandler` inserts `="\"\""` then
moves to `advancedTail + 2` → lands ~2 chars into the ` }}`. `ModifierInsertHandler` (`()`) is off by
one the same way.

**Fix:** capture the offset once.
```kotlin
val at = context.tailOffset
context.document.insertString(at, tail)
context.editor.caretModel.moveToOffset(at + tail.length - 1)
context.commitDocument()
```
- Parameter `="\"\""` (len 3) → `at + 2` = inside `""`.
- Modifier `()` (len 2) → `at + 1` = inside `()`.

## Component 2 — Smart tag prefills, colon form (`AntlersTagInsertHandler`)

A curated, extensible set of handle-taking tags:
```kotlin
private val COLON_HANDLE_TAGS = setOf("collection", "taxonomy", "nav", "foreach", "partial")
```
When the completed tag is in the set, insert a colon directly after the name and drop the caret after
it (the user types the handle next):
- **pair** (`collection`/`nav`/`taxonomy`/`foreach`) → `{{ collection:<caret> }}{{ /collection }}`
- **single** (`partial`) → `{{ partial:<caret> }}`

All other tags keep the current behavior (pair → `{{ name <caret> }}{{ /name }}` param slot; single →
caret after the name). The colon path handles both the normal flow (the typed handler already
inserted `}}`, so `{{ collection }}` → insert `:` after the name → `{{ collection:<caret> }}`) and the
not-yet-closed flow (insert `: }}`). Pair tags additionally append `{{ /name }}` after the opener.

Isolated in the insert handler; extending the set later is a one-line change. (The existing
`partial:src="…"` parameter-value completion is unaffected — this only changes the *initial* tag
insertion to the colon form, which is also a valid partial syntax.)

## Component 3 — Auto-popup (verify; fix if suppressed)

Inside a template-over-HTML file the HTML data language can suppress the as-you-type completion popup
within `{{ }}`. **First verify** (manually / via the auto-popup test harness) whether the popup
appears while typing an identifier inside `{{ }}`.

If suppressed, add to `AntlersTypedHandler`:
```kotlin
override fun checkAutoPopup(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file.viewProvider.baseLanguage !== AntlersLanguage.INSTANCE) return Result.CONTINUE
    if (!(c.isLetter() || c == '_' || c == ':' || c == '|')) return Result.CONTINUE
    // Only inside an Antlers {{ }} construct (not in surrounding HTML).
    val el = file.findElementAt(editor.caretModel.offset - 1)
    if (PsiTreeUtil.getParentOfType(el, AntlersStatement::class.java) == null) return Result.CONTINUE
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
    return Result.STOP
}
```
**Testing caveat:** auto-popup E2E needs `CompletionAutoPopupTestCase` (a distinct base from
`BasePlatformTestCase`). If that proves impractical here, verify the `checkAutoPopup` predicate logic
with a direct unit test (right char + inside-`{{ }}` → would schedule; HTML region / wrong char →
CONTINUE) and document that the scheduling itself is verified manually.

## Component 4 — Closing-tag auto-fill (prioritize nearest unclosed)

**Context:** add `isClosing: Boolean = false` to `AntlersCompletionInfo`; set it `true` in the
`T_SLASH` branch of `AntlersCompletionContext.classify` (the `{{ /<caret>` position, which already
classifies as `TAG_NAME`).

**Nearest-unclosed helper:** `AntlersClosingTagHelper.nearestUnclosedTag(position, project): String?`
— a forward stack walk over the `AntlersStatement`s whose start offset is before the current `{{`:
push on an opener (a catalog `isPair` tag head, or an `if`/`unless` condition), pop on a matching
closer; the stack top is the nearest unclosed tag name. Reuses the open/close classification from
`AntlersNestingTreeBuilder` (extract the shared predicate or call a new `nearestOpenAt` on it to keep
the logic in one place).

**Provider:** in the `TAG_NAME` branch, when `info.isClosing` and `nearestUnclosedTag(...)` is
non-null, add that tag as a **top-priority** lookup element via
`PrioritizedLookupElement.withPriority(LookupElementBuilder.create(name)…, Double.MAX_VALUE)` with an
insert handler that ensures `name }}` (the closer). It sorts first and auto-selects, so `{{ /` →
popup with the right closer highlighted → Enter. The normal tag list still follows (so the user can
pick a different one).

## Architecture

- Components 1–2: insert-handler edits. Component 3: a `TypedHandlerDelegate.checkAutoPopup` method.
  Component 4: an `AntlersCompletionInfo.isClosing` flag + a small nearest-unclosed helper +
  provider prioritization. All additive; no parser/grammar/dependency change.

## Testing (`BasePlatformTestCase` unless noted)

- **C1** (`AntlersParamModifierInsertTest`, new): complete a parameter → assert text + caret inside
  `name="<caret>"`; complete an arg-taking modifier → caret inside `name(<caret>)`. Drives the real
  handler via `completeBasic()`/`finishLookup`.
- **C2** (extend `AntlersTagInsertTest`): completing `collection` → `{{ collection:<caret> }}{{ /collection }}`,
  caret right after the colon; completing `partial` → `{{ partial:<caret> }}`; a non-handle pair tag
  (e.g. `cache`) keeps the param-slot behavior.
- **C3:** the `checkAutoPopup` predicate unit test (or `CompletionAutoPopupTestCase` if feasible);
  document whichever is used.
- **C4** (`AntlersClosingTagTest`, new): in `{{ collection }}{{ /<caret> }}` the completion's first/
  auto-selected element is `collection`; the `nearestUnclosedTag` helper returns the innermost open
  tag (nested `{{ collection }}{{ if x }}{{ /<caret>` → `if`); no unclosed → no prioritized element.
- Full suite stays green.

## Out of scope

- True auto-insert (no Enter) for closing tags — pre-selected + Enter is the chosen behavior.
- A per-`TagDef` handle-taking flag (curated set is simpler; revisit if it grows).
- Reworking parameter-value completion or the catalog.
