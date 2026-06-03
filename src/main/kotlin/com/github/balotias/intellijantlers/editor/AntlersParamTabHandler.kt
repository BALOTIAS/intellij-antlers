package com.github.balotias.intellijantlers.editor

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

/**
 * Implements the repeating param tab-stop flow on `EditorTab`. Engages only while an
 * [AntlersParamSession] is armed and the caret sits inside the tracked opening tag; otherwise it
 * delegates to the platform's original Tab handler (indent), so normal editing is untouched.
 *
 * Slot rule (param region = just-after-`{{` .. start-of-`}}`):
 *  - the slot is *empty* when the text before the caret is empty or ends with a space → jump to the
 *    terminal stop (collapsing any dangling spaces to a single separator first);
 *  - otherwise a param token ends at the caret → open a fresh slot (insert one space, keep the caret
 *    in the new slot).
 */
class AntlersParamTabHandler(private val original: EditorActionHandler) : EditorActionHandler() {

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        original.isEnabled(editor, caret, dataContext) || AntlersParamSession.of(editor) != null

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val session = AntlersParamSession.of(editor)
        if (session == null) {
            original.execute(editor, caret, dataContext)
            return
        }
        if (!session.isValidFor(editor) || session.reachedTerminal) {
            AntlersParamSession.clear(editor)
            original.execute(editor, caret, dataContext)
            return
        }

        val document = editor.document
        val offset = editor.caretModel.offset
        val regionStart = session.openTagStartMarker.endOffset  // just after "{{"
        val tagEnd = session.openTagEndMarker.startOffset        // start of "}}"
        if (offset < regionStart || offset > tagEnd) {
            AntlersParamSession.clear(editor)
            original.execute(editor, caret, dataContext)
            return
        }

        val chars = document.charsSequence
        val before = chars.subSequence(regionStart, offset).toString()
        // Condition mode (if/unless/elseif): always jump to the block — never open a param slot.
        val slotEmpty = session.conditionMode || before.isEmpty() || before.last() == ' '

        if (!slotEmpty) {
            // A param ends at/after the caret → open a fresh slot past the end of the current param.
            // If the caret sits inside a quoted value (`from="te|st"`), advance past the closing quote
            // first so we never split the value with the inserted space.
            val insertAt = endOfCurrentParam(chars, regionStart, offset, tagEnd)
            document.insertString(insertAt, " ")
            editor.caretModel.moveToOffset(insertAt + 1)
            // Offer parameter-name suggestions in the fresh slot.
            editor.project?.let { AutoPopupController.getInstance(it).scheduleAutoPopup(editor) }
        } else {
            // Empty slot → collapse dangling spaces to a single separator, jump to the terminal stop.
            var lastNonSpace = tagEnd - 1
            while (lastNonSpace >= regionStart && chars[lastNonSpace] == ' ') lastNonSpace--
            val gapStart = lastNonSpace + 1
            if (gapStart < tagEnd) {
                document.replaceString(gapStart, tagEnd, " ")
            }
            editor.caretModel.moveToOffset(session.terminalOffset())
            session.reachedTerminal = true
        }
    }

    /**
     * The offset just past the end of the param the caret is touching. Normally that is the caret
     * itself, but when the caret is inside a quoted value the param ends after the matching closing
     * quote, so we scan `[regionStart, offset)` for an open quote and, if found, advance to just past
     * its closing quote (bounded by [tagEnd]). An unterminated quote falls back to the caret.
     */
    private fun endOfCurrentParam(chars: CharSequence, regionStart: Int, offset: Int, tagEnd: Int): Int {
        var quote: Char? = null
        var i = regionStart
        while (i < offset) {
            val c = chars[i]
            when {
                quote == null && (c == '"' || c == '\'') -> quote = c
                quote == c -> quote = null
            }
            i++
        }
        val open = quote ?: return offset
        var j = offset
        while (j < tagEnd) {
            if (chars[j] == open) return j + 1
            j++
        }
        return offset
    }
}
