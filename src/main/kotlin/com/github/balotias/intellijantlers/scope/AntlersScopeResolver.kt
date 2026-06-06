package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.CONTAINER_FIELD_TYPES
import com.github.balotias.intellijantlers.blueprint.PageBlueprintResolver
import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import java.util.concurrent.ConcurrentHashMap

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
        val alias: String?,               // if set, namespace activates only inside a child {{ alias }}
        val navMeta: Boolean = false      // true for nav scopes (offer nav-tree variables)
    )

    /** The injection host in the outer file for an injected element, else the element itself. */
    fun hostOrSelf(element: PsiElement): PsiElement =
        InjectedLanguageManager.getInstance(element.project).getInjectionHost(element) ?: element

    /** BlueprintScopes enclosing [element], innermost first. Empty = top level (global fallback). */
    fun scopesAt(element: PsiElement): List<BlueprintScope> {
        val target = hostOrSelf(element)
        val file = target.containingFile ?: return emptyList()
        val caret = target.textRange.startOffset
        // Memoize per caret offset: completion resolves the scope at the same position several times
        // (directly + via AntlersFieldContext.fieldsInScope), and each call replayed every statement.
        val memo = CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(
                ConcurrentHashMap<Int, List<BlueprintScope>>(),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
        return memo.computeIfAbsent(caret) { computeScopesAt(file, caret, target.project) }
    }

    private fun computeScopesAt(file: PsiFile, caret: Int, project: com.intellij.openapi.project.Project): List<BlueprintScope> {
        val catalog = if (project.isDefault) null else AntlersCatalogService.getInstance(project)

        val statements = AntlersStatements.sortedIn(file).filter { it.textRange.endOffset <= caret }

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
                stack.addLast(Frame(head, aliased.namespace, true, null, aliased.navMeta))
                continue
            }

            // Recursive nav: {{ children }} re-enters the enclosing nav scope.
            if (head == "children") {
                val navFrame = stack.lastOrNull { it.navMeta && it.active && it.namespace != null }
                if (navFrame != null) {
                    stack.addLast(Frame("children", navFrame.namespace, true, null, navMeta = true))
                    continue
                }
            }

            // Nav tag: opens a scope over the nav/collection namespace, flagged for nav-meta variables.
            if (head == "nav") {
                val ns = navNamespace(namePath, stmt)
                val alias = paramValue(stmt, setOf("as"))
                stack.addLast(
                    if (alias != null) Frame("nav", ns, false, alias, navMeta = true)
                    else Frame("nav", ns, true, null, navMeta = true)
                )
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

            // Container-typed blueprint field (grid/group/replicator/bard) used as a pair tag
            // → open a sub-field scope. Resolve the field against the namespaces active so far
            // (or the E2 page mapping at top level), NEVER via AntlersFieldContext (which calls us).
            val containerNs = containerScope(head, stack, stmt, project)
            if (containerNs != null) {
                val alias = paramValue(stmt, setOf("as"))
                stack.addLast(
                    if (alias != null) Frame(head, containerNs, false, alias)
                    else Frame(head, containerNs, true, null)
                )
                continue
            }

            // Any other catalog pair tag (cache, section, or an iterating tag with no handle):
            // push a transparent frame so its closer balances. Non-pair tags / plain variables
            // have no closer, so we must NOT push them.
            if (catalog?.tag(head)?.isPair == true) stack.addLast(Frame(head, null, false, null))
        }

        return stack.filter { it.active && it.namespace != null }
            .reversed()
            .map { BlueprintScope(it.namespace!!, it.navMeta) }
    }

    private fun popTo(stack: ArrayDeque<Frame>, name: String) {
        val idx = stack.indexOfLast { it.name == name }
        if (idx >= 0) while (stack.size > idx) stack.removeLast()
    }

    /** The namespace a container-typed field [head] opens, or null if [head] isn't a container here. */
    private fun containerScope(
        head: String,
        stack: ArrayDeque<Frame>,
        stmt: AntlersStatement,
        project: com.intellij.openapi.project.Project
    ): BlueprintNamespace? {
        val current = currentNamespaces(stack, stmt)
        if (current.isEmpty()) return null
        val svc = BlueprintService.getInstance(project)
        val field = current.firstNotNullOfOrNull { ns -> svc.fieldsFor(ns).firstOrNull { it.handle == head } }
            ?: return null
        if (field.type.lowercase() !in CONTAINER_FIELD_TYPES) return null
        return field.namespace.copy(path = field.namespace.path + head)
    }

    /** Namespaces valid at this point in the walk: active frames (innermost first), else E2 page mapping. */
    private fun currentNamespaces(stack: ArrayDeque<Frame>, stmt: AntlersStatement): List<BlueprintNamespace> {
        val active = stack.filter { it.active && it.namespace != null }.reversed().map { it.namespace!! }
        if (active.isNotEmpty()) return active
        return PageBlueprintResolver.namespacesFor(stmt)
    }

    /** The namespace a `{{ nav … }}` tag iterates. Never null (defaults to the `pages` collection). */
    private fun navNamespace(namePath: AntlersNamePathMixin, stmt: AntlersStatement): BlueprintNamespace {
        val segs = namePath.segments  // segs[0] == "nav"
        if (segs.size >= 3 && segs[1] == "collection") {
            return BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, segs[2])
        }
        if (segs.size >= 2) {
            return BlueprintNamespace(BlueprintNamespace.Kind.NAVIGATION, segs[1])
        }
        val param = paramValue(stmt, setOf("handle", "from"))
        return if (param != null) BlueprintNamespace(BlueprintNamespace.Kind.NAVIGATION, param)
        else BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "pages")
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
