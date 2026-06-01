package com.github.balotias.intellijantlers.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

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
