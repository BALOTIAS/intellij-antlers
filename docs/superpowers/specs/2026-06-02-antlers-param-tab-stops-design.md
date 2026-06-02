# Antlers Param Tab Stops — Design

**Date:** 2026-06-02
**Branch:** `antlers-param-tab-stops`
**Status:** Approved approach, pending spec review

## Goal

Give the normal tag completion a repeating parameter "tab-stop" flow. After completing a tag, the
caret lands in the opening-tag parameter slot; pressing **Tab** while a param is being typed creates a
fresh slot for the next param, and pressing Tab on an empty slot jumps the caret to the terminal stop
(inside the block for a pair tag, after the tag for a single tag). This delivers, by hand, the
"repeating add-param stop" that IntelliJ live templates cannot express natively (their tab stops are a
fixed set).

Example, completing `collection`:

```
{{ collection ‹caret› }}{{ /collection }}
  type  from="blog"   Tab →
{{ collection from="blog" ‹caret› }}{{ /collection }}
  type  limit="5"      Tab on empty slot →
{{ collection from="blog" limit="5" }}‹caret›{{ /collection }}
  Tab again → normal indent, session over
```

## Background (current state)

- `completion/AntlersTagInsertHandler.kt` inserts a completed tag and positions the caret directly
  (no tab stops). Pair tags become `{{ name ‹caret› }}{{ /name }}` with the caret in the param slot;
  single tags become `{{ name‹caret› }}` (caret after the name, closed if needed). This is the path we
  augment.
- `completion/ShorthandTagInsertHandler.kt` (the `:coll` path) uses a live template with a synced
  `HANDLE` variable. It is **out of scope** here and unchanged.
- Condition keywords (`{{ if … }}`) are inserted by a different path and are **out of scope** (their
  single expression is not a param list).
- There is currently no custom `EditorTab` action handler, so Tab today does the platform default
  (indent) inside Antlers files.

## Components

### 1. `AntlersParamSession` — marker holder (`editor/AntlersParamSession.kt`, new)

A small data holder describing one active param-entry session, stored on the editor via a
`Key<AntlersParamSession>` in user data. Fields:

- `openTagStartMarker: RangeMarker` — marks the opening tag's `{{` (its **start** offset). Bounds the
  left edge of "is the caret still in this tag".
- `openTagEndMarker: RangeMarker` — marks the opening tag's `}}` (its **start** offset). The param
  region is `[regionStart, openTagEndMarker.startOffset)`. Created un-greedy on the left so typed
  params before it push it right.
- `isPair: Boolean` — retained for clarity/tests and any future divergence; behavior of the terminal
  jump is identical regardless.
- `reachedTerminal: Boolean` (mutable, default `false`) — set once the caret has jumped to the
  terminal stop, so the next Tab ends the session.

- `terminalOffset(): Int` = `openTagEndMarker.endOffset` (just after `}}` — inside the block for a pair
  tag, after the tag for a single tag; the offset expression is the same, only the text already sitting
  there differs).
- `isValidFor(editor): Boolean` returns true only when both markers are valid, the editor has exactly
  one caret (`caretModel.caretCount == 1`), and the caret offset is within
  `[openTagStartMarker.startOffset, openTagEndMarker.endOffset]`.

Companion helpers:

- `install(editor, session)` — puts it in user data.
- `clear(editor)` — removes it and disposes both markers.
- `of(editor): AntlersParamSession?` — reads it back.

### 2. Arming the session — `AntlersTagInsertHandler`

After it finishes inserting and positions the caret (existing logic unchanged), the handler creates
the two range markers (`{{` start and opening `}}` start) and installs an `AntlersParamSession` —
every tag can carry params, so this is armed on every tag completion (pair and single). Concretely, at the end of
both the single and pair branches, before `context.commitDocument()` returns, compute:

- `openTagStart` = the `{{` offset for this statement (search back from `nameEnd` for `"{{"`).
- `openTagEndStart` = the offset of the opening tag's `}}` (for pairs this is `openTagEnd - 2`; for
  singles it is the `}}` we inserted or found).

Create the markers from `context.document`, build the session (`isPair`), and `install`. If the offsets
can't be resolved (defensive: tag not well-formed), skip arming — no session, Tab stays default.

> Note: the param slot the caret starts in must contain at least one space so the "empty slot"
> detection has somewhere to sit. The existing pair path already inserts `{{ name  }}` (two spaces,
> caret centred); the single path inserts `{{ name }}` with the caret right after the name. For singles
> we add one leading space so the caret starts in a real slot: `{{ name ‹caret› }}`. (This is the only
> behavioral change to the existing insert positioning, and only for single tags.)

### 3. The Tab handler — `editor/AntlersParamTabHandler.kt` (new)

`class AntlersParamTabHandler(private val original: EditorActionHandler) : EditorActionHandler()`.

`doExecute(editor, caret, dataContext)`:

1. Resolve `session = AntlersParamSession.of(editor)`. If null → `original.execute(...)`, return.
2. If `!session.isValidFor(editor)` (invalid marker / multi-caret / caret outside range) →
   `AntlersParamSession.clear(editor)`, `original.execute(...)`, return.
