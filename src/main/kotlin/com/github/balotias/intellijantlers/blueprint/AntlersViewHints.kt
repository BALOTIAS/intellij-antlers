package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.psi.AntlersFrontMatter
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersHintParser
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * The blueprint namespaces a view declares via a leading `{{# @collection|@entry|@blueprint <handle> #}}`
 * hint comment (Antlers Toolbox). Cached per-file. `@blueprint` is read as a collection handle in v1.
 *
 * Note: because T_COMMENT_OPEN/TEXT/CLOSE are all in `getCommentTokens()`, IntelliJ represents them
 * as flat `PsiComment` leaf nodes in the file's child list rather than wrapping them in a composite
 * AntlersComment node. We therefore scan the leading children directly for T_COMMENT_TEXT.
 */
object AntlersViewHints {

    private val SCOPING = setOf("@collection", "@entry", "@blueprint")

    fun declaredNamespaces(file: PsiFile): List<BlueprintNamespace> =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(compute(file), PsiModificationTracker.MODIFICATION_COUNT)
        }

    private fun compute(file: PsiFile): List<BlueprintNamespace> {
        // Walk the file's leading children to find the first T_COMMENT_TEXT before any real content.
        val body = leadingCommentBody(file) ?: return emptyList()
        return AntlersHintParser.parse(body)
            .filter { it.name in SCOPING && it.value.isNotBlank() }
            .map { BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, it.value.substringBefore(' ').trim()) }
            .distinct()
    }

    /**
     * Returns the text of the first T_COMMENT_TEXT leaf that appears before any non-comment,
     * non-whitespace content, or null if there is none.
     */
    private fun leadingCommentBody(file: PsiFile): String? {
        for (child in file.children) {
            // Skip whitespace between nodes, and leading YAML front matter (it precedes the hint comment).
            if (child is PsiWhiteSpace || child is AntlersFrontMatter) continue
            // Comment tokens (T_COMMENT_OPEN, T_COMMENT_TEXT, T_COMMENT_CLOSE) are leaves
            if (child is PsiComment) {
                if (child.node.elementType == AntlersTypes.T_COMMENT_TEXT) return child.text
                // T_COMMENT_OPEN or T_COMMENT_CLOSE: keep scanning
                continue
            }
            // Any other (real content) node: stop looking
            break
        }
        return null
    }
}
