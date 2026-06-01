package com.github.balotias.intellijantlers.completion

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.regex.Pattern

object AntlersCustomTagFinder {

    // Matches `class MyCustomTag extends Tags` or `class MyCustomTag extends \Statamic\Tags\Tags`
    private val CLASS_PATTERN = Pattern.compile("class\\s+([a-zA-Z0-9_]+)\\s+extends\\s+(?:\\\\Statamic\\\\Tags\\\\)?Tags")

    fun findCustomTags(project: Project): List<String> {
        val tags = mutableListOf<String>()
        
        try {
            // Lightweight search: only look at files ending with .php
            val phpFiles = FilenameIndex.getAllFilesByExt(project, "php", GlobalSearchScope.projectScope(project))
            
            for (file in phpFiles) {
                val path = file.path
                // Optimization: Only scan files inside "Tags" directories
                if (path.contains("/Tags/")) {
                    try {
                        val content = VfsUtilCore.loadText(file)
                        val matcher = CLASS_PATTERN.matcher(content)
                        if (matcher.find()) {
                            val className = matcher.group(1)
                            // Convert CamelCase to snake_case
                            tags.add(camelToSnakeCase(className))
                        }
                    } catch (e: Exception) {
                        // Ignore read errors
                    }
                }
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            // Index not ready or other issues
        }
        
        return tags.distinct()
    }

    private fun camelToSnakeCase(str: String): String {
        val regex = "([a-z])([A-Z]+)"
        val replacement = "$1_$2"
        return str.replace(regex.toRegex(), replacement).lowercase()
    }
}
