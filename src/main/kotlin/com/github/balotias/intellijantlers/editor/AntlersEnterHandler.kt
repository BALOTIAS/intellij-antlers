package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile

/**
 * Expands an empty one-line matched block on a single Enter:
 * `{{ collection }}<caret>{{ /collection }}` becomes the opener, an indented caret line, and the
 * closer on its own line at the opener's indent. Fires only when, ignoring spaces/tabs (but not
 * newlines), the caret sits between an opener's `}}` and a closing tag `{{ /… }}`. Indentation is
 * computed from the code-style options, not the formatter.
 */
class AntlersEnterHandler : EnterHandlerDelegateAdapter() {

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffsetRef: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?,
    ): EnterHandlerDelegate.Result {
        if (file.viewProvider.baseLanguage !== AntlersLanguage.INSTANCE) return EnterHandlerDelegate.Result.Continue
        if (editor.caretModel.caretCount != 1) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        val text = document.charsSequence
        val caret = editor.caretModel.offset

        // Backward over spaces/tabs (stop at a newline) → must end on "}}".
        var i = caret
        while (i > 0 && (text[i - 1] == ' ' || text[i - 1] == '\t')) i--
        if (i < 2 || text[i - 1] != '}' || text[i - 2] != '}') return EnterHandlerDelegate.Result.Continue
        val openerEnd = i

        // Forward over spaces/tabs (stop at a newline) → must be a closing tag "{{" … "/".
        var j = caret
        while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
        if (j + 1 >= text.length || text[j] != '{' || text[j + 1] != '{') return EnterHandlerDelegate.Result.Continue
        var k = j + 2
        while (k < text.length && (text[k] == ' ' || text[k] == '\t')) k++
        if (k >= text.length || text[k] != '/') return EnterHandlerDelegate.Result.Continue
        val closerStart = j

        // Leading whitespace of the opener's line.
        var lineStart = openerEnd
        while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
        var e = lineStart
        while (e < openerEnd && (text[e] == ' ' || text[e] == '\t')) e++
        val openerIndent = text.subSequence(lineStart, e).toString()

        val opts = CodeStyle.getIndentOptions(file)
        val unit = if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
        val body = openerIndent + unit

        document.replaceString(openerEnd, closerStart, "\n$body\n$openerIndent")
        editor.caretModel.moveToOffset(openerEnd + 1 + body.length)
        return EnterHandlerDelegate.Result.Stop
    }
}
