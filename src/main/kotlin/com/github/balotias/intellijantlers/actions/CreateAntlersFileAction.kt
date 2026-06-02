package com.github.balotias.intellijantlers.actions

import com.github.balotias.intellijantlers.AntlersIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

/** Adds "New -> Antlers Template", creating a starter `.antlers.html` from the bundled template. */
class CreateAntlersFileAction : CreateFileFromTemplateAction(
    "Antlers Template", "Creates a new Antlers template", AntlersIcons.FILE
) {
    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
        "Create Antlers Template"

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle("New Antlers Template")
            .addKind("Antlers Template", AntlersIcons.FILE, "Antlers Template")
    }
}
