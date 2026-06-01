package com.github.balotias.intellijantlers.catalog

/** The bundled Statamic-6 catalog, expressed as Kotlin data (no external JSON parser is available
 *  on the plugin runtime classpath). Edit here to extend coverage. */
object CatalogData {
    val TAGS: List<TagDef> = listOf(
        TagDef(
            name = "collection",
            description = "Fetch and loop over entries in a collection.",
            docUrl = "https://statamic.dev/tags/collection",
            isPair = true,
            methods = listOf("count", "next", "previous", "older", "newer"),
            parameters = listOf(
                ParamDef(name = "from", description = "Collection handle(s) to fetch from."),
                ParamDef(name = "limit", description = "Maximum number of entries.", type = "integer"),
                ParamDef(name = "sort", description = "Field and direction, e.g. date:desc."),
                ParamDef(name = "filter", description = "A custom query filter."),
                ParamDef(name = "paginate", description = "Entries per page.", type = "integer"),
                ParamDef(name = "as", description = "Alias the results into a named loop."),
                ParamDef(name = "scope", description = "Scope each item under a variable.")
            )
        ),
        TagDef(
            name = "nav",
            description = "Build navigation / structure trees.",
            docUrl = "https://statamic.dev/tags/nav",
            isPair = true,
            methods = listOf("breadcrumbs"),
            parameters = listOf(
                ParamDef(name = "handle", description = "The navigation handle."),
                ParamDef(name = "from", description = "Start the tree from a URI."),
                ParamDef(name = "include_home", description = "Include the home page.", type = "boolean"),
                ParamDef(name = "max_depth", description = "Maximum nesting depth.", type = "integer")
            )
        ),
        TagDef(
            name = "partial",
            description = "Include another template.",
            docUrl = "https://statamic.dev/tags/partial",
            isPair = false,
            methods = listOf("if_exists", "exists"),
            parameters = listOf(
                ParamDef(name = "src", description = "Path to the partial.", required = true)
            )
        ),
        TagDef(
            name = "asset",
            description = "Fetch a single asset.",
            docUrl = "https://statamic.dev/tags/asset",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "id", description = "The asset id (container::path)."),
                ParamDef(name = "src", description = "The asset path.")
            )
        ),
        TagDef(
            name = "assets",
            description = "Loop over multiple assets.",
            docUrl = "https://statamic.dev/tags/assets",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "handle", description = "The field/variable holding assets."),
                ParamDef(name = "limit", description = "Maximum number of assets.", type = "integer")
            )
        ),
        TagDef(
            name = "glide",
            description = "Manipulate images.",
            docUrl = "https://statamic.dev/tags/glide",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "src", description = "Source image path/url."),
                ParamDef(name = "width", description = "Target width.", type = "integer"),
                ParamDef(name = "height", description = "Target height.", type = "integer"),
                ParamDef(name = "fit", description = "Fit mode (crop, contain, max...)."),
                ParamDef(name = "format", description = "Output format.")
            )
        ),
        TagDef(
            name = "form",
            description = "Render and handle forms.",
            docUrl = "https://statamic.dev/tags/form",
            isPair = true,
            methods = listOf("create", "errors", "success", "set", "submission", "submissions"),
            parameters = listOf(
                ParamDef(name = "in", description = "The form handle."),
                ParamDef(name = "redirect", description = "Where to redirect on success.")
            )
        ),
        TagDef(
            name = "user",
            description = "Authenticated user / user data.",
            docUrl = "https://statamic.dev/tags/user",
            isPair = true,
            methods = listOf("profile", "logout", "can", "is", "in")
        ),
        TagDef(
            name = "taxonomy",
            description = "Loop over taxonomy terms.",
            docUrl = "https://statamic.dev/tags/taxonomy",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "from", description = "Taxonomy handle."),
                ParamDef(name = "sort", description = "Sort field/direction.")
            )
        ),
        TagDef(
            name = "cache",
            description = "Cache a portion of a template.",
            docUrl = "https://statamic.dev/tags/cache",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "for", description = "Duration, e.g. 60 minutes."),
                ParamDef(name = "key", description = "A custom cache key.")
            )
        ),
        TagDef(
            name = "section",
            description = "Define a template section.",
            isPair = true
        ),
        TagDef(
            name = "yield",
            description = "Output a named section.",
            isPair = false
        ),
        TagDef(
            name = "redirect",
            description = "Redirect the response.",
            isPair = false
        ),
        TagDef(
            name = "increment",
            description = "An auto-incrementing counter.",
            isPair = false
        ),
        TagDef(
            name = "markdown",
            description = "Render markdown.",
            isPair = true
        ),
        TagDef(
            name = "obfuscate",
            description = "Obfuscate text from bots.",
            isPair = true
        ),
        TagDef(
            name = "session",
            description = "Read/flash session data.",
            isPair = true
        ),
        TagDef(
            name = "mix",
            description = "Laravel Mix asset url.",
            isPair = false
        ),
        TagDef(
            name = "vite",
            description = "Vite asset tags.",
            isPair = false
        ),
        TagDef(
            name = "svg",
            description = "Inline an SVG.",
            isPair = false
        ),
        TagDef(
            name = "trans",
            description = "Translate a string.",
            isPair = false
        ),
        TagDef(
            name = "link",
            description = "Generate a URL.",
            isPair = false
        ),
        TagDef(
            name = "dump",
            description = "Dump variables for debugging.",
            isPair = false
        ),
        TagDef(
            name = "get_content",
            description = "Fetch content by id/url.",
            isPair = true
        ),
        TagDef(
            name = "search",
            description = "Query a search index.",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "index", description = "The search index handle."),
                ParamDef(name = "query", description = "The search query variable.")
            )
        )
    )

    val MODIFIERS: List<ModifierDef> = listOf(
        ModifierDef(name = "upper", description = "Uppercase the value."),
        ModifierDef(name = "lower", description = "Lowercase the value."),
        ModifierDef(name = "title", description = "Title-case the value."),
        ModifierDef(name = "ucfirst", description = "Capitalise the first letter."),
        ModifierDef(name = "truncate", description = "Truncate to a length.", takesArguments = true),
        ModifierDef(name = "limit", description = "Limit to N items/characters.", takesArguments = true),
        ModifierDef(name = "raw", description = "Output without escaping."),
        ModifierDef(name = "markdown", description = "Render markdown."),
        ModifierDef(name = "nl2br", description = "Convert newlines to <br>."),
        ModifierDef(name = "strip_tags", description = "Remove HTML tags.", takesArguments = true),
        ModifierDef(name = "format", description = "Format a date.", takesArguments = true),
        ModifierDef(name = "format_localized", description = "Locale-aware date format.", takesArguments = true),
        ModifierDef(name = "relative", description = "Relative date, e.g. 3 days ago."),
        ModifierDef(name = "count", description = "Count items."),
        ModifierDef(name = "length", description = "Length of a string/array."),
        ModifierDef(name = "first", description = "First item."),
        ModifierDef(name = "last", description = "Last item."),
        ModifierDef(name = "reverse", description = "Reverse a string/array."),
        ModifierDef(name = "sort", description = "Sort an array.", takesArguments = true),
        ModifierDef(name = "where", description = "Filter an array by key/value.", takesArguments = true),
        ModifierDef(name = "pluck", description = "Pluck a field from each item.", takesArguments = true),
        ModifierDef(name = "join", description = "Join an array with a glue string.", takesArguments = true),
        ModifierDef(name = "explode", description = "Split a string into an array.", takesArguments = true),
        ModifierDef(name = "replace", description = "Replace a substring.", takesArguments = true),
        ModifierDef(name = "url", description = "The URL of an entry/asset."),
        ModifierDef(name = "slugify", description = "Make a URL slug."),
        ModifierDef(name = "default", description = "Fallback when empty.", takesArguments = true),
        ModifierDef(name = "ensure_right", description = "Ensure the string ends with a suffix.", takesArguments = true),
        ModifierDef(name = "embed_url", description = "Make an embeddable URL."),
        ModifierDef(name = "to_json", description = "Encode as JSON.")
    )
}
