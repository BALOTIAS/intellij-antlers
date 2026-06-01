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
    private val IMPORT_RE = Regex("""^\s*(?:-\s*)?import:\s*['"]?([A-Za-z0-9_-]+)['"]?\s*$""")

    /** An `import: <fieldset>` directive at [atPath] within the blueprint/fieldset of [baseNs]. */
    private data class ImportMarker(val baseNs: BlueprintNamespace, val atPath: List<String>, val imported: String)

    fun scan(project: Project): List<BlueprintField> {
        val raw = mutableListOf<BlueprintField>()
        val markers = mutableListOf<ImportMarker>()
        try {
            val yamls = FilenameIndex.getAllFilesByExt(project, "yaml", GlobalSearchScope.projectScope(project))
            for (file in yamls) {
                val p = file.path
                if (!p.contains("/resources/blueprints/") && !p.contains("/resources/fieldsets/")) continue
                try {
                    extract(file, raw, markers)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) { /* tolerant */ }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) { /* index not ready */ }
        return expandImports(raw, markers)
    }

    private fun extract(file: VirtualFile, out: MutableList<BlueprintField>, markers: MutableList<ImportMarker>) {
        val text = VfsUtilCore.loadText(file)
        val base = BlueprintNamespace.fromPath(file.path)
        val lines = text.split("\n")
        val stack = ArrayDeque<Pair<Int, String>>()   // (indent, handle), deepest last
        var pos = 0
        for (i in lines.indices) {
            val line = lines[i]
            val m = HANDLE_RE.find(line)
            if (m != null) {
                val indent = leadingWs(line)
                while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
                val handle = m.groupValues[1]
                val path = stack.map { it.second }
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
                out.add(BlueprintField(handle, display, type, file, handleOffset, base.copy(path = path)))
                stack.addLast(indent to handle)
            } else {
                val im = IMPORT_RE.find(line)
                if (im != null) {
                    val indent = leadingWs(line)
                    while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
                    markers.add(ImportMarker(base, stack.map { it.second }, im.groupValues[1]))
                }
            }
            pos += line.length + 1
        }
    }

    /** Number of leading space/tab characters on [line] (the indentation column). */
    private fun leadingWs(line: String): Int {
        var n = 0
        while (n < line.length && (line[n] == ' ' || line[n] == '\t')) n++
        return n
    }

    /** Inline every blueprint-level import marker by grafting the imported fieldset's expanded fields. */
    private fun expandImports(raw: List<BlueprintField>, markers: List<ImportMarker>): List<BlueprintField> {
        val out = raw.toMutableList()
        for (m in markers) {
            if (m.baseNs.kind == BlueprintNamespace.Kind.FIELDSET) continue  // fieldset→fieldset handled by recursion
            for (field in expandFieldset(m.imported, raw, markers, mutableSetOf())) {
                out.add(field.copy(namespace = m.baseNs.copy(path = m.atPath + field.namespace.path)))
            }
        }
        return out
    }

    /**
     * The fully-expanded fields of fieldset [handle], with namespace paths RELATIVE to the fieldset root
     * (only `.namespace.path` matters to callers; kind/handle are re-stamped at the graft site).
     * Cycle-guarded via [visited].
     */
    private fun expandFieldset(
        handle: String,
        raw: List<BlueprintField>,
        markers: List<ImportMarker>,
        visited: MutableSet<String>
    ): List<BlueprintField> {
        if (!visited.add(handle)) return emptyList()       // cycle: stop
        val result = mutableListOf<BlueprintField>()
        raw.filterTo(result) {
            it.namespace.kind == BlueprintNamespace.Kind.FIELDSET && it.namespace.handle == handle
        }
        for (m in markers) {
            if (m.baseNs.kind == BlueprintNamespace.Kind.FIELDSET && m.baseNs.handle == handle) {
                for (field in expandFieldset(m.imported, raw, markers, visited)) {
                    result.add(field.copy(namespace = field.namespace.copy(path = m.atPath + field.namespace.path)))
                }
            }
        }
        visited.remove(handle)
        return result
    }
}
