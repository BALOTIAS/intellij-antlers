package com.github.balotias.intellijantlers.editor

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager

/** Deletes a closing tag (`{{ /name }}` / `{{ endif }}`) that has no matching opener. */
class RemoveStrayCloserFix(closer: PsiElement) : IntentionAction {

    private val pointer = SmartPointerManager.createPointer(closer)

    override fun getText(): String = "Remove stray closing tag"
    override fun getFamilyName(): String = "Remove stray closing tag"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = pointer.element != null
    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        pointer.element?.delete()
    }
}
