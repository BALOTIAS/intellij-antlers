package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Reconstructs the enclosing iterating-tag scope at a caret. The Antlers grammar is flat — an opener
 * `{{ collection:blog }}` and its `{{ /collection }}` are sibling top-level statements — so we replay
 * the statements that end before the caret through a stack, mirroring AntlersFoldingBuilder.
 * Never throws; unbalanced templates yield an over-broad or empty scope.
 */
object AntlersScopeResolver {

    private val ITERATING = setOf("collection", "taxonomy", "users", "user", "form", "assets")
    private val CONDITION_OPENERS = setOf("if", "unless")
    private val CONDITION_CLOSERS = mapOf("endif" to "if", "endunless" to "unless")

    /** A pushed open construct awaiting its closer. */
    private data class Frame(
        val name: String,                 // matched against the closer (tag head or alias)
        val namespace: BlueprintNamespace?,
        val active: Boolean,              // namespace contributes to this frame's body
        val alias: String?                // if set, namespace activates only inside a child {{ alias }}
    )

    /** BlueprintScopes enclosing [element], innermost first. Empty = top level (global fallback). */
    fun scopesAt(element: PsiElement): List<BlueprintScope> {
        val file = element.containingFile ?: return emptyList()
        val caret = element.textRange.startOffset
        val project = element.project
        val catalog = if (project.isDefault) null else AntlersCatalogService.getInstance(project)

        val statements = PsiTreeUtil.findChildrenOfType(file, AntlersStatement::class.java)
            .filter { it.textRange.endOffset <= caret }
            .sortedBy { it.textRange.startOffset }

        val stack = ArrayDeque<Frame>()
        for (stmt in statements) {
            // Closing tag: pop the nearest matching frame (and any unmatched inner frames).
            val closing = stmt.closingTag
            if (closing != null) {
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':')
                if (name != null) popTo(stack, name)
                continue
            }
            // Condition: if/unless open a transparent frame; endif/endunless close it; else/elseif ignored.
            val condition = stmt.condition
            if (condition != null) {
                val kw = (condition as? AntlersConditionMixin)?.keyword ?: continue
                CONDITION_CLOSERS[kw]?.let { popTo(stack, it) }
                if (kw in CONDITION_OPENERS) stack.addLast(Frame(kw, null, false, null))
                continue
            }
            // Opener with a name path.
            val namePath = stmt.namePath as? AntlersNamePathMixin ?: continue
            val head = namePath.head
            if (head.isBlank()) continue

            // Alias activation: this tag's name matches an enclosing aliased frame's alias.
            val aliased = stack.lastOrNull { it.alias == head }
            if (aliased != null) {
                stack.addLast(Frame(head, aliased.namespace, true, null))
                continue
            }

            // Recognized iterating tag with a resolvable handle.
            if (head in ITERATING) {
                val ns = resolveNamespace(head, namePath, stmt)
                if (ns != null) {
                    val alias = paramValue(stmt, setOf("as"))
                    stack.addLast(
                        if (alias != null) Frame(head, ns, false, alias)
                        else Frame(head, ns, true, null)
                    )
                    continue
                }
            }

            // Any other catalog pair tag (cache, section, or an iterating tag with no handle):
            // push a transparent frame so its closer balances. Non-pair tags / plain variables
            // have no closer, so we must NOT push them.
            if (catalog?.tag(head)?.isPair == true) stack.addLast(Frame(head, null, false, null))
        }

        return stack.filter { it.active && it.namespace != null }
            .reversed()
            .map { BlueprintScope(it.namespace!!) }
    }

    private fun popTo(stack: ArrayDeque<Frame>, name: String) {
        val idx = stack.indexOfLast { it.name == name }
        if (idx >= 0) while (stack.size > idx) stack.removeLast()
    }

    private fun resolveNamespace(head: String, namePath: AntlersNamePathMixin, stmt: AntlersStatement): BlueprintNamespace? {
        val kind = when (head) {
            "collection" -> BlueprintNamespace.Kind.COLLECTION
            "taxonomy" -> BlueprintNamespace.Kind.TAXONOMY
            "form" -> BlueprintNamespace.Kind.FORM
            "assets" -> BlueprintNamespace.Kind.ASSET
            "users", "user" -> return BlueprintNamespace(BlueprintNamespace.Kind.USER, "user")
            else -> return null
        }
        val handle = namePath.method ?: paramValue(stmt, setOf("from", "in")) ?: return null
        return BlueprintNamespace(kind, handle)
    }

    /** The unquoted value of the first parameter on [stmt] whose name is in [names], or null. */
    private fun paramValue(stmt: AntlersStatement, names: Set<String>): String? {
        for (param in PsiTreeUtil.getChildrenOfTypeAsList(stmt, AntlersParameterMixin::class.java)) {
            if (param.parameterName in names) {
                val raw = param.valueElement?.text ?: return null
                return stripQuotes(raw).takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun stripQuotes(s: String): String {
        if (s.length >= 2 && (s.first() == '"' || s.first() == '\'') && s.last() == s.first()) {
            return s.substring(1, s.length - 1)
        }
        return s
    }
}
