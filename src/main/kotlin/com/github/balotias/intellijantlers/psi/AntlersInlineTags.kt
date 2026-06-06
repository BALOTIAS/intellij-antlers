package com.github.balotias.intellijantlers.psi

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * An inline tag-call head: a bare `T_IDENT` opening `{tag …}` — preceded by `{`, not followed by `:` (so it
 * is not an array key like `{collection: 'x'}`), whose text is a known catalog tag.
 *
 * In `{{ x = {obfuscate_link …} }}` the parser leaves the tag name as a bare T_IDENT directly under the
 * statement (not wrapped in a NAME_PATH). Shared by the semantic highlighter (tag coloring) and the
 * go-to-declaration reference so coloring and navigation agree by construction.
 */
object AntlersInlineTags {
    fun isInlineTagHead(element: PsiElement): Boolean {
        if (element.node?.elementType != AntlersTypes.T_IDENT) return false
        if (PsiTreeUtil.skipWhitespacesBackward(element)?.node?.elementType != AntlersTypes.T_LBRACE) return false
        if (PsiTreeUtil.skipWhitespacesForward(element)?.node?.elementType == AntlersTypes.T_COLON) return false
        return AntlersCatalogService.getInstance(element.project).isTag(element.text)
    }
}
