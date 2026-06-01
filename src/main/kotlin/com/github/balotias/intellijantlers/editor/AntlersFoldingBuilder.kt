package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersNoparseBlock
import com.github.balotias.intellijantlers.psi.AntlersPhpBlock
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

private val CONDITION_OPENERS = setOf("if", "unless")
private val CONDITION_CLOSERS = mapOf("endif" to "if", "endunless" to "unless")

class AntlersFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val out = mutableListOf<FoldingDescriptor>()

        // Single-node folds: noparse, php composite nodes.
        PsiTreeUtil.findChildrenOfType(root, AntlersNoparseBlock::class.java).forEach { addNode(out, it) }
        PsiTreeUtil.findChildrenOfType(root, AntlersPhpBlock::class.java).forEach { addNode(out, it) }

        // Comment folds: T_COMMENT_OPEN/TEXT/CLOSE are registered as comment tokens, so they
        // appear as PsiComment leaf nodes (not AntlersComment composite nodes). Scan for
        // T_COMMENT_OPEN leaves and pair each with its T_COMMENT_CLOSE sibling.
        addCommentFoldsFromTokens(root, out)

        // Paired tag / condition folds via a name stack over statements in document order.
        val catalog = if (root.project.isDefault) null else AntlersCatalogService.getInstance(root.project)
        val stack = ArrayDeque<Pair<String, AntlersStatement>>()
        val statements = PsiTreeUtil.findChildrenOfType(root, AntlersStatement::class.java)
            .sortedBy { it.textRange.startOffset }
        for (stmt in statements) {
            val closing = stmt.closingTag
            val condition = stmt.condition
            if (closing != null) {
                // {{ /tag }} or {{ /if }}: match against opener on the stack
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':') ?: continue
                val idx = stack.indexOfLast { it.first == name }
                if (idx >= 0) {
                    val (_, open) = stack.removeAt(idx)
                    while (stack.size > idx) stack.removeLast() // drop unmatched inner opens
                    val range = TextRange(open.textRange.startOffset, stmt.textRange.endOffset)
                    if (range.length > 0) out.add(FoldingDescriptor(open.node, range))
                }
            } else if (condition != null) {
                val keyword = (condition as? AntlersConditionMixin)?.keyword ?: continue
                val openerName = CONDITION_CLOSERS[keyword]
                if (openerName != null) {
                    // {{ endif }} / {{ endunless }}: pop the matching opener and emit a fold
                    val idx = stack.indexOfLast { it.first == openerName }
                    if (idx >= 0) {
                        val (_, open) = stack.removeAt(idx)
                        while (stack.size > idx) stack.removeLast()
                        val range = TextRange(open.textRange.startOffset, stmt.textRange.endOffset)
                        if (range.length > 0) out.add(FoldingDescriptor(open.node, range))
                    }
                } else if (keyword in CONDITION_OPENERS) {
                    // {{ if ... }} or {{ unless ... }}: push openers onto the stack
                    stack.addLast(keyword to stmt)
                }
            } else {
                // {{ tag }} or {{ tag:method }}: push paired catalog tags onto stack
                val namePath = stmt.namePath
                val head = (namePath as? AntlersNamePathMixin)?.head ?: continue
                val isPair = catalog?.tag(head)?.isPair == true
                if (isPair) stack.addLast(head to stmt)
            }
        }
        return out.toTypedArray()
    }

    /**
     * When T_COMMENT_OPEN/T_COMMENT_TEXT/T_COMMENT_CLOSE are in getCommentTokens(), the parser
     * wraps them as PsiComment leaf nodes rather than building an AntlersComment composite.
     * This method scans for T_COMMENT_OPEN leaves and pairs them with the subsequent T_COMMENT_CLOSE.
     */
    private fun addCommentFoldsFromTokens(root: PsiElement, out: MutableList<FoldingDescriptor>) {
        var element: PsiElement? = root.firstChild ?: return
        while (element != null) {
            if (element is PsiComment && element.node.elementType === AntlersTypes.T_COMMENT_OPEN) {
                val openNode = element.node
                val openStart = element.textRange.startOffset
                // Walk siblings to find T_COMMENT_CLOSE
                var sibling: PsiElement? = element.nextSibling
                var closeEnd = -1
                while (sibling != null) {
                    if (sibling is PsiComment && sibling.node.elementType === AntlersTypes.T_COMMENT_CLOSE) {
                        closeEnd = sibling.textRange.endOffset
                        break
                    }
                    if (sibling !is PsiComment && sibling !is PsiWhiteSpace) break
                    sibling = sibling.nextSibling
                }
                if (closeEnd > openStart + 6) {
                    out.add(FoldingDescriptor(openNode, TextRange(openStart, closeEnd)))
                }
            }
            // Recurse into non-leaf children (e.g. if the comment is nested inside another composite)
            if (element.firstChild != null && element !is PsiComment) {
                addCommentFoldsFromTokens(element, out)
            }
            element = element.nextSibling
        }
    }

    private fun addNode(out: MutableList<FoldingDescriptor>, element: PsiElement) {
        if (element.textLength > 6) out.add(FoldingDescriptor(element.node, element.textRange))
    }

    override fun getPlaceholderText(node: ASTNode): String {
        return when (node.elementType) {
            AntlersTypes.T_COMMENT_OPEN -> "{{# … #}}"
            else -> when (node.psi) {
                is AntlersNoparseBlock -> "{{ noparse … }}"
                is AntlersPhpBlock -> "{{ php … }}"
                else -> "…"
            }
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
