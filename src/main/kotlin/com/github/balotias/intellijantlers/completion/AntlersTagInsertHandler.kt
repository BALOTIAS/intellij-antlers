package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Inserts an Antlers tag correctly.
 *
 * The platform has already inserted the bare tag name when this runs, with the caret right after it
 * (e.g. `{{ asset<caret>`). This handler:
 *  - closes the opening tag with ` }}` if the user hasn't typed `}}` yet, and
 *  - for pair/block tags, appends a matching `{{ /tag }}` and drops the caret on an indented body line.
 *
 * It never assumes braces are already present, so it cannot produce the broken `asset src="" }` output.
 */
class AntlersTagInsertHandler(private val isPair: Boolean) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val name = item.lookupString
        val caret = context.tailOffset
        val text = document.charsSequence.toString()

        // Is the opening tag already closed with `}}` before the next `{{`?
        val nextClose = text.indexOf("}}", caret)
        val nextOpen = text.indexOf("{{", caret)
        val hasClose = nextClose >= 0 && (nextOpen < 0 || nextClose < nextOpen)

        val openTagEnd: Int
        if (hasClose) {
            openTagEnd = nextClose + 2
        } else {
            val needsSpace = caret > 0 && text[caret - 1] != ' '
            val closing = if (needsSpace) " }}" else "}}"
            document.insertString(caret, closing)
            openTagEnd = caret + closing.length
        }

        if (isPair) {
            val block = "\n    \n{{ /$name }}"
            document.insertString(openTagEnd, block)
            // Caret on the indented body line (after the leading "\n    ").
            context.editor.caretModel.moveToOffset(openTagEnd + 5)
        } else {
            // Single tag: leave the caret just after the name so params can be typed.
            context.editor.caretModel.moveToOffset(caret)
        }

        context.commitDocument()
    }
}
