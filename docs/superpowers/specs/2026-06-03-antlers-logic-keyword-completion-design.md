# Antlers Logic-Keyword Completion — Design

**Date:** 2026-06-03
**Branch:** `antlers-logic-keyword-completion`
**Status:** Approved approach, pending spec review

## Goal

Restore in-brace completion for Antlers logic keywords (`if`, `unless`, `else`, `elseif`, `endif`,
`endunless`). They are **not** catalog tags — they were only ever surfaced as live templates, and the
commit that scoped live templates to *outside* `{{ }}` (to fix `{{ {{ if }} }}` doubling) left no
in-brace completion in their place. Typing `{{ if` now offers no `if`. This adds them as first-class
`TAG_NAME`-context completions, **context-aware** so that `else`/`elseif`/`endif`/`endunless` appear
only when the caret is inside the matching open block.

## Background (current state)

- `AntlersCompletionContext.classify`: a caret right after `{{` (or while typing the tag head)
  classifies as `TAG_NAME` (`prev == T_LDOUBLE`). A caret after `{{ /` is `TAG_NAME, isClosing=true`.
- `AntlersCompletionProvider` `TAG_NAME` arm: for `isClosing`, it prepends the nearest unclosed tag
  (`AntlersNestingTreeBuilder.nearestUnclosedAt(file, stmtStart, project)`) as a prioritized "Close
  tag", then offers `catalog.tags()` + scoped fields + loop/nav/system vars. `catalog.tags()` does
  **not** include `if`/`unless`/etc. (verified — 48 `TagDef`s, none are logic keywords).
- `AntlersNestingTreeBuilder` already models conditions: `CONDITION_OPENERS = {if, unless}`,
  `CONDITION_CLOSERS = {endif→if, endunless→unless}`; `else`/`elseif` are condition keywords that are
  transparent to nesting (neither open nor close). `nearestUnclosedAt(root, beforeOffset, project)`
  returns the innermost still-open pair tag **or** condition name (`"if"`, `"unless"`, or a pair-tag
  head), or null.
- `AntlersBalanceAnnotator` + folding + structure view all consume the nesting tree, so once a
  `{{ if … }}{{ /if }}` is inserted they balance/fold it with no extra work.
- `AntlersTagInsertHandler` arms the repeating-param `AntlersParamSession`. Logic keywords must **not**
  reuse it: a condition is a single expression (`x and y` contains spaces), so repeating-param Tab
  would wrongly split it. Logic keywords get a plain caret with no session.

## Components

### 1. Keyword model + insert handler — `completion/AntlersLogicKeywords.kt` (new)

```kotlin
enum class LogicKind { OPENER, MID, PLAIN }

data class LogicKeyword(val name: String, val kind: LogicKind, val closer: String? = null)

val LOGIC_OPENERS = listOf(
    LogicKeyword("if", LogicKind.OPENER, closer = "if"),
    LogicKeyword("unless", LogicKind.OPENER, closer = "unless"),
)
// Mid/closing keywords keyed by the innermost open condition they belong to.
val LOGIC_IF_FOLLOWERS = listOf(
    LogicKeyword("else", LogicKind.PLAIN),
    LogicKeyword("elseif", LogicKind.MID),
    LogicKeyword("endif", LogicKind.PLAIN),
)
val LOGIC_UNLESS_FOLLOWERS = listOf(
    LogicKeyword("else", LogicKind.PLAIN),
    LogicKeyword("endunless", LogicKind.PLAIN),
)
```

(`elseif` is offered only for `if`; Antlers `unless` takes `else`/`endunless` but not `elseif`.)

`AntlersKeywordInsertHandler(keyword: LogicKeyword) : InsertHandler<LookupElement>` — the platform has
already inserted the bare keyword with the caret right after it (`nameEnd = context.tailOffset`). Using
the same "already closed?" detection as `AntlersTagInsertHandler` (find the next `}}` vs next `{{`):

- **OPENER** (`if`/`unless`): produce the centred condition slot `{{ if  }}{{ /if }}` — a space on each
  side of the caret (i.e. two spaces between the keyword and `}}`, caret centred), the opener closed,
  and `{{ /<closer> }}` appended right after the opening `}}`. Typing a condition reads `{{ if x }}`.
  This is exactly `AntlersTagInsertHandler`'s pair path **minus the session** and with the closer name
  = `kw.closer`.
- **MID** (`elseif`): produce the centred slot `{{ elseif  }}` (caret centred), opener closed, **no**
  appended closer.
- **PLAIN** (`else`/`endif`/`endunless`): leave `{{ else }}` (single space, the typed form), and move
  the caret to **after** the opening `}}` (past the tag). Ensure the tag is closed (`}}` present).

No `AntlersParamSession` is installed in any branch. The OPENER/MID branches reuse the same
alreadyClosed / needsSpace normalization as `AntlersTagInsertHandler` so `{{ if<caret> }}` →
`{{ if  }}…` with the caret centred.

### 2. Offer the keywords — `AntlersCompletionProvider` `TAG_NAME` arm