3. If `session.reachedTerminal` → clear the session, `original.execute(...)` (normal indent), return.
4. Otherwise run the param logic (single caret, within the opening tag):
   - Let `offset = caret.offset`, `tagEnd = openTagEndMarker.startOffset`.
   - Look at the text between the start of the param region and `offset` to find the **current slot**:
     the run of non-space characters ending at `offset` (the param token the caret is touching), and
     whether the slot is "empty" (the character before the caret is a space or the param-region start,
     i.e. nothing typed in this slot).
   - **Non-empty slot** (a param token precedes the caret): ensure exactly one space follows the token
     up to the caret, then insert a new empty slot — concretely, if the char at `offset` is not already
     a space, insert `" "` at `offset` and move the caret to `offset + 1`; if it is already a space,
     just move the caret one past it. Net effect: caret now sits in a fresh empty slot. Session stays
     active (not terminal).
   - **Empty slot** (caret has no param token before it in this slot): collapse any dangling spaces in
     the param region down to the canonical single space after the last token (so we don't leave
     `{{ collection from="x"   }}`), then move the caret to `session.terminalOffset()`. Set
     `session.reachedTerminal = true`. Session stays installed so a subsequent Tab (step 3) ends it.
   - `editor.document` edits run inside the action's write context (the handler executes as a write
     action); commit not required for caret moves but call `PsiDocumentManager.commitDocument` is not
     needed since we operate on the document directly.

`isEnabled` / `isEnabledForCaret`: return true (we always claim the action and decide inside
`doExecute`, delegating to `original` when not applicable) — or delegate `original.isEnabled` so we
never report enabled when the platform wouldn't be. Use the delegating form:
`isEnabledForCaret(...) = original.isEnabledForCaret(...) || AntlersParamSession.of(editor) != null`.

### 4. Registration — `plugin.xml`

```xml
<editorActionHandler action="EditorTab"
    implementationClass="com.github.balotias.intellijantlers.editor.AntlersParamTabHandler"/>
```

The platform constructs it with the previously-registered handler as the `original` constructor arg,
so non-Antlers / no-session Tab presses pass straight through.

## Slot detection — precise rule

Given the param region `[regionStart, tagEnd)` and the caret `offset` (regionStart ≤ offset ≤ tagEnd):

- `before = document.text.substring(regionStart, offset)`.
- The **current slot is empty** when `before` is empty or ends with a space (`before.isEmpty() ||
  before.last() == ' '`). → terminal jump.
- Otherwise the **current slot is non-empty** (a param token ends at the caret). → new slot.

This makes the flow deterministic and independent of what is to the right of the caret, which matches
the "type a param, Tab, type the next" interaction.

## Lifecycle / exit conditions (all handled by lazy validation at Tab time)

- **Click away / caret moves out of the tag:** `isValidFor` sees the caret outside
  `[openTagStartMarker.startOffset, openTagEndMarker.endOffset]` → clears + delegates. No caret
  listener needed.
- **Multiple carets:** `editor.caretModel.caretCount > 1` → not valid → delegate.
- **Marker invalidated (undo removed the insertion):** `marker.isValid == false` → clear + delegate.
- **Completion popup / live template active during Tab:** those consume Tab via their own actions
  before `EditorTab` fires, so our handler isn't even invoked; if it is, the session caret check still
  guards us.
- **Reached terminal then Tab again:** `reachedTerminal` branch clears + delegates to normal indent.

The session never persists beyond the current tag interaction in any way that affects later Tabs — the
first Tab outside the tracked range disposes it.

## Architecture

Additive and local: one new `AntlersParamSession` holder, one new `AntlersParamTabHandler` registered
on `EditorTab`, one arming call + a one-space tweak (singles) in `AntlersTagInsertHandler`, and one
`plugin.xml` line. No parser/grammar/dependency change. The shorthand and condition paths are
untouched.

## Testing (`BasePlatformTestCase`)

New file `completion/AntlersParamTabTest.kt` (drives real completion + `EditorTab` action):

- **T1 — pair, repeating params:** complete `collection`, `type("from=\"blog\"")`,
  `performEditorAction(ACTION_EDITOR_TAB)`; assert document contains `from="blog" ` with the caret in a
  new empty slot before `}}`. Type `limit="5"`, Tab again → another slot. Confirms repeat.
- **T2 — pair, empty slot jumps into block:** from T1's state with an empty slot, Tab → assert caret
  offset is immediately after `}}` and before `{{ /collection }}`, and the param region collapsed to a
  single trailing space (`{{ collection from="blog" limit="5" }}`).
- **T3 — terminal Tab ends session:** from T2, Tab again → assert the document gains normal indentation
  behavior (a tab/spaces inserted at caret per the editor's indent) and `AntlersParamSession.of(editor)
  == null`.
- **T4 — single tag:** complete a single tag (e.g. `partial`), assert the caret starts in a slot
  (`{{ partial ‹caret› }}`), type a param, Tab → new slot; Tab on empty → caret moves to just after
  `}}`; session cleared.
- **T5 — click-away fallback:** complete a pair tag (session armed), move the caret outside the tag
  (`editor.caretModel.moveToOffset(0)`), `performEditorAction(ACTION_EDITOR_TAB)` → assert normal
  indent happened at offset 0 and the session was cleared (no exception, no param mutation).
- **T6 — no session, normal Tab untouched:** in a plain Antlers file with no completion,
  `performEditorAction(ACTION_EDITOR_TAB)` indents as before (regression guard that the handler
  delegates).

## Out of scope

- Pre-filling known/required params as stops (rejected earlier — clutters the tag).
- Applying the flow to condition keywords (`{{ if … }}`) or to the `:coll` shorthand path.
- Esc-to-cancel as an explicit key binding (exit is via clicking away or Tab-past-end; lazy validation
  covers it).
- Smart awareness of *which* params are valid for the tag while tabbing (this is positional only; param
  *name* completion already exists separately).
