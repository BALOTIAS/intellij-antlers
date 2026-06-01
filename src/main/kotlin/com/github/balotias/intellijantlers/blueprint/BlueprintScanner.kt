package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/** Hand-rolled extractor of Statamic blueprint/fieldset fields. Tolerant; never throws. */
object BlueprintScanner {

    private val HANDLE_RE = Regex("""^\s*(?:-\s*)?handle:\s*['"]?([A-Za-z_][A-Za-z0-9_]*)['"]?\s*$""")
    private val DISPLAY_RE = Regex("""^\s*display:\s*['"]?(.+?)['"]?\s*$""")
    private val TYPE_RE = Regex("""^\s*(?:type|field):\s*['"]?([A-Za-z_][A-Za-z0-9_]*)['"]?\s*$""")

    fun scan(project: Project): List<BlueprintField> {
        val out = mutableListOf<BlueprintField>()
        try {
            val yamls = FilenameIndex.getAllFilesByExt(project, "yaml", GlobalSearchScope.projectScope(project))
            for (file in yamls) {
                val p = file.path
                if (!p.contains("/resources/blueprints/") && !p.contains("/resources/fieldsets/")) continue
                try {
                    extract(file, out)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) { /* tolerant */ }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) { /* index not ready */ }
        return out
    }

    private fun extract(file: VirtualFile, out: MutableList<BlueprintField>) {
        val text = VfsUtilCore.loadText(file)
        val ns = BlueprintNamespace.fromPath(file.path)
        val lines = text.split("\n")
        var pos = 0
        for (i in lines.indices) {
            val line = lines[i]
            val m = HANDLE_RE.find(line)
            if (m != null) {
                val handle = m.groupValues[1]
                // Offset of the captured handle *value* (not the `handle:` key), so nav lands on
                // the value even when the value itself is literally "handle".
                val handleOffset = pos + (m.groups[1]?.range?.first ?: 0)
                var display = ""
                var type = ""
                var j = i + 1
                while (j < lines.size && j < i + 8) {
                    if (HANDLE_RE.find(lines[j]) != null) break
                    if (display.isEmpty()) DISPLAY_RE.find(lines[j])?.let { display = it.groupValues[1] }
                    if (type.isEmpty()) TYPE_RE.find(lines[j])?.let { type = it.groupValues[1] }
                    j++
                }
                out.add(BlueprintField(handle, display, type, file, handleOffset, ns))
            }
            pos += line.length + 1
        }
    }
}
