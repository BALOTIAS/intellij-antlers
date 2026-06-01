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
}
