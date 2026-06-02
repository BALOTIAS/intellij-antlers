package com.github.balotias.intellijantlers.editor

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
        val slotEmpty = before.isEmpty() || before.last() == ' '

        if (!slotEmpty) {
            // A param token ends at the caret → open a fresh slot after it.
            document.insertString(offset, " ")
            editor.caretModel.moveToOffset(offset + 1)
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
}
