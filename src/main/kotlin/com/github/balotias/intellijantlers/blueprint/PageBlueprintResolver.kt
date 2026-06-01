package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.references.StatamicProject
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/** Maps the current template file to the COLLECTION namespaces whose `template:` points at it. */
object PageBlueprintResolver {

    /** COLLECTION namespaces for collections whose template renders [element]'s file. Empty if none. */
    fun namespacesFor(element: PsiElement): List<BlueprintNamespace> {
        val viewsRoot = StatamicProject.viewsRoot(element) ?: return emptyList()
        val vf = element.containingFile?.originalFile?.virtualFile ?: return emptyList()
        val viewPath = viewPathOf(vf, viewsRoot) ?: return emptyList()
        val target = normalize(viewPath)
        return CollectionConfigService.getInstance(element.project).configs()
            .filter { normalize(it.template) == target }
            .map { BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, it.handle) }
            .distinct()
    }

    /** "blog/show" from ".../resources/views/blog/show.antlers.html", or null if not under [viewsRoot]. */
    private fun viewPathOf(file: VirtualFile, viewsRoot: VirtualFile): String? {
        val rel = VfsUtilCore.getRelativePath(file, viewsRoot, '/') ?: return null
        return rel.removeSuffix(".antlers.html")
    }

    /** Statamic accepts both `blog/show` and `blog.show`; normalize separators to `/`. */
    private fun normalize(s: String): String = s.replace('.', '/')
}
