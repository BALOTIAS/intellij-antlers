package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Flags unbalanced paired tags / conditions by replaying the file's statements through a name stack
 * (the same algorithm as AntlersFoldingBuilder). Only judges KNOWN pair constructs — conditions and
 * catalog-isPair tags — so unknown/addon tags never produce false positives. Tolerant; never throws.
 */
class AntlersBalanceAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is AntlersFile) return            // run once, on the Antlers file root
        if (element.project.isDefault) return
        val catalog = AntlersCatalogService.getInstance(element.project)

        val stack = ArrayDeque<Pair<String, AntlersStatement>>()
        val statements = PsiTreeUtil.findChildrenOfType(element, AntlersStatement::class.java)
            .sortedBy { it.textRange.startOffset }
        for (stmt in statements) {
            val closing = stmt.closingTag
            if (closing != null) {
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':') ?: continue
                val idx = stack.indexOfLast { it.first == name }
                if (idx >= 0) {
                    while (stack.size > idx) stack.removeLast()
                } else if (name in CONDITION_OPENERS || catalog.tag(name)?.isPair == true) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Closing '/$name' has no matching opening tag.")
                        .range(stmt).create()
                }
                continue
            }
            val condition = stmt.condition
            if (condition != null) {
                val kw = (condition as? AntlersConditionMixin)?.keyword ?: continue
                val openerName = CONDITION_CLOSERS[kw]
                if (openerName != null) {
                    val idx = stack.indexOfLast { it.first == openerName }
                    if (idx >= 0) {
                        while (stack.size > idx) stack.removeLast()
                    } else {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Closing '$kw' has no matching opening tag.")
                            .range(stmt).create()
                    }
                } else if (kw in CONDITION_OPENERS) {
                    stack.addLast(kw to stmt)
                }
                continue
            }
            val namePath = stmt.namePath as? AntlersNamePathMixin ?: continue
            val head = namePath.head
            if (head.isBlank()) continue
            if (catalog.tag(head)?.isPair == true) stack.addLast(head to stmt)
        }
        for ((name, open) in stack) {
            holder.newAnnotation(HighlightSeverity.WARNING, "'{{ $name }}' is never closed.")
                .range(open).create()
        }
    }

    companion object {
        private val CONDITION_OPENERS = setOf("if", "unless")
        private val CONDITION_CLOSERS = mapOf("endif" to "if", "endunless" to "unless")
    }
}
