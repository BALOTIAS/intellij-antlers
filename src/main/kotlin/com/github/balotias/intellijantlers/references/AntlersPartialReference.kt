package com.github.balotias.intellijantlers.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafPsiElement

/**
 * Resolves a partial path (e.g. "blog/card") to its template file under `resources/views`.
 *
 * [isPathTail] is true only for the reference covering the LAST path segment (the `src="…"` string, or
 * `card` in `partial:blog/card`). Only the tail participates in rename / find-usages — the directory-part
 * references (`blog`) navigate but don't rewrite, so renaming never double-writes (even when the dir name
 * equals the basename, e.g. `partial:card/card`).
 */
class AntlersPartialReference(
    element: PsiElement,
    range: TextRange,
    private val path: String,
    val isPathTail: Boolean = true
) : PsiReferenceBase<PsiElement>(element, range) {

    override fun resolve(): PsiElement? {
        val vf = StatamicProject.resolvePartial(element, path) ?: return null
        return PsiManager.getInstance(element.project).findFile(vf)
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        if (!isPathTail) return element   // dir-part references navigate but don't rewrite
        val newSegment = stripPartialExtensions(newElementName)
        val rangeText = rangeInElement.substring(element.text)
        // String form: rangeText holds the full path (e.g. "blog/card") — splice in the new last segment.
        // Colon tail form: rangeText holds just the last segment — replace it.
        val newRangeText =
            if (rangeText.contains('/')) rangeText.substringBeforeLast('/') + "/" + newSegment
            else newSegment
        return rewrite(newRangeText)
    }

    override fun bindToElement(targetElement: PsiElement): PsiElement {
        if (!isPathTail) return element   // dir-part references aren't rewritten on move
        val file = targetElement as? PsiFile ?: return element
        val root = StatamicProject.viewsRoot(element) ?: return element
        val newFullPath = relativePathMinusExt(root, file.virtualFile ?: return element) ?: return element
        val rangeText = rangeInElement.substring(element.text)
        // String form: range holds the whole path → write the full new path (handles dir moves).
        // Colon form: range holds only the last segment → best-effort last-segment swap.
        val newRangeText = if (rangeText == path) newFullPath else newFullPath.substringAfterLast('/')
        return rewrite(newRangeText)
    }

    private fun rewrite(newRangeText: String): PsiElement {
        val leaf = element as? LeafPsiElement ?: return element
        val old = leaf.text
        val newText = old.substring(0, rangeInElement.startOffset) + newRangeText +
            old.substring(rangeInElement.endOffset)
        return leaf.replaceWithText(newText).psi
    }

    private fun stripPartialExtensions(name: String): String =
        name.removeSuffix(".antlers.html").removeSuffix(".antlers.php").removeSuffix(".html")

    private fun relativePathMinusExt(
        root: com.intellij.openapi.vfs.VirtualFile,
        vf: com.intellij.openapi.vfs.VirtualFile
    ): String? {
        val rel = getRelativePath(root, vf) ?: return null
        return rel.removeSuffix(".antlers.html").removeSuffix(".antlers.php").removeSuffix(".html")
    }

    override fun getVariants(): Array<Any> =
        StatamicProject.listPartials(element).toTypedArray()

    private fun getRelativePath(root: com.intellij.openapi.vfs.VirtualFile, file: com.intellij.openapi.vfs.VirtualFile): String? {
        val rootPath = root.path
        val filePath = file.path
        if (!filePath.startsWith(rootPath)) return null
        return filePath.removePrefix("$rootPath/")
    }
}
