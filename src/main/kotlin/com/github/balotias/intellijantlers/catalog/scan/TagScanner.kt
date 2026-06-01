package com.github.balotias.intellijantlers.catalog.scan

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.regex.Pattern

/** Finds project-defined Statamic tags by scanning PHP files under a `Tags/` directory. */
object TagScanner {
    private val CLASS_PATTERN =
        Pattern.compile("class\\s+([a-zA-Z0-9_]+)\\s+extends\\s+(?:\\\\?Statamic\\\\Tags\\\\)?Tags")

    fun scan(project: Project): List<String> = scanDir(project, "/Tags/", CLASS_PATTERN)

    internal fun scanDir(project: Project, dirMarker: String, pattern: Pattern): List<String> {
        val names = mutableListOf<String>()
        try {
            val phpFiles = FilenameIndex.getAllFilesByExt(project, "php", GlobalSearchScope.projectScope(project))
            for (file in phpFiles) {
                if (!file.path.contains(dirMarker)) continue
                try {
                    val matcher = pattern.matcher(VfsUtilCore.loadText(file))
                    if (matcher.find()) names.add(camelToSnake(matcher.group(1)))
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    // ignore unreadable files
                }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            // index not ready
        }
        return names.distinct()
    }

    internal fun camelToSnake(str: String): String =
        str.replace("([a-z])([A-Z]+)".toRegex(), "$1_$2").lowercase()
}
