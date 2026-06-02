package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Inserts an Antlers tag. The platform has already inserted the bare name with the caret right after
 * it. Single tags: ensure the opener closes, caret stays after the name. Pair tags: produce
 * `{{ name <caret> }}{{ /name }}` with the caret in the opening-tag parameter slot (one space each
 * side), the closer inline — the user expands to a block with Enter; no forced indent.
 */
class AntlersTagInsertHandler(private val isPair: Boolean) : InsertHandler<LookupElement> {

    private val colonHandleTags = setOf("collection", "taxonomy", "nav", "foreach", "partial")

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val name = item.lookupString
        val nameEnd = context.tailOffset
        val text = document.charsSequence.toString()

        val nextClose = text.indexOf("}}", nameEnd)
        val nextOpen = text.indexOf("{{", nameEnd)
        val alreadyClosed = nextClose >= 0 && (nextOpen < 0 || nextClose < nextOpen)

        if (name in colonHandleTags) {
            // Idiomatic colon form: `{{ collection:<caret> }}` (+ closer for pair tags).
            if (alreadyClosed) document.insertString(nameEnd, ":")
            else document.insertString(nameEnd, ": }}")
            val caretPos = nameEnd + 1                       // right after the ':'
            if (isPair) {
                val close = document.charsSequence.toString().indexOf("}}", caretPos)
                if (close >= 0) document.insertString(close + 2, "{{ /$name }}")
            }
            context.editor.caretModel.moveToOffset(caretPos)
            context.commitDocument()
            return
        }

        if (!isPair) {
            if (!alreadyClosed) {
                val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
                document.insertString(nameEnd, if (needsSpace) " }}" else "}}")
            }
            context.editor.caretModel.moveToOffset(nameEnd)
            context.commitDocument()
            return
        }

        // Pair tag.
        val openTagEnd: Int
        val paramCaret: Int
        if (alreadyClosed) {
            val gap = text.substring(nameEnd, nextClose)
            if (gap.isBlank()) {
                // `{{ name<gap>}}` → normalize to `{{ name  }}`, caret centred.
                document.replaceString(nameEnd, nextClose, "  ")
                paramCaret = nameEnd + 1
                openTagEnd = nameEnd + 4               // "  " + "}}"
            } else {
                // Existing params in the opener → caret right after the name.
                paramCaret = nameEnd
                openTagEnd = nextClose + 2
            }
        } else {
            val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
            val opener = if (needsSpace) "  }}" else " }}"
            document.insertString(nameEnd, opener)
            paramCaret = nameEnd + (if (needsSpace) 1 else 0)
            openTagEnd = nameEnd + opener.length
        }

        document.insertString(openTagEnd, "{{ /$name }}")
        context.editor.caretModel.moveToOffset(paramCaret)
        context.commitDocument()
    }
}
