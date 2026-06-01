package com.github.balotias.intellijantlers.documentation

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.catalog.ParamDef
import com.github.balotias.intellijantlers.catalog.TagDef
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
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

        // Modifier: the identifier is the modifier name.
        PsiTreeUtil.getParentOfType(ident, AntlersModifierMixin::class.java)?.let { mod ->
            if (mod.modifierName == name) {
                val def = catalog.modifiers().firstOrNull { it.name == name } ?: return null
                return section(
                    "Antlers modifier <b>${esc(name)}</b>",
                    def.description,
                    def.docUrl
                )
            }
        }

        // Parameter: the identifier is the parameter name of a known tag.
        PsiTreeUtil.getParentOfType(ident, AntlersParameterMixin::class.java)?.let { param ->
            if (param.parameterName == name) {
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
                val tag = catalog.tag(name) ?: return null
                return tagDoc(tag)
            }
            if (path.method == name) {
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

    private fun enclosingTag(ident: PsiElement, catalog: AntlersCatalogService): TagDef? {
        val statement = PsiTreeUtil.getParentOfType(ident, AntlersStatement::class.java) ?: return null
        val head = PsiTreeUtil.findChildOfType(statement, AntlersNamePathMixin::class.java)?.head ?: return null
        return catalog.tag(head)
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
            sb.append("<a href=\"${esc(docUrl)}\">${esc(docUrl)}</a>")
            sb.append(DocumentationMarkup.SECTIONS_END)
        }
    }

    private fun esc(s: String): String = StringUtil.escapeXmlEntities(s)
}
