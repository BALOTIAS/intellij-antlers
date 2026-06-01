package com.github.balotias.intellijantlers.blueprint

data class SystemVariable(val name: String, val description: String)

/** Common Statamic variables available in most template contexts. */
object SystemVariables {
    val ALL: List<SystemVariable> = listOf(
        SystemVariable("title", "The title of the entry or page."),
        SystemVariable("url", "The URL of the entry."),
        SystemVariable("permalink", "The absolute URL of the entry."),
        SystemVariable("slug", "The URL slug of the entry."),
        SystemVariable("id", "The unique id of the entry."),
        SystemVariable("date", "The entry's date."),
        SystemVariable("last_modified", "When the entry was last modified."),
        SystemVariable("status", "draft, published, or scheduled."),
        SystemVariable("published", "Whether the entry is published."),
        SystemVariable("content", "The rendered main content."),
        SystemVariable("author", "The entry's author."),
        SystemVariable("template", "The template used to render."),
        SystemVariable("layout", "The layout used to render."),
        SystemVariable("collection", "The entry's collection handle."),
        SystemVariable("mount", "The entry the collection is mounted to."),
        SystemVariable("parent", "The parent entry in a structure."),
        SystemVariable("depth", "The depth in a structure tree."),
        SystemVariable("is_current", "Whether this is the current URL."),
        SystemVariable("is_parent", "Whether this is an ancestor of the current URL."),
        SystemVariable("now", "The current date/time."),
        SystemVariable("site", "The current site handle."),
        SystemVariable("locale", "The current locale."),
        SystemVariable("current_url", "The current request URL."),
        SystemVariable("current_uri", "The current request URI."),
        SystemVariable("csrf_token", "The CSRF token value."),
        SystemVariable("environment", "The application environment.")
    )
}
