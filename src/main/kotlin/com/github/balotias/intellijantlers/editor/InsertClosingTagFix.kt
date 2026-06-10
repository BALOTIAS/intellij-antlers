package com.github.balotias.intellijantlers.editor

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/** Appends a `{{ /name }}` closer for an unclosed pair tag / condition. */
class InsertClosingTagFix(private val name: String) : IntentionAction {

    override fun getText(): String = "Insert closing '{{ /$name }}'"
    override fun getFamilyName(): String = "Insert closing tag"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true
    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val document = editor?.document ?: file?.viewProvider?.document ?: return
        val text = document.charsSequence
        val prefix = if (text.isNotEmpty() && text[text.length - 1] != '\n') "\n" else ""
        document.insertString(text.length, "$prefix{{ /$name }}")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
