package com.github.balotias.intellijantlers.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.Key

/**
 * One active param-entry session, armed right after a tag completion. Tracks the opening tag's `{{`
 * and `}}` via range markers (which shift as the user types params), so the Tab handler can tell
 * "still inside this tag" from "caret moved away" and locate the terminal stop. Stored on the editor
 * via [KEY]; validated lazily on each Tab — there is no caret listener.
 */
class AntlersParamSession(
    val openTagStartMarker: RangeMarker,
    val openTagEndMarker: RangeMarker,
    val isPair: Boolean,
) {
    /** Set once the caret has jumped to the terminal stop; the next Tab ends the session. */
    var reachedTerminal: Boolean = false

    /** Just after the opening `}}` — inside the block for a pair tag, after the tag for a single. */
    fun terminalOffset(): Int = openTagEndMarker.endOffset

    /** True only when both markers are valid, there is a single caret, and it sits inside the tag. */
    fun isValidFor(editor: Editor): Boolean {
        if (!openTagStartMarker.isValid || !openTagEndMarker.isValid) return false
        if (editor.caretModel.caretCount != 1) return false
        val caret = editor.caretModel.offset
        return caret in openTagStartMarker.startOffset..openTagEndMarker.endOffset
    }

    fun dispose() {
        openTagStartMarker.dispose()
        openTagEndMarker.dispose()
    }

    companion object {
        private val KEY: Key<AntlersParamSession> = Key.create("antlers.param.session")

        fun install(editor: Editor, session: AntlersParamSession) {
            clear(editor)
            editor.putUserData(KEY, session)
        }

        fun of(editor: Editor): AntlersParamSession? = editor.getUserData(KEY)

        fun clear(editor: Editor) {
            editor.getUserData(KEY)?.dispose()
            editor.putUserData(KEY, null)
        }
    }
}
