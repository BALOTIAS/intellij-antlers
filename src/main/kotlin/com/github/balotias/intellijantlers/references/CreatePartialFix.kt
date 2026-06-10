package com.github.balotias.intellijantlers.references

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil

/** Creates the missing view file for an unresolved `{{ partial:… }}` reference. */
class CreatePartialFix(private val rawPath: String) : LocalQuickFix {

    override fun getName(): String = "Create partial '$rawPath'"
    override fun getFamilyName(): String = "Create partial"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val root = StatamicProject.viewsRoot(element) ?: return
        val rel = rawPath.replace('.', '/')                  // Statamic/Laravel dot notation → path
        val dirPath = rel.substringBeforeLast('/', "")
        val fileName = rel.substringAfterLast('/') + ".antlers.html"
        val dir = if (dirPath.isEmpty()) root else VfsUtil.createDirectoryIfMissing(root, dirPath)
        if (dir.findChild(fileName) == null) dir.createChildData(this, fileName)
    }
}
