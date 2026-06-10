package com.github.balotias.intellijantlers.documentation

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.catalog.FieldtypeProperties
import com.github.balotias.intellijantlers.catalog.ModifierDef
import com.github.balotias.intellijantlers.catalog.ModifierSignature
import com.github.balotias.intellijantlers.catalog.ParamDef
import com.github.balotias.intellijantlers.catalog.TagDef
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.references.AntlersPartialParams
import com.github.balotias.intellijantlers.references.AntlersPartialReferenceHelper
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.github.balotias.intellijantlers.scope.AntlersMemberResolver
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** Catalog-backed quick documentation for Antlers tags, methods, parameters, and modifiers. */
class AntlersDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val ident = originalElement ?: element
        if (ident.node?.elementType != AntlersTypes.T_IDENT) return null
        val name = ident.text
        val catalog = AntlersCatalogService.getInstance(ident.project)

        // `switch(…)` is the inline match-like *operator*, not the `{{ switch between=… }}` cycling tag.
        // Resolve it first so hover shows the operator's docs instead of the tag's (same name, `(` tail).
        if (isSwitchOperator(ident)) return switchOperatorDoc()

        // A `field:operator="value"` query condition — show the operator's docs, not a param/modifier.
        if (com.github.balotias.intellijantlers.completion.AntlersConditionOperators.isConditionOperatorIdent(ident))
            return conditionOperatorDoc(name)

        // Modifier: the identifier is the modifier name.
        PsiTreeUtil.getParentOfType(ident, AntlersModifierMixin::class.java)?.let { mod ->
            if (mod.modifierName == name) {
                val def = catalog.modifiers().firstOrNull { it.name == name } ?: return null
                return modifierDoc(def)
            }
        }

        // Parameter: the identifier is the parameter name of a known tag, or a partial include param.
        PsiTreeUtil.getParentOfType(ident, AntlersParameterMixin::class.java)?.let { param ->
            if (param.parameterName == name) {
                // Partial include param: doc comes from the included partial's `{{# @param … #}}`.
                partialParamDoc(ident, name)?.let { return it }
                val tag = enclosingTag(ident, catalog) ?: return null
                val p = tag.parameters.firstOrNull { it.name == name } ?: return null
                val type = if (p.type.isNotBlank()) " : ${esc(p.type)}" else ""
                val req = if (p.required) " (required)" else ""
                return section(
                    "Parameter <b>${esc(name)}</b>$type$req — tag <code>${esc(tag.name)}</code>",
                    p.description,
                    tag.docUrl
                )
            }
        }

        // Tag head or method.
        PsiTreeUtil.getParentOfType(ident, AntlersNamePathMixin::class.java)?.let { path ->
            if (path.head == name) {
                catalog.tag(name)?.let { return tagDoc(it) }
                val field = AntlersFieldContext.resolveField(ident, name, ident.project)
                val scoped = AntlersFieldContext.namespacesFor(ident) != null
                field?.let { f ->
                    val type = if (f.type.isNotBlank()) " (${esc(f.type)})" else ""
                    val ns = if (scoped) namespaceLabel(f.namespace) else ""
                    val title = "Field <b>${esc(name)}</b>$type" +
                        (if (f.display.isNotBlank()) " — ${esc(f.display)}" else "") + ns
                    return section(title, f.display, "")
                }
                com.github.balotias.intellijantlers.blueprint.SystemVariables.ALL.firstOrNull { it.name == name }?.let { sv ->
                    return section("Variable <b>${esc(name)}</b>", sv.description, "")
                }
                return null
            }
            val idents = path.node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }
            val index = idents.indexOfFirst { it.psi == ident }
            if (index == 1 && idents.firstOrNull()?.psi?.text == "view") {
                val entry = com.github.balotias.intellijantlers.view.ViewFrontMatterService
                    .getInstance(ident.project).topLevel(ident.containingFile)
                    .firstOrNull { it.name == name } ?: return null
                return section("View variable <b>${esc(name)}</b>", entry.valuePreview, "")
            }
            if (index > 0) {
                val prefix = idents.take(index).map { it.text }
                val parent = AntlersMemberResolver.resolveField(ident, prefix, ident.project)
                if (parent != null) {
                    val svc = BlueprintService.getInstance(ident.project)
                    for (childNs in AntlersMemberResolver.childNamespaces(parent)) {
                        svc.fieldsFor(childNs).firstOrNull { it.handle == name }?.let { f ->
                            val type = if (f.type.isNotBlank()) " (${esc(f.type)})" else ""
                            val title = "Field <b>${esc(name)}</b>$type" +
                                (if (f.display.isNotBlank()) " — ${esc(f.display)}" else "") + namespaceLabel(f.namespace)
                            return section(title, f.display, "")
                        }
                    }
                    FieldtypeProperties.forType(parent.type).firstOrNull { it.name == name }?.let { p ->
                        return section("Property <b>${esc(name)}</b> · ${esc(parent.type)}", p.description, "")
                    }
                }
                // Fall back to the catalog tag-method doc (e.g. collection:count).
                val tag = catalog.tag(path.head) ?: return null
                return section(
                    "Method <b>${esc(name)}</b> of tag <code>${esc(tag.name)}</code>",
                    tag.description,
                    tag.docUrl
                )
            }
        }
        return null
    }

    override fun getCustomDocumentationElement(
        editor: com.intellij.openapi.editor.Editor,
        file: com.intellij.psi.PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        // Modifiers, catalog tags, and params have no PSI reference, so the platform can't resolve a
        // documentation target on hover/Ctrl-Q. Route the T_IDENT under the cursor to generateDoc.
        if (contextElement?.node?.elementType == AntlersTypes.T_IDENT) return contextElement
        val at = file.findElementAt(targetOffset)
        return if (at?.node?.elementType == AntlersTypes.T_IDENT) at else null
    }

    /** " · collection: blog › rows" style suffix; empty for the UNKNOWN namespace. */
    private fun namespaceLabel(ns: BlueprintNamespace): String {
        if (ns.kind == BlueprintNamespace.Kind.UNKNOWN) return ""
        val kind = ns.kind.name.lowercase()
        val pathSuffix = if (ns.path.isEmpty()) "" else " › " + ns.path.joinToString(" › ") { esc(it) }
        return " · ${esc(kind)}: ${esc(ns.handle)}$pathSuffix"
    }

    /** Quick-doc for a `{{ partial:… }}` include parameter, from the partial's `@param` hints; null if N/A. */
    private fun partialParamDoc(ident: PsiElement, name: String): String? {
        val statement = PsiTreeUtil.getParentOfType(ident, AntlersStatement::class.java) ?: return null
        if (PsiTreeUtil.getChildOfType(statement, AntlersNamePathMixin::class.java)?.head != "partial") return null
        val partial = AntlersPartialReferenceHelper.includedPartialFile(statement) ?: return null
        val pp = AntlersPartialParams.of(partial).firstOrNull { it.name == name } ?: return null
        val req = if (pp.deprecated) " (deprecated)" else if (pp.required) " (required)" else " (optional)"
        val desc = if (pp.deprecated) "Deprecated. ${pp.description}".trim() else pp.description
        return section(
            "Parameter <b>${esc(name)}</b>$req — partial <code>${esc(partial.name)}</code>",
            desc,
            ""
        )
    }

    private fun enclosingTag(ident: PsiElement, catalog: AntlersCatalogService): TagDef? {
        val statement = PsiTreeUtil.getParentOfType(ident, AntlersStatement::class.java) ?: return null
        val head = PsiTreeUtil.getChildOfType(statement, AntlersNamePathMixin::class.java)?.head ?: return null
        return catalog.tag(head)
    }

    private fun modifierDoc(def: ModifierDef): String {
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("Antlers modifier <b>${esc(ModifierSignature.render(def))}</b>")
        sb.append(DocumentationMarkup.DEFINITION_END)
        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append(esc(def.description))
        if (def.parameters.isNotEmpty()) {
            sb.append("<br/><br/><b>Parameters</b><br/>")
            for (p in def.parameters) {
                val opt = when {
                    !p.optional -> ""
                    p.default.isNotBlank() -> " <i>(optional, default: ${esc(p.default)})</i>"
                    else -> " <i>(optional)</i>"
                }
                sb.append("<code>${esc(p.name)}</code> — ${esc(p.description)}$opt<br/>")
            }
        }
        sb.append(DocumentationMarkup.CONTENT_END)
        appendDocUrl(sb, def.docUrl)
        return sb.toString()
    }

    /**
     * True when [ident] is the head of the inline `switch(…)` operator. The `(` may be the ident's own
     * next sibling (the bare `{ switch(…) }` inline form) or the next sibling of its name-path wrapper
     * (`{{ switch(…) }}`), so walk leaves rather than siblings to cover both.
     */
    private fun isSwitchOperator(ident: PsiElement): Boolean {
        if (ident.text != "switch") return false
        var leaf = PsiTreeUtil.nextLeaf(ident)
        while (leaf != null && leaf.text.isBlank()) leaf = PsiTreeUtil.nextLeaf(leaf)
        return leaf?.node?.elementType == AntlersTypes.T_LPAREN
    }

    private fun switchOperatorDoc(): String {
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("Antlers operator <b>switch</b>")
        sb.append(DocumentationMarkup.DEFINITION_END)
        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append(esc(
            "Inline conditional that returns the value of the first matching case. Each case is an " +
                "(expression) => value pair; an empty () => value is the default. Handy inside " +
                "interpolations and tag parameters, where tag pairs aren't allowed."
        ))
        sb.append(DocumentationMarkup.CONTENT_END)
        appendDocUrl(sb, "https://statamic.dev/frontend/antlers#switch")
        return sb.toString()
    }

    private fun conditionOperatorDoc(operator: String): String {
        val desc = com.github.balotias.intellijantlers.completion.AntlersConditionOperators.describe(operator)
            ?: "Filters the tag's results."
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("Tag condition operator <b>${esc(operator)}</b>")
        sb.append(DocumentationMarkup.DEFINITION_END)
        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append(esc("$desc A query condition, written as a tag parameter: field:$operator=\"value\"."))
        sb.append(DocumentationMarkup.CONTENT_END)
        appendDocUrl(sb, "https://statamic.dev/tags/conditions")
        return sb.toString()
    }

    private fun tagDoc(tag: TagDef): String {
        val kind = if (tag.isPair) "block tag" else "tag"
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("Antlers $kind <b>${esc(tag.name)}</b>")
        sb.append(DocumentationMarkup.DEFINITION_END)
        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append(esc(tag.description))
        if (tag.parameters.isNotEmpty()) {
            sb.append("<br/><br/><b>Parameters</b><br/>")
            for (p: ParamDef in tag.parameters) {
                sb.append("<code>${esc(p.name)}</code> — ${esc(p.description)}<br/>")
            }
        }
        sb.append(DocumentationMarkup.CONTENT_END)
        appendDocUrl(sb, tag.docUrl)
        return sb.toString()
    }

    private fun section(title: String, description: String, docUrl: String): String {
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START).append(title).append(DocumentationMarkup.DEFINITION_END)
        if (description.isNotBlank()) {
            sb.append(DocumentationMarkup.CONTENT_START).append(esc(description)).append(DocumentationMarkup.CONTENT_END)
        }
        appendDocUrl(sb, docUrl)
        return sb.toString()
    }

    private fun appendDocUrl(sb: StringBuilder, docUrl: String) {
        if (docUrl.isNotBlank()) {
            sb.append(DocumentationMarkup.SECTIONS_START)
            sb.append(DocumentationMarkup.SECTION_HEADER_START)
            sb.append("Docs")
            sb.append(DocumentationMarkup.SECTION_SEPARATOR)
            sb.append("<a href=\"").append(esc(docUrl)).append("\">").append(esc(docUrl)).append("</a>")
            sb.append(DocumentationMarkup.SECTION_END)
            sb.append(DocumentationMarkup.SECTIONS_END)
        }
    }

    private fun esc(s: String): String = StringUtil.escapeXmlEntities(s)
}
