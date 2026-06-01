package com.github.balotias.intellijantlers.inspection

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/** Weak warning on a `| modifier` whose name is in neither the bundled catalog nor the project scan. */
class AntlersUnknownModifierInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is AntlersModifierMixin) return
                val name = element.modifierName
                if (name.isBlank()) return
                val project = element.project
                if (project.isDefault) return
                if (AntlersCatalogService.getInstance(project).modifiers().any { it.name == name }) return
                val ident = element.node.findChildByType(AntlersTypes.T_IDENT)?.psi ?: element
                holder.registerProblem(ident, "Unknown modifier '$name'", ProblemHighlightType.WEAK_WARNING)
            }
        }
}
