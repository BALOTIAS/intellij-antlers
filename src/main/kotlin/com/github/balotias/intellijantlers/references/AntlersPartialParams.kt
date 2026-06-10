package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersFrontMatter
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersHintParser
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

/**
 * Reads a partial's declared parameters from its leading `{{# @param … #}}` directive comment.
 * Mirrors [com.github.balotias.intellijantlers.blueprint.AntlersViewHints]: the directive block is the
 * first comment before any real content. `fromDirectiveValue` is pure (unit-testable, IntelliJ-free).
 */
object AntlersPartialParams {

    /** One `@param[*] <name> <description>` (or `@deprecated <name> <message>`) declaration. */
    data class PartialParam(
        val name: String,
        val required: Boolean,
        val description: String,
        val deprecated: Boolean = false,
    )

    /** Decompose a `@param` directive value into a [PartialParam]; null when there is no name token. */
    fun fromDirectiveValue(value: String): PartialParam? {
        var v = value.trim()
        var required = false
        if (v.startsWith("*")) { required = true; v = v.removePrefix("*").trim() }
        if (v.isEmpty()) return null
        val parts = v.split(Regex("\\s+"), limit = 2)
        val name = parts[0]
        if (name.isEmpty()) return null
        return PartialParam(name, required, parts.getOrElse(1) { "" }.trim())
    }

    /** The `@param` declarations in [partialFile]'s leading directive comment, in declaration order. */
    fun of(partialFile: PsiFile): List<PartialParam> {
        // A resolved partial may be handed to us as the template-data (HTML) PSI; reach for the Antlers
        // tree that actually holds the `{{# … #}}` comments (falling back to the file as given).
        val antlers = partialFile.viewProvider.getPsi(AntlersLanguage.INSTANCE) ?: partialFile
        val body = leadingCommentBody(antlers) ?: return emptyList()
        val directives = AntlersHintParser.parse(body)
        val params = directives.filter { it.name == "@param" }.mapNotNull { fromDirectiveValue(it.value) }
        val deprecated = directives.filter { it.name == "@deprecated" }
            .mapNotNull { fromDirectiveValue(it.value)?.copy(required = false, deprecated = true) }
        // `@param` wins for a name in both — the param itself isn't deprecated, only a usage of it is.
        return (params + deprecated).distinctBy { it.name }
    }

    private fun leadingCommentBody(file: PsiFile): String? {
        for (child in file.children) {
            if (child is PsiWhiteSpace || child is AntlersFrontMatter) continue
            if (child is PsiComment) {
                if (child.node.elementType == AntlersTypes.T_COMMENT_TEXT) return child.text
                continue
            }
            break
        }
        return null
    }
}
