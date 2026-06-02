package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.editor.AntlersParamSession
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Inserts an Antlers tag. A tag that takes parameters gets a centred param slot `{{ name <caret> }}`
 * (plus `{{ /name }}` for pairs) and arms the repeating-param [AntlersParamSession]. A tag with NO
 * parameters skips the slot/session: the caret lands in the block (pair) or right after the tag
 * (single), since there is nothing to type inside the tag.
 */
class AntlersTagInsertHandler(
    private val isPair: Boolean,
    private val hasParams: Boolean,
) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val name = item.lookupString
        val nameEnd = context.tailOffset
        val text = document.charsSequence.toString()

        val nextClose = text.indexOf("}}", nameEnd)
        val nextOpen = text.indexOf("{{", nameEnd)
        val alreadyClosed = nextClose >= 0 && (nextOpen < 0 || nextClose < nextOpen)

        if (!hasParams) {
            // No params → no slot. Single-space the opener; caret past "}}" (in the block for a pair).
            val closeStart: Int
            if (alreadyClosed) {
                val gap = text.substring(nameEnd, nextClose)
                if (gap.isBlank()) {
                    if (gap != " ") document.replaceString(nameEnd, nextClose, " ")
                    closeStart = nameEnd + 1
                } else {
                    closeStart = nextClose            // existing params (rare) → don't mangle text
                }
            } else {
                val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
                val ins = if (needsSpace) " }}" else "}}"
                document.insertString(nameEnd, ins)
                closeStart = nameEnd + ins.length - 2
            }
            val afterClose = closeStart + 2
            if (isPair) document.insertString(afterClose, "{{ /$name }}")
            context.editor.caretModel.moveToOffset(afterClose)
            context.commitDocument()
            return
        }

        // Param tag: centred slot, capture where the caret goes and where "}}" starts.
        val paramCaret: Int
        val openTagCloseStart: Int
        if (alreadyClosed) {
            val gap = text.substring(nameEnd, nextClose)
            if (gap.isBlank()) {
                document.replaceString(nameEnd, nextClose, "  ")
                paramCaret = nameEnd + 1
                openTagCloseStart = nameEnd + 2
            } else {
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
