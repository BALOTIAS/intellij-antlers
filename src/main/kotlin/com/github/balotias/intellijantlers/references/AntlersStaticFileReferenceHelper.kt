package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil

/**
 * Turns a literal media path written in an Antlers expression into a reference to its file under the web
 * root — covering glide `src="/img/hero.jpg"`, asset `url="photos/x.webp"`, and bare path strings
 * `{{ "/img/logo.png" }}`. Only whole strings that look like a media file path qualify. The `svg` and
 * `partial` tags resolve their own way (resources/svg, views), so those heads are left to their helpers.
 */
object AntlersStaticFileReferenceHelper {

    private val MEDIA_PATH =
        Regex("""^/?[\w\-./]+\.(jpe?g|png|gif|webp|avif|svg|ico|bmp|mp4|webm)$""", RegexOption.IGNORE_CASE)

    fun refsForString(element: PsiElement): Array<PsiReference> {
        val raw = element.text
        val inner = raw.removeSurrounding("\"").removeSurrounding("'")
        if (!MEDIA_PATH.matches(inner)) return emptyArray()
        if (headOf(element) in setOf("svg", "partial")) return emptyArray()
        val start = if (raw.length >= 2) 1 else 0
        return arrayOf(AntlersStaticFileReference(element, TextRange(start, start + inner.length), inner))
    }

    private fun headOf(element: PsiElement): String? =
        PsiTreeUtil.getParentOfType(element, AntlersStatement::class.java)
            ?.let { PsiTreeUtil.getChildOfType(it, AntlersNamePathMixin::class.java) }?.head
}