In the `TAG_NAME` branch, **only when `!info.isClosing`** (the `{{ /…` closing path is unchanged), after
computing the statement start `stmtStart` (same value already used to seed the closing-tag lookup —
extract it once), add the logic keywords:

```kotlin
// Always: the block openers.
for (kw in LOGIC_OPENERS) addLogic(result, kw)        // if, unless
// Followers, scoped to the innermost open condition.
when (AntlersNestingTreeBuilder.nearestUnclosedAt(file, stmtStart, project)) {
    "if"     -> LOGIC_IF_FOLLOWERS.forEach { addLogic(result, it) }
    "unless" -> LOGIC_UNLESS_FOLLOWERS.forEach { addLogic(result, it) }
}
```

where `addLogic` builds a `LookupElementBuilder.create(kw.name)` with `AntlersIcons.FILE`, type text
`"Logic"` (block keywords) and the `AntlersKeywordInsertHandler(kw)`. The `stmtStart` is
`PsiTreeUtil.getParentOfType(position, AntlersStatement::class.java)?.textRange?.startOffset ?:
parameters.offset` (the existing `isClosing` code computes the same; refactor so both paths share it).

`else` must not be added twice when both follower lists contain it — they are mutually exclusive
branches of the `when`, so only one list is ever used; no dedup needed. The keyword names never collide
with `catalog.tags()` (none of if/unless/else/elseif/endif/endunless is a catalog tag), and they are
added before/independently of the field/var loops which use their own `seen` set (seeded from
`catalog.tags()` only) — keyword names won't suppress a same-named field, but a field literally named
`if` is not a real Statamic handle, so this is acceptable and out of scope.

## Architecture

Additive and local: one new file (keyword model + insert handler), a context-aware block added to the
existing `TAG_NAME` provider arm reusing `nearestUnclosedAt`, no grammar/parser/catalog/dependency
change. Balance, folding, and structure view already understand the inserted constructs.

## Edge cases

- **Caret already inside an open `{{ if }}` typing `el`:** `nearestUnclosedAt` (computed from
  statements strictly before `stmtStart`) returns `"if"` → `else`/`elseif`/`endif` offered; prefix
  matcher filters to `else`/`elseif`.
- **Innermost open frame is a pair tag** (`{{ if }}{{ collection }}<caret>`): `nearestUnclosedAt`
  returns `"collection"` → followers suppressed (correct: you close `collection` first). `if`/`unless`
  remain offered (you can always open a nested condition).
- **Top level, nothing open:** only `if`/`unless` offered.
- **`{{ /…` closing context:** unchanged — handled by the existing `isClosing` prioritized-closer path
  (`{{ /if }}` etc.).
- **OPENER insert when the statement is already closed with text after the head** (rare, completing
  into a populated tag): mirror `AntlersTagInsertHandler`'s alreadyClosed branch — caret after the
  keyword, still append the closer.

## Testing (`BasePlatformTestCase`)

New `completion/AntlersLogicKeywordTest.kt`. A `lookups(text)` helper returns
`myFixture.lookupElementStrings`; an `insert(text, keyword)` helper completes and asserts against
`myFixture.editor.document.text` (the document is the source of truth — `myFixture.file.text` lags
uncommitted edits) and `myFixture.caretOffset`.

- **L1 openers offered:** `lookups("{{ i<caret> }}")` contains `if`; `lookups("{{ un<caret> }}")`
  contains `unless`.
- **L2 followers suppressed at top level:** `lookups("{{ e<caret> }}")` contains neither `else`,
  `elseif`, nor `endif` (nothing open).
- **L3 followers offered inside if:** `lookups("{{ if x }}{{ e<caret> }}")` contains `else`, `elseif`,
  `endif`; does **not** contain `endunless`.
- **L4 followers offered inside unless:** `lookups("{{ unless x }}{{ e<caret> }}")` contains `else`,
  `endunless`; does **not** contain `elseif` or `endif`.
- **L5 suppressed when a pair tag is innermost:** `lookups("{{ if x }}{{ collection }}{{ e<caret> }}")`
  contains neither `else` nor `endif` (still offers `if`/`unless`).
- **L6 opener insert:** completing `if` in `{{ i<caret> }}` yields `{{ if  }}{{ /if }}` (two spaces,
  caret centred at `"{{ if ".length`); no `AntlersParamSession` armed
  (`AntlersParamSession.of(editor) == null`).
- **L7 mid insert:** completing `elseif` inside an open-if context (e.g. `{{ if x }}{{ el<caret> }}`)
  yields `…{{ elseif  }}` (two spaces, caret centred), with no closer appended for that statement.
- **L8 plain insert:** completing `endif` inside an open-if context yields `…{{ endif }}` with the
  caret immediately after that statement's closing `}}`.

## Out of scope

- Tab-to-block after typing a condition (no session by design; would conflict with spaced conditions).
- Smart placement/validation of `else` vs `elseif` ordering, or detecting a duplicate `else`.
- `switch`/`case` (catalog tag handling, separate).
- Re-enabling live templates inside `{{ }}` (intentionally scoped out to avoid delimiter doubling; this
  feature replaces that path with proper completion).
