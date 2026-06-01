package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/** Inserts a tag parameter as `name="<caret>"`. */
object ParameterInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val tail = "=\"\""
        context.document.insertString(context.tailOffset, tail)
        context.editor.caretModel.moveToOffset(context.tailOffset + tail.length - 1)
        context.commitDocument()
    }
}
