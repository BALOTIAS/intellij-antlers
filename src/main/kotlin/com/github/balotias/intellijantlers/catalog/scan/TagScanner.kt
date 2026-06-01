package com.github.balotias.intellijantlers.catalog.scan

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.regex.Pattern

/** A resolved PHP class location: the virtual file and the offset of the class name. */
data class NavTarget(val file: VirtualFile, val offset: Int)

/** Finds project-defined Statamic tags by scanning PHP files under a `Tags/` directory. */
object TagScanner {
    internal val CLASS_PATTERN =
        Pattern.compile("class\\s+([a-zA-Z0-9_]+)\\s+extends\\s+(?:\\\\?Statamic\\\\Tags\\\\)?Tags")

    fun scan(project: Project): List<String> = scanDir(project, "/Tags/", CLASS_PATTERN)

    fun find(project: Project, snakeName: String): NavTarget? =
        findInDir(project, "/Tags/", CLASS_PATTERN, snakeName)

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

    internal fun findInDir(project: Project, dirMarker: String, pattern: Pattern, snakeName: String): NavTarget? {
        try {
            val phpFiles = FilenameIndex.getAllFilesByExt(project, "php", GlobalSearchScope.projectScope(project))
            for (file in phpFiles) {
                if (!file.path.contains(dirMarker)) continue
                try {
                    val matcher = pattern.matcher(VfsUtilCore.loadText(file))
                    if (matcher.find() && camelToSnake(matcher.group(1)) == snakeName) return NavTarget(file, matcher.start(1))
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
        return null
    }

    internal fun camelToSnake(str: String): String =
        str.replace("([a-z])([A-Z]+)".toRegex(), "$1_$2").lowercase()
}
