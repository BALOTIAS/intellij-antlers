package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.catalog.ModifierDef
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression

/**
 * Inserts a modifier. With required parameters it starts a live template `(p1, p2)` whose tab-stops
 * default to the parameter names; otherwise (args but none required) it inserts `()` with the caret
 * inside; a no-argument modifier inserts nothing extra.
 */
class ModifierInsertHandler(private val def: ModifierDef) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val required = def.parameters.filter { !it.optional }
        if (def.parameters.isEmpty() && !def.takesArguments) return

        if (required.isEmpty()) {
            val at = context.tailOffset
            context.document.insertString(at, "()")
            context.editor.caretModel.moveToOffset(at + 1)
            context.commitDocument()
            return
        }

        val mgr = TemplateManager.getInstance(context.project)
        val template = mgr.createTemplate("", "")
        template.isToReformat = false
        template.addTextSegment("(")
        required.forEachIndexed { i, p ->
            if (i > 0) template.addTextSegment(", ")
            template.addVariable(p.name, TextExpression(p.name), true)
        }
        template.addTextSegment(")")
        context.editor.caretModel.moveToOffset(context.tailOffset)
        mgr.startTemplate(context.editor, template)
    }
}
