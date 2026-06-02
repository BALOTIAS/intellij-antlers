package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.TemplateManager

/** Tags that take a project handle via the `{{ tag:handle }}` colon shorthand. */
val SHORTHAND_TAGS = setOf("collection", "taxonomy", "nav", "form", "foreach", "section", "dictionary", "partial")

/**
 * Inserts a colon-shorthand tag as a live template with a synced `HANDLE` variable, so the handle
 * typed once fills both the opener and the mirrored closer: `{{ collection:blog }}{{ /collection:blog }}`.
 */
class ShorthandTagInsertHandler(private val tag: String, private val isPair: Boolean) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val text = document.charsSequence.toString()
        val caret = context.tailOffset
        val stmtStart = text.lastIndexOf("{{", caret)
        if (stmtStart < 0) return
        val closeIdx = text.indexOf("}}", caret)
        val stmtEnd = if (closeIdx in stmtStart until text.length) closeIdx + 2 else caret
        document.deleteString(stmtStart, stmtEnd)
        context.commitDocument()
        context.editor.caretModel.moveToOffset(stmtStart)

        val tm = TemplateManager.getInstance(context.project)
        val body = if (isPair) "{{ $tag:\$HANDLE\$ }}\$END\${{ /$tag:\$HANDLE\$ }}"
                   else "{{ $tag:\$HANDLE\$ }}\$END\$"
        val template = tm.createTemplate("", "", body)
        template.addVariable("HANDLE", "", "", true)
        template.isToReformat = false
        tm.startTemplate(context.editor, template)
    }
}
