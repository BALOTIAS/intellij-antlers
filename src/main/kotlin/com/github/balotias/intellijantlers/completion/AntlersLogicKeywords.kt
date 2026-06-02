package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/** How a logic keyword is inserted. */
enum class LogicKind { OPENER, MID, PLAIN }

/** An Antlers logic keyword and how to insert it. [closer] is the closing keyword for OPENERs. */
data class LogicKeyword(val name: String, val kind: LogicKind, val closer: String? = null)

/** Block openers — always offered in a `{{ }}` tag-name position. */
val LOGIC_OPENERS = listOf(
    LogicKeyword("if", LogicKind.OPENER, closer = "if"),
    LogicKeyword("unless", LogicKind.OPENER, closer = "unless"),
)

/** Offered only when the innermost open condition is `if`. */
val LOGIC_IF_FOLLOWERS = listOf(
    LogicKeyword("else", LogicKind.PLAIN),
    LogicKeyword("elseif", LogicKind.MID),
    LogicKeyword("endif", LogicKind.PLAIN),
)

/** Offered only when the innermost open condition is `unless` (Antlers `unless` has no `elseif`). */
val LOGIC_UNLESS_FOLLOWERS = listOf(
    LogicKeyword("else", LogicKind.PLAIN),
    LogicKeyword("endunless", LogicKind.PLAIN),
)

/**
 * Inserts a logic keyword. The platform has already placed the bare keyword with the caret right after
 * it. OPENER/MID build a centred condition slot `{{ kw  }}` (caret centred, a space each side) — OPENER
 * also appends `{{ /closer }}`; PLAIN keeps the single-space `{{ kw }}` and drops the caret just after
 * the closing `}}`. No `AntlersParamSession` is armed (a condition is one expression, not params).
 */
class AntlersKeywordInsertHandler(private val kw: LogicKeyword) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val nameEnd = context.tailOffset
        val text = document.charsSequence.toString()

        val nextClose = text.indexOf("}}", nameEnd)
        val nextOpen = text.indexOf("{{", nameEnd)
        val alreadyClosed = nextClose >= 0 && (nextOpen < 0 || nextClose < nextOpen)

        if (kw.kind == LogicKind.PLAIN) {
            val caret: Int
            if (alreadyClosed) {
                if (text.substring(nameEnd, nextClose) != " ") document.replaceString(nameEnd, nextClose, " ")
                caret = nameEnd + 1 + 2                       // past the single space + "}}"
            } else {
                val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
                val ins = if (needsSpace) " }}" else "}}"
                document.insertString(nameEnd, ins)
                caret = nameEnd + ins.length
            }
            context.editor.caretModel.moveToOffset(caret)
            context.commitDocument()
            return
        }

        // OPENER or MID: centred condition slot (same normalization as AntlersTagInsertHandler).
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

        if (kw.kind == LogicKind.OPENER) {
            document.insertString(openTagCloseStart + 2, "{{ /${kw.closer} }}")
        }
        context.editor.caretModel.moveToOffset(paramCaret)
        context.commitDocument()
    }
}
