package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Auto-inserts ` }}` and centres the caret when the user completes a `{{` opener: `{{ <caret> }}`. */
class AntlersTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != '{') return Result.CONTINUE
        if (file.viewProvider.baseLanguage !== AntlersLanguage.INSTANCE) return Result.CONTINUE

        val doc = editor.document
        val offset = editor.caretModel.offset
        val text = doc.charsSequence
        // Just typed the second '{' of a "{{" opener.
        if (offset < 2 || text[offset - 1] != '{' || text[offset - 2] != '{') return Result.CONTINUE
        // Not part of "{{{".
        if (offset >= 3 && text[offset - 3] == '{') return Result.CONTINUE
        // Don't double if "}}" already follows.
        val after = text.subSequence(offset, minOf(text.length, offset + 4)).toString().trimStart()
        if (after.startsWith("}}")) return Result.CONTINUE

        // If a stray single "}" was auto-inserted (e.g. by the HTML brace handler), replace it.
        if (offset < text.length && text[offset] == '}' && (offset + 1 >= text.length || text[offset + 1] != '}')) {
            doc.replaceString(offset, offset + 1, "  }}")
        } else {
            doc.insertString(offset, "  }}")
        }
        editor.caretModel.moveToOffset(offset + 1)
        return Result.STOP
    }
}
