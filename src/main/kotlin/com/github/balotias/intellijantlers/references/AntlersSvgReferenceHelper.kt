package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil

/**
 * References for the Statamic `svg` tag so its name resolves to the SVG file (go-to-declaration):
 *   - `{{ svg:trash }}`        — colon shorthand; the `trash` name-path segment.
 *   - `{{ svg src="trash" }}`  — the `src` parameter's string value (also a nested `icons/trash`).
 * Resolution is [StatamicProject.resolveSvg]. Inline SVG markup (`src="<svg …>"`) is left alone.
 */
object AntlersSvgReferenceHelper {

    fun refsForString(element: PsiElement): Array<PsiReference> {
        val statement = PsiTreeUtil.getParentOfType(element, AntlersStatement::class.java) ?: return emptyArray()
        if (svgHead(statement) == null) return emptyArray()
        val param = PsiTreeUtil.getParentOfType(element, AntlersParameterMixin::class.java) ?: return emptyArray()
        if (param.parameterName != "src" || param.valueElement != element) return emptyArray()
        val raw = element.text
        val inner = raw.removeSurrounding("\"").removeSurrounding("'")
        if (inner.isBlank() || inner.startsWith("<")) return emptyArray()   // inline SVG markup, not a file
        val start = if (raw.length >= 2) 1 else 0
        return arrayOf(AntlersSvgReference(element, TextRange(start, start + inner.length), inner))
    }

    fun refsForIdent(element: PsiElement): Array<PsiReference> {
        val statement = PsiTreeUtil.getParentOfType(element, AntlersStatement::class.java) ?: return emptyArray()
        val namePath = svgHead(statement) ?: return emptyArray()
        // Only a path segment after the `svg` head (the `trash` in `svg:trash`), not the head itself.
        val npIdents = namePath.node.getChildren(null)
            .filter { it.elementType == AntlersTypes.T_IDENT }.map { it.psi }
        if (npIdents.indexOf(element) < 1) return emptyArray()
        val name = svgName(statement) ?: return emptyArray()
        return arrayOf(AntlersSvgReference(element, TextRange(0, element.textLength), name))
    }

    private fun svgHead(statement: AntlersStatement): AntlersNamePathMixin? =
        PsiTreeUtil.getChildOfType(statement, AntlersNamePathMixin::class.java)?.takeIf { it.head == "svg" }

    /** The svg name as written: the `src` value, else the text after `svg:` up to whitespace / `}` / `|`. */
    private fun svgName(statement: AntlersStatement): String? {
        srcValue(statement)?.let { return it }
        val text = statement.text
        val i = text.indexOf("svg:")
        if (i < 0) return null
        return text.substring(i + 4).takeWhile { !it.isWhitespace() && it != '}' && it != '|' }.ifBlank { null }
    }

    private fun srcValue(statement: AntlersStatement): String? {
        val p = PsiTreeUtil.getChildrenOfTypeAsList(statement, AntlersParameterMixin::class.java)
            .firstOrNull { it.parameterName == "src" } ?: return null
        val v = p.valueElement?.text?.removeSurrounding("\"")?.removeSurrounding("'")?.ifBlank { null } ?: return null
        return if (v.startsWith("<")) null else v   // inline SVG markup, not a file name
    }
}
