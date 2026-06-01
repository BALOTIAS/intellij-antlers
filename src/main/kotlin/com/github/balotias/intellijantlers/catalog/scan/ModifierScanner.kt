package com.github.balotias.intellijantlers.catalog.scan

import com.intellij.openapi.project.Project
import java.util.regex.Pattern

/** Finds project-defined Statamic modifiers by scanning PHP files under a `Modifiers/` directory. */
object ModifierScanner {
    private val CLASS_PATTERN =
        Pattern.compile("class\\s+([a-zA-Z0-9_]+)\\s+extends\\s+(?:\\\\?Statamic\\\\Modifiers\\\\)?Modifier")

    fun scan(project: Project): List<String> = TagScanner.scanDir(project, "/Modifiers/", CLASS_PATTERN)
}
