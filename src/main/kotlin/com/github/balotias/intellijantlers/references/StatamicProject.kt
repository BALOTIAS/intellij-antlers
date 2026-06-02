package com.github.balotias.intellijantlers.references

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/** Locates the Statamic/Laravel `resources/views` root for partial resolution. */
object StatamicProject {

    /**
     * The `resources/views` directory relevant to [element]'s file.
     *
     * First tries walking up the ancestor chain to find a `views` dir whose parent is `resources`.
     * If that fails (e.g. the file is not inside `resources/views`), walks up looking for a
     * directory that contains `resources/views` as a child.
     */
    fun viewsRoot(element: PsiElement): VirtualFile? {
        val fileVf = element.containingFile?.originalFile?.virtualFile ?: return null

        // Primary: the file itself lives inside resources/views (resolving tests)
        var dir: VirtualFile? = fileVf.parent
        while (dir != null) {
            if (dir.name == "views" && dir.parent?.name == "resources") return dir
            dir = dir.parent
        }

        // Fallback: search from the file's directory upward, looking for any ancestor
        // that has a resources/views subdirectory (completion test, file outside views)
        dir = fileVf.parent
        while (dir != null) {
            val views = findViewsChild(dir)
            if (views != null) return views
            dir = dir.parent
        }
        return null
    }

    /**
     * Returns `resources/views` if [root] itself has a child `resources` that contains `views`,
     * or null otherwise (does NOT recurse deeper to avoid performance issues).
     */
    private fun findViewsChild(root: VirtualFile): VirtualFile? {
        val resources = root.findChild("resources") ?: return null
        return resources.findChild("views")
    }

    /** All partial paths under `resources/views` (e.g. "blog/card"), extension-stripped. */
    fun listPartials(element: PsiElement): List<String> {
        val root = viewsRoot(element) ?: return emptyList()
        val out = mutableListOf<String>()
        collectPartials(root, root, out)
        return out
    }

    /** Collection handles = subdirectory names of `resources/blueprints/collections`. */
    fun listCollectionHandles(element: PsiElement): List<String> = blueprintSubdirs(element, "collections")

    /** Taxonomy handles = subdirectory names of `resources/blueprints/taxonomies`. */
    fun listTaxonomyHandles(element: PsiElement): List<String> = blueprintSubdirs(element, "taxonomies")

    private fun blueprintSubdirs(element: PsiElement, kind: String): List<String> {
        val resources = viewsRoot(element)?.parent ?: return emptyList()
        val dir = resources.findChild("blueprints")?.findChild(kind) ?: return emptyList()
        return dir.children.filter { it.isDirectory }.map { it.name }
    }

    private fun collectPartials(root: VirtualFile, dir: VirtualFile, out: MutableList<String>) {
        for (child in dir.children) {
            if (child.isDirectory) {
                collectPartials(root, child, out)
            } else if (child.name.endsWith(".antlers.html") || child.name.endsWith(".html")) {
                val rel = relativePath(root, child) ?: continue
                out.add(rel.removeSuffix(".antlers.html").removeSuffix(".html"))
            }
        }
    }

    private fun relativePath(root: VirtualFile, file: VirtualFile): String? {
        if (!file.path.startsWith(root.path)) return null
        return file.path.removePrefix("${root.path}/")
    }
}
