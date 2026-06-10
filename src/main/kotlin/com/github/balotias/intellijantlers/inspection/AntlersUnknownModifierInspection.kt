package com.github.balotias.intellijantlers.inspection

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement

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
                val known = AntlersCatalogService.getInstance(project).modifiers().map { it.name }
                if (name in known) return
                val ident = element.node.findChildByType(AntlersTypes.T_IDENT)?.psi ?: element
                val fixes = closestMatches(name, known).map { ChangeModifierFix(it) }.toTypedArray()
                holder.registerProblem(ident, "Unknown modifier '$name'", ProblemHighlightType.WEAK_WARNING, *fixes)
            }
        }

    /** Up to 3 known modifier names within a small edit distance of [name] (likely typos), nearest first. */
    private fun closestMatches(name: String, known: List<String>): List<String> {
        val max = if (name.length <= 4) 1 else 2
        return known.asSequence()
            .map { it to levenshtein(name, it) }
            .filter { it.second in 1..max }
            .sortedBy { it.second }
            .map { it.first }
            .take(3)
            .toList()
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /** Rewrites the flagged modifier name to a suggested known modifier. */
    private class ChangeModifierFix(private val suggestion: String) : LocalQuickFix {
        override fun getName() = "Change to '$suggestion'"
        override fun getFamilyName() = "Change to a known modifier"
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            (descriptor.psiElement as? LeafPsiElement)?.replaceWithText(suggestion)
        }
    }
}
