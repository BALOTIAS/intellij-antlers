package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Inserts an Antlers tag with a centred parameter slot: `{{ name <caret> }}` (one space each side of
 * the caret), plus `{{ /name }}` appended for pair tags. Starting the caret in a real slot lets the
 * user type a param immediately, and the [AntlersParamSession] armed here drives the repeating
 * Tab-to-next-param / Tab-to-block flow (see AntlersParamTabHandler).
 */
class AntlersTagInsertHandler(private val isPair: Boolean) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val name = item.lookupString
        val nameEnd = context.tailOffset
        val text = document.charsSequence.toString()

        val nextClose = text.indexOf("}}", nameEnd)
        val nextOpen = text.indexOf("{{", nameEnd)
        val alreadyClosed = nextClose >= 0 && (nextOpen < 0 || nextClose < nextOpen)

        // Build the opening tag's param slot. `paramCaret` is the centred caret; `openTagCloseStart`
        // is the offset of this opening tag's "}}".
        val paramCaret: Int
        val openTagCloseStart: Int
        if (alreadyClosed) {
            val gap = text.substring(nameEnd, nextClose)
            if (gap.isBlank()) {
                // `{{ name<gap>}}` → normalize to `{{ name  }}`, caret centred.
                document.replaceString(nameEnd, nextClose, "  ")
                paramCaret = nameEnd + 1
                openTagCloseStart = nameEnd + 2
            } else {
                // Existing params already in the opener → caret right after the name.
                paramCaret = nameEnd
                openTagCloseStart = nextClose
            }
        } else {
            val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
            val opener = if (needsSpace) "  }}" else " }}"
            document.insertString(nameEnd, opener)
            paramCaret = nameEnd + (if (needsSpace) 1 else 0)
            openTagCloseStart = nameEnd + opener.length - 2
        }

        if (isPair) {
            document.insertString(openTagCloseStart + 2, "{{ /$name }}")
        }
        context.editor.caretModel.moveToOffset(paramCaret)
        context.commitDocument()

        // Arm the param-entry session. `{{` for this statement is the last "{{" at/before the name.
        val openTagStart = text.lastIndexOf("{{", nameEnd)
        if (openTagStart >= 0) {
            val startMarker = document.createRangeMarker(openTagStart, openTagStart + 2)
            val endMarker = document.createRangeMarker(openTagCloseStart, openTagCloseStart + 2)
            startMarker.isGreedyToRight = false
            endMarker.isGreedyToLeft = false
            AntlersParamSession.install(
                context.editor,
                AntlersParamSession(startMarker, endMarker, isPair),
            )
        }
    }
}
