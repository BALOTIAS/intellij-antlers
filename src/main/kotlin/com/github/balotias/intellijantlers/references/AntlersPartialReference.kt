package com.github.balotias.intellijantlers.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafPsiElement

private val PARTIAL_EXTENSIONS = listOf("antlers.html", "html")

/** Resolves a partial path (e.g. "blog/card") to its template file under `resources/views`. */
class AntlersPartialReference(
    element: PsiElement,
    range: TextRange,
    private val path: String
) : PsiReferenceBase<PsiElement>(element, range) {

    override fun resolve(): PsiElement? {
        val root = StatamicProject.viewsRoot(element) ?: return null
        for (ext in PARTIAL_EXTENSIONS) {
            val vf = root.findFileByRelativePath("$path.$ext") ?: continue
            return PsiManager.getInstance(element.project).findFile(vf)
        }
        return null
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val newSegment = stripPartialExtensions(newElementName)
        val rangeText = rangeInElement.substring(element.text)
        // String form: rangeText holds the full path (e.g. "blog/card") — splice in the new last segment.
        // Colon form: rangeText holds one path segment. Only rename if it IS the last segment (skip dir parts).
        val lastSegment = path.substringAfterLast('/')
        val newRangeText = when {
            rangeText.contains('/') -> rangeText.substringBeforeLast('/') + "/" + newSegment
            rangeText == lastSegment -> newSegment
            else -> return element  // not the last segment (e.g. "blog" in partial:blog/card) — skip
        }
        return rewrite(newRangeText)
    }

    override fun bindToElement(targetElement: PsiElement): PsiElement {
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
        name.removeSuffix(".antlers.html").removeSuffix(".html")

    private fun relativePathMinusExt(
        root: com.intellij.openapi.vfs.VirtualFile,
        vf: com.intellij.openapi.vfs.VirtualFile
    ): String? {
        val rel = getRelativePath(root, vf) ?: return null
        return rel.removeSuffix(".antlers.html").removeSuffix(".html")
    }

    override fun getVariants(): Array<Any> {
        val root = StatamicProject.viewsRoot(element) ?: return emptyArray()
        val result = mutableListOf<String>()
        collectPartials(root, root, result)
        return result.toTypedArray()
    }

    private fun collectPartials(root: com.intellij.openapi.vfs.VirtualFile, dir: com.intellij.openapi.vfs.VirtualFile, result: MutableList<String>) {
        for (child in dir.children) {
            if (child.isDirectory) {
                collectPartials(root, child, result)
            } else if (child.name.endsWith(".antlers.html") || child.name.endsWith(".html")) {
                val relPath = getRelativePath(root, child) ?: return
                val stripped = relPath.removeSuffix(".antlers.html").removeSuffix(".html")
                result.add(stripped)
            }
        }
    }

    private fun getRelativePath(root: com.intellij.openapi.vfs.VirtualFile, file: com.intellij.openapi.vfs.VirtualFile): String? {
        val rootPath = root.path
        val filePath = file.path
        if (!filePath.startsWith(rootPath)) return null
        return filePath.removePrefix("$rootPath/")
    }
}
