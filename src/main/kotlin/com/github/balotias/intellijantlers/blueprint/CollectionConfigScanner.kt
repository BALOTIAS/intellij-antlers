package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

// Extracts template: from each content/collections/*.yaml config. Tolerant; never throws.
object CollectionConfigScanner {

    private const val MARKER = "/content/collections/"
    private val TEMPLATE_RE = Regex("""^\s*template:\s*['"]?([^'"\n]+?)['"]?\s*$""", RegexOption.MULTILINE)

    fun scan(project: Project): List<CollectionConfig> {
        val out = mutableListOf<CollectionConfig>()
        try {
            val yamls = FilenameIndex.getAllFilesByExt(project, "yaml", GlobalSearchScope.projectScope(project))
            for (file in yamls) {
                val p = file.path.replace('\\', '/')
                val i = p.indexOf(MARKER)
                if (i < 0) continue
                val rest = p.substring(i + MARKER.length)
                if (rest.contains('/')) continue
                if (!rest.endsWith(".yaml")) continue
                try {
                    val handle = rest.removeSuffix(".yaml")
                    val template = extractTemplate(file)
                    if (handle.isNotBlank() && template.isNotBlank()) out.add(CollectionConfig(handle, template))
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: Exception) {
        }
        return out
    }

    private fun extractTemplate(file: VirtualFile): String {
        val text = VfsUtilCore.loadText(file)
        for (line in text.split("\n")) {
            TEMPLATE_RE.find(line)?.let { return it.groupValues[1].trim() }
        }
        return ""
    }
}
