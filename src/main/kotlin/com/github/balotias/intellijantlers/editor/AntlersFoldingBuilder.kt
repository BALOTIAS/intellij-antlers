package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.psi.AntlersNoparseBlock
import com.github.balotias.intellijantlers.psi.AntlersPhpBlock
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.github.balotias.intellijantlers.scope.NestingNode
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

        // Paired tag / condition folds via the shared nesting builder.
        addPairFolds(AntlersNestingTreeBuilder.build(root, root.project).roots, out)

        return out.toTypedArray()
    }

    private fun addPairFolds(nodes: List<NestingNode>, out: MutableList<FoldingDescriptor>) {
        for (n in nodes) {
            val closer = n.closer
            if (closer != null) {
                val range = TextRange(n.opener.textRange.startOffset, closer.textRange.endOffset)
                if (range.length > 0) out.add(FoldingDescriptor(n.opener.node, range))
            }
            addPairFolds(n.children, out)
        }
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
