package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.github.balotias.intellijantlers.scope.NestingNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement

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

        val tree = AntlersNestingTreeBuilder.build(element, element.project)

        // Closers with no opener — flag only known constructs (conditions or catalog-isPair tags).
        for (stmt in tree.unmatchedClosers) {
            val closing = stmt.closingTag
            if (closing != null) {
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':') ?: continue
                if (name in CONDITION_OPENERS || catalog.tag(name)?.isPair == true) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Closing '/$name' has no matching opening tag.")
                        .range(stmt).create()
                }
            } else {
                val kw = (stmt.condition as? AntlersConditionMixin)?.keyword ?: continue
                holder.newAnnotation(HighlightSeverity.ERROR, "Closing '$kw' has no matching opening tag.")
                    .range(stmt).create()
            }
        }

        // Openers never closed (every node in the tree is already a known construct by construction).
        reportUnclosed(tree.roots, holder)

        // Paired tag whose closer has an explicit handle that differs from the opener's handle.
        reportHandleMismatch(tree.roots, holder)
    }

    private fun reportHandleMismatch(nodes: List<NestingNode>, holder: AnnotationHolder) {
        for (n in nodes) {
            val closer = n.closer
            if (closer != null) {
                val openerHandle = (n.opener.namePath as? AntlersNamePathMixin)
                    ?.segments?.drop(1)?.joinToString(":") ?: ""
                val closerName = (closer.closingTag as? AntlersClosingTagMixin)?.closedName ?: ""
                val closerHandle = closerName.substringAfter(":", "")
                if (openerHandle.isNotEmpty() && closerHandle.isNotEmpty() && openerHandle != closerHandle) {
                    holder.newAnnotation(HighlightSeverity.WARNING,
                        "Closing handle ':$closerHandle' does not match the opening '{{ ${n.name}:$openerHandle }}'.")
                        .range(closer).create()
                }
            }
            reportHandleMismatch(n.children, holder)
        }
    }

    private fun reportUnclosed(nodes: List<NestingNode>, holder: AnnotationHolder) {
        for (n in nodes) {
            if (n.closer == null) {
                holder.newAnnotation(HighlightSeverity.WARNING, "'{{ ${n.name} }}' is never closed.")
                    .range(n.opener).create()
            }
            reportUnclosed(n.children, holder)
        }
    }

    companion object {
        private val CONDITION_OPENERS = setOf("if", "unless")
        private val CONDITION_CLOSERS = mapOf("endif" to "if", "endunless" to "unless")
    }
}
