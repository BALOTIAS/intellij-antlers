package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/** Inserts a tag parameter as `name="<caret>"`. */
object ParameterInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val tail = "=\"\""
        val at = context.tailOffset
        context.document.insertString(at, tail)
        context.editor.caretModel.moveToOffset(at + tail.length - 1)
        context.commitDocument()
    }
}
