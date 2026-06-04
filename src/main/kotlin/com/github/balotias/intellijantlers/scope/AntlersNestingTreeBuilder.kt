package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/** One paired tag/condition: its opener, matched closer (null = unclosed), and nested children. */
data class NestingNode(
    val opener: AntlersStatement,
    val name: String,
    val closer: AntlersStatement?,
    val children: List<NestingNode>
)

/** Reconstructed tag/condition nesting for a file. */
data class NestingTree(
    val roots: List<NestingNode>,
    val unmatchedClosers: List<AntlersStatement>
)

/**
 * The single source of truth for paired-tag/condition nesting, shared by folding, balance diagnostics,
 * and the structure view. Reproduces the original folding/balance stack walk: only catalog-isPair tags
 * and if/unless conditions open; a closer pops the nearest matching opener, dropping inner unmatched
 * openers (hoisting their already-closed children to the parent); openers left at EOF are unclosed;
 * closers with no opener are unmatched. Tolerant; never throws.
 */
object AntlersNestingTreeBuilder {

    private val CONDITION_OPENERS = setOf("if", "unless")
    private val CONDITION_CLOSERS = mapOf("endif" to "if", "endunless" to "unless")

    private class Frame(val name: String, val opener: AntlersStatement, val children: MutableList<NestingNode> = mutableListOf())

    /** Cached sorted statements when [root] is a file (the usual case); a direct traversal otherwise. */
    private fun cachedStatements(root: PsiElement): List<AntlersStatement> =
        (root as? PsiFile)?.let { AntlersStatements.sortedIn(it) }
            ?: PsiTreeUtil.findChildrenOfType(root, AntlersStatement::class.java).sortedBy { it.textRange.startOffset }

    fun build(root: PsiElement, project: Project): NestingTree {
        val catalog = if (project.isDefault) null else AntlersCatalogService.getInstance(project)
        val roots = mutableListOf<NestingNode>()
        val unmatched = mutableListOf<AntlersStatement>()
        val stack = ArrayDeque<Frame>()

        val statements = cachedStatements(root)
        for (stmt in statements) {
            val closing = stmt.closingTag
            if (closing != null) {
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':') ?: continue
                val idx = stack.indexOfLast { it.name == name }
                if (idx >= 0) closeMatched(stack, idx, stmt, roots) else unmatched.add(stmt)
                continue
            }
            val condition = stmt.condition
            if (condition != null) {
                val kw = (condition as? AntlersConditionMixin)?.keyword ?: continue
                val openerName = CONDITION_CLOSERS[kw]
                if (openerName != null) {
                    val idx = stack.indexOfLast { it.name == openerName }
                    if (idx >= 0) closeMatched(stack, idx, stmt, roots) else unmatched.add(stmt)
                } else if (kw in CONDITION_OPENERS) {
                    stack.addLast(Frame(kw, stmt))
                }
                continue
            }
            val namePath = stmt.namePath as? AntlersNamePathMixin ?: continue
            val head = namePath.head
            if (head.isBlank()) continue
            if (catalog?.tag(head)?.isPair == true) stack.addLast(Frame(head, stmt))
        }
        // Openers still open at EOF are unclosed; attach innermost-first to their parent (or roots).
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            val node = NestingNode(f.opener, f.name, null, f.children)
            (stack.lastOrNull()?.children ?: roots).add(node)
        }
        return NestingTree(roots, unmatched)
    }

    /** Name of the innermost still-open pair tag / condition strictly before [beforeOffset], or null. */
    fun nearestUnclosedAt(root: PsiElement, beforeOffset: Int, project: Project): String? {
        val catalog = if (project.isDefault) null else AntlersCatalogService.getInstance(project)
        val stack = ArrayDeque<String>()
        val statements = cachedStatements(root).filter { it.textRange.startOffset < beforeOffset }
        for (stmt in statements) {
            val closing = stmt.closingTag
            if (closing != null) {
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':')
                if (name != null) { val i = stack.indexOfLast { it == name }; if (i >= 0) while (stack.size > i) stack.removeLast() }
                continue
            }
            val condition = stmt.condition
            if (condition != null) {
                val kw = (condition as? AntlersConditionMixin)?.keyword
                if (kw != null) {
                    val opener = CONDITION_CLOSERS[kw]
                    if (opener != null) { val i = stack.indexOfLast { it == opener }; if (i >= 0) while (stack.size > i) stack.removeLast() }
                    else if (kw in CONDITION_OPENERS) stack.addLast(kw)
                }
                continue
            }
            val head = (stmt.namePath as? AntlersNamePathMixin)?.head
            if (head != null && head.isNotBlank() && catalog?.tag(head)?.isPair == true) stack.addLast(head)
        }
        return stack.lastOrNull()
    }

    /** Close the frame at [idx] with [closer]; drop inner frames but hoist their closed children up. */
    private fun closeMatched(stack: ArrayDeque<Frame>, idx: Int, closer: AntlersStatement, roots: MutableList<NestingNode>) {
        // Inner unmatched openers are DROPPED (not flagged — matching the original walk), but their
        // already-closed children are hoisted to the parent so a properly-closed inner construct keeps
        // its fold.
        while (stack.size > idx + 1) {
            val dropped = stack.removeLast()
            stack.last().children.addAll(dropped.children)
        }
        val f = stack.removeLast()
        val node = NestingNode(f.opener, f.name, closer, f.children)
        (stack.lastOrNull()?.children ?: roots).add(node)
    }
}
