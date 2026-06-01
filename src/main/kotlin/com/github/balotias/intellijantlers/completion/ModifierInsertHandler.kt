package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/** Inserts a modifier; if it takes arguments, adds `()` and puts the caret inside. */
class ModifierInsertHandler(private val takesArguments: Boolean) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        if (!takesArguments) return
        val tail = "()"
        context.document.insertString(context.tailOffset, tail)
        context.editor.caretModel.moveToOffset(context.tailOffset + 1)
        context.commitDocument()
    }
}
