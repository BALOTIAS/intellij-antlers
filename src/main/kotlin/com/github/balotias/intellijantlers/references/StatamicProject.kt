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

    /**
     * Resolves a partial [path] to its file. Precedence: the `views/partials/` subfolder before the
     * views root, and the exact name before the underscored-partial convention (`{{ partial:btn }}`
     * resolves `_btn.antlers.html` — Statamic's recommended partial naming).
     */
    fun resolvePartial(element: PsiElement, rawPath: String): VirtualFile? {
        val root = viewsRoot(element) ?: return null
        val path = rawPath.replace('.', '/')   // Laravel/Statamic dot notation: layouts.default.footer
        val exts = listOf("antlers.html", "html")
        val names = listOf(path, underscoredPartial(path))   // exact name, then `_basename`
        for (loc in listOf("partials/", "")) {
            for (name in names) {
                for (ext in exts) root.findFileByRelativePath("$loc$name.$ext")?.let { return it }
            }
        }
        return null
    }

    /** Inserts a leading underscore before the path's last segment: `blog/card` → `blog/_card`. */
    private fun underscoredPartial(path: String): String {
        val slash = path.lastIndexOf('/')
        return if (slash < 0) "_$path" else path.substring(0, slash + 1) + "_" + path.substring(slash + 1)
    }

    /** All partial paths under `resources/views` (e.g. "blog/card"), extension-stripped; a partial in
     *  the `partials/` subfolder is offered by its short name (`btn`, not `partials/btn`), de-duplicated. */
    fun listPartials(element: PsiElement): List<String> {
        val root = viewsRoot(element) ?: return emptyList()
        val out = LinkedHashSet<String>()
        collectPartials(root, root, out)
        return out.toList()
    }

    /** Collection handles = subdirectory names of `resources/blueprints/collections`. */
    fun listCollectionHandles(element: PsiElement): List<String> = blueprintSubdirs(element, "collections")

    /** Taxonomy handles = subdirectory names of `resources/blueprints/taxonomies`. */
    fun listTaxonomyHandles(element: PsiElement): List<String> = blueprintSubdirs(element, "taxonomies")

    /** Form handles = `*.yaml` basenames under `resources/blueprints/forms`. */
    fun listFormHandles(element: PsiElement): List<String> = blueprintFiles(element, "forms")

    /** Nav handles = `*.yaml` basenames under `resources/blueprints/navigation`. */
    fun listNavHandles(element: PsiElement): List<String> = blueprintFiles(element, "navigation")

    private fun blueprintFiles(element: PsiElement, kind: String): List<String> {
        val resources = viewsRoot(element)?.parent ?: return emptyList()
        val dir = resources.findChild("blueprints")?.findChild(kind) ?: return emptyList()
        return dir.children.filter { !it.isDirectory && it.name.endsWith(".yaml") }
            .map { it.name.removeSuffix(".yaml") }
    }

    private fun blueprintSubdirs(element: PsiElement, kind: String): List<String> {
        val resources = viewsRoot(element)?.parent ?: return emptyList()
        val dir = resources.findChild("blueprints")?.findChild(kind) ?: return emptyList()
        return dir.children.filter { it.isDirectory }.map { it.name }
    }

    private fun collectPartials(root: VirtualFile, dir: VirtualFile, out: MutableSet<String>) {
        for (child in dir.children) {
            if (child.isDirectory) {
                collectPartials(root, child, out)
            } else if (child.name.endsWith(".antlers.html") || child.name.endsWith(".html")) {
                val rel = relativePath(root, child)?.removeSuffix(".antlers.html")?.removeSuffix(".html") ?: continue
                // `views/partials/btn` → `btn`; an underscored partial `_btn` → `btn` (referenced w/o `_`).
                out.add(stripLeadingUnderscore(rel.removePrefix("partials/")))
            }
        }
    }

    /** Strips a leading underscore from a partial name's last segment: `_btn` → `btn`, `blog/_card` → `blog/card`. */
    private fun stripLeadingUnderscore(name: String): String {
        val slash = name.lastIndexOf('/')
        val base = name.substring(slash + 1)
        return if (base.startsWith("_")) name.substring(0, slash + 1) + base.removePrefix("_") else name
    }

    private fun relativePath(root: VirtualFile, file: VirtualFile): String? {
        if (!file.path.startsWith(root.path)) return null
        return file.path.removePrefix("${root.path}/")
    }
}
