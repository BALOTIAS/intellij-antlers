package com.github.balotias.intellijantlers.inspection

import com.github.balotias.intellijantlers.psi.AntlersIdentLeaf
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.github.balotias.intellijantlers.references.AntlersPartialReference
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/**
 * Weak warning on a `{{ partial:… }}` include whose path resolves to no view file, with a
 * "Create partial" fix (from the reference's own [AntlersPartialReference.getQuickFixes]). Only the path
 * tail is flagged, so each unresolved include reports once.
 */
class AntlersUnresolvedPartialInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.project.isDefault) return
                if (element !is AntlersIdentLeaf && element !is AntlersStringLeaf) return
                val ref = element.references.filterIsInstance<AntlersPartialReference>()
                    .firstOrNull { it.isPathTail } ?: return
                if (ref.resolve() != null) return
                holder.registerProblem(
                    element, "Cannot resolve partial", ProblemHighlightType.WEAK_WARNING, *ref.quickFixes
                )
            }
        }
}
