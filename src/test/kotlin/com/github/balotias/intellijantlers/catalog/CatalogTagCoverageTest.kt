package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTagCoverageTest {

    // Statamic 6 core top-level tag handles (spec Appendix A).
    private val expected = setOf(
        "404", "asset", "assets", "cache", "children", "collection", "cookie", "dictionary", "dump",
        "foreach", "form", "get_content", "get_error", "get_errors", "get_files", "get_site", "glide",
        "increment", "installed", "link", "locales", "loop", "markdown", "mix", "mount_url", "nav",
        "nocache", "oauth", "obfuscate", "parent", "partial", "protect", "redirect", "route", "scope",
        "search", "section", "session", "svg", "switch", "taxonomy", "trans", "user", "user_groups",
        "user_roles", "users", "vite", "yield",
        // Template/layout structural constructs documented on the Antlers language page (not /tags/).
        "once", "prepend", "push", "slot", "stack"
    )

    private fun tag(name: String) = CatalogTags.ALL.first { it.name == name }

    @Test fun coversAllCoreTags() {
        val have = CatalogTags.ALL.map { it.name }.toSet()
        val missing = expected - have
        assertTrue("missing tags: $missing", missing.isEmpty())
    }

    @Test fun noDuplicatesAndDocUrls() {
        val names = CatalogTags.ALL.map { it.name }
        assertTrue("duplicate tag names", names.size == names.toSet().size)
        // Most tags live under /tags/; the Antlers structural constructs (push/once/slot…) are
        // documented on the language page instead, so allow either canonical Statamic docs location.
        assertTrue("docUrls well-formed",
            CatalogTags.ALL.all {
                it.docUrl.startsWith("https://statamic.dev/tags/") ||
                    it.docUrl.startsWith("https://statamic.dev/frontend/antlers")
            })
    }

    @Test fun pairFlagsForStructuralTags() {
        listOf("collection", "nav", "taxonomy", "form", "assets", "cache", "section", "loop", "foreach",
            "users", "nocache", "push", "prepend", "once").forEach { assertTrue("$it should be a pair", tag(it).isPair) }
        listOf("partial", "glide", "link", "svg", "redirect", "yield", "404", "asset", "mix", "trans",
            "switch", "stack", "slot").forEach { assertTrue("$it should be single", !tag(it).isPair) }
    }

    @Test fun subTagsPresent() {
        assertTrue(tag("session").methods.containsAll(listOf("set", "has", "forget")))
        assertTrue(tag("user").methods.containsAll(listOf("login_form", "can")))
        assertTrue(tag("collection").methods.contains("count"))
        assertTrue(tag("form").methods.containsAll(listOf("create", "errors", "fields")))
    }

    @Test fun keyParametersPresent() {
        fun params(t: String) = tag(t).parameters.map { it.name }.toSet()
        assertTrue(params("collection").containsAll(listOf("from", "limit", "sort")))
        assertTrue(params("glide").containsAll(listOf("width", "height")))
        assertTrue(params("nav").contains("handle"))
        assertTrue("every ParamDef has a name", CatalogTags.ALL.all { t -> t.parameters.all { it.name.isNotBlank() } })
    }
}
