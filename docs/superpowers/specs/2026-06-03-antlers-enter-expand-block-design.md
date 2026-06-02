# Antlers Enter-Expand Block — Design

**Date:** 2026-06-03
**Branch:** `antlers-enter-expand-block`
**Status:** Approved approach, pending spec review

## Goal

Pressing **Enter once** with the caret inside an empty matched block expands it into an indented
three-line structure, putting the closing tag on its own line at the opener's indent:

```
{{ collection }}‹caret›{{ /collection }}
```
becomes (caret on the indented middle line):
```
{{ collection }}
    ‹caret›
{{ /collection }}
```

This matches the just-completed-block case (`AntlersTagInsertHandler` / logic-keyword inserts leave the
caret exactly between `}}` and `{{ /name }}`) and works for logic blocks (`{{ if x }}‹caret›{{ /if }}`)
the same way. Today this needs two Enters (one to break the line, one to push the closer down).

## Background (current state)

- There is **no** Enter handler in the plugin — only `AntlersTypedHandler` (`{`) and the `EditorTab`
  param handler. HTML's "enter between tags" does not apply: `{{ }}` are outer-language tokens to HTML.
- A single Enter today just inserts one newline, leaving the closer on the caret's line.
- The formatter (`AntlersHtmlFormattingModelBuilder`) reuses the HTML formatter and has documented
  indentation quirks, so this feature computes indentation itself rather than relying on it.

## Components

### `AntlersEnterHandler` (`editor/AntlersEnterHandler.kt`, new)

`class AntlersEnterHandler : EnterHandlerDelegateAdapter`, overriding `preprocessEnter`. Registered:

```xml
<enterHandlerDelegate implementation="com.github.balotias.intellijantlers.editor.AntlersEnterHandler"/>
```

`preprocessEnter(file, editor, caretOffsetRef, caretAdvance, dataContext, originalHandler): Result`:

1. **Guard the language:** `file.viewProvider.baseLanguage === AntlersLanguage.INSTANCE`, single caret.
   Else `Result.Continue`.
2. **Detect the empty matched block** (text scan on `document.charsSequence`, `caret = caretOffsetRef.get()`):
   - Walk **backward** from `caret` over spaces/tabs only (stop at a newline → not a one-line block).
     Require the two chars ending there are `}}`. Call that position `openerEnd`.
   - Walk **forward** from `caret` over spaces/tabs only (stop at a newline). Require the text there
     starts with `{{`, then (skipping spaces/tabs) a `/` — i.e. a closing tag. Call the `{{` position
     `closerStart`.
   - If either side fails, `Result.Continue` (normal Enter).
3. **Compute indentation:**
   - `lineStart` = start of the line containing `openerEnd`; `openerIndent` = the leading whitespace of
     that line (chars from `lineStart` up to the first non-whitespace).
   - `unit` from `CodeStyle.getIndentOptions(file)`: `"\t"` if `USE_TAB_CHARACTER` else
     `" ".repeat(INDENT_SIZE)`.
4. **Rewrite:** `document.replaceString(openerEnd, closerStart, "\n" + openerIndent + unit + "\n" + openerIndent)`.
   Move the caret to `openerEnd + 1 + openerIndent.length + unit.length` (end of the body indent on the
   middle line) via `caretOffsetRef.set(...)` **and** `editor.caretModel.moveToOffset(...)`.
5. Return `EnterHandlerDelegate.Result.Stop` so the platform does not insert its own newline.

No PSI, formatter, or grammar dependency — pure text + code-style indent options.

## Edge cases

- **Caret directly adjacent** (`}}‹caret›{{ /x }}`, the post-completion case): backward/forward skip
  zero whitespace; works.
- **Whitespace between** (`}} ‹caret› {{ /x }}`): the spaces are part of `[openerEnd, closerStart)` and
  get replaced, normalizing to the clean three-line form.
- **Already multi-line** (`}}` and `{{` separated by a newline): the backward/forward scan stops at the
  newline → does not fire (normal Enter).
- **Non-empty block** (`}}foo‹caret›{{ /x }}`): backward scan hits `o` (not `}}`) → does not fire.
- **No closer / single tag** (`{{ partial }}‹caret›`): forward scan finds no `{{ /` → does not fire.
- **Lone closer after a variable** (`{{ title }}‹caret›{{ /x }}`): technically fires (shape matches);
  harmless — splitting an empty region between `}}` and `{{ /` is always reasonable. Name-matching is
  intentionally **not** required (the chosen trigger is "empty matched block", shape-based).

## Testing (`BasePlatformTestCase`)

New `editor/AntlersEnterHandlerTest.kt`. Helper types Enter and reads the document (source of truth):

```kotlin
private fun enter(textWithCaret: String): String {
    myFixture.configureByText("p.antlers.html", textWithCaret)
    myFixture.type("\n")
    return myFixture.editor.document.text
}
private fun unit(): String {
    val o = com.intellij.psi.codeStyle.CodeStyle.getIndentOptions(myFixture.file)
    return if (o.USE_TAB_CHARACTER) "\t" else " ".repeat(o.INDENT_SIZE)
}
```

- **E1 col-0 block:** `enter("{{ collection }}<caret>{{ /collection }}")` ==
  `"{{ collection }}\n${unit()}\n{{ /collection }}"`; caret at `"{{ collection }}\n${unit()}".length`.
- **E2 indented opener:** `enter("  {{ if x }}<caret>{{ /if }}")` ==
  `"  {{ if x }}\n  ${unit()}\n  {{ /if }}"` (body = openerIndent + unit; closer = openerIndent).
- **E3 whitespace between normalizes:** `enter("{{ collection }} <caret> {{ /collection }}")` ==
  the same as E1.
- **E4 logic block:** `enter("{{ unless x }}<caret>{{ /unless }}")` ==
  `"{{ unless x }}\n${unit()}\n{{ /unless }}"`.
- **E5 non-empty block does NOT fire:** for `enter("{{ collection }}foo<caret>{{ /collection }}")`,
  assert exactly **one** `\n` was inserted (`docText.count { it == '\n' } == 1`) — our handler stayed
  out and the default Enter ran.
- **E6 already multi-line does NOT double-expand:** `enter("{{ collection }}\n<caret>\n{{ /collection }}")`
  inserts exactly one more `\n` (normal Enter; our handler doesn't fire because a newline precedes the
  caret on the backward scan).

## Out of scope

- Re-running the formatter / reflowing surrounding HTML (we only touch the empty region).
- Name-matching the closer to the opener (chosen trigger is shape-based).
- Backspace/Delete collapsing the expanded block (separate concern).
