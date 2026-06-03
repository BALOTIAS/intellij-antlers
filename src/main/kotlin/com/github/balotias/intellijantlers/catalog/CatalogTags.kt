package com.github.balotias.intellijantlers.catalog

/** Bundled Statamic-6 tag catalog (Kotlin data — no runtime JSON parser). Extend here. */
object CatalogTags {
    val ALL: List<TagDef> = listOf(
        TagDef(
            name = "relate",
            description = "Loop over a relationship field (Statamic 5; removed in 6 — use augmentation).",
            docUrl = "https://statamic.dev/tags/relate",
            isPair = true,
            removedIn = 6,
        ),
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
            methods = listOf("batch", "data_url"),
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
            methods = listOf("create", "errors", "fields", "set", "submission", "submissions", "success"),
            parameters = listOf(
                ParamDef(name = "in", description = "The form handle."),
                ParamDef(name = "redirect", description = "Where to redirect on success.")
            )
        ),
        TagDef(
            name = "user",
            description = "Authenticated user / user data.",
            docUrl = "https://statamic.dev/tags/user",
            isPair = false,
            methods = listOf(
                "login_form", "logout", "logout_url", "register_form", "profile", "profile_form",
                "password_form", "forgot_password_form", "reset_password_form", "can", "is", "in",
                "passkey_form", "passkeys", "delete_passkey_form", "elevated_session_form",
                "two_factor_challenge_form", "two_factor_enable_form", "two_factor_setup_form",
                "two_factor_enabled", "two_factor_recovery_codes", "reset_two_factor_recovery_codes_form",
                "disable_two_factor_form"
            )
        ),
        TagDef(
            name = "taxonomy",
            description = "Loop over taxonomy terms.",
            docUrl = "https://statamic.dev/tags/taxonomy",
            isPair = true,
            methods = listOf("count"),
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
            docUrl = "https://statamic.dev/tags/section",
            isPair = true
        ),
        TagDef(
            name = "yield",
            description = "Output a named section.",
            docUrl = "https://statamic.dev/tags/yield",
            isPair = false
        ),
        TagDef(
            name = "redirect",
            description = "Redirect the response.",
            docUrl = "https://statamic.dev/tags/redirect",
            isPair = false
        ),
        TagDef(
            name = "increment",
            description = "An auto-incrementing counter.",
            docUrl = "https://statamic.dev/tags/increment",
            isPair = false
        ),
        TagDef(
            name = "markdown",
            description = "Render markdown.",
            docUrl = "https://statamic.dev/tags/markdown",
            isPair = true,
            methods = listOf("indent")
        ),
        TagDef(
            name = "obfuscate",
            description = "Obfuscate text from bots.",
            docUrl = "https://statamic.dev/tags/obfuscate",
            isPair = true
        ),
        TagDef(
            name = "session",
            description = "Get, set, check, and forget data in the user's session.",
            docUrl = "https://statamic.dev/tags/session",
            isPair = false,
            methods = listOf("set", "get", "has", "forget", "flush", "flash", "dump")
        ),
        TagDef(
            name = "mix",
            description = "Laravel Mix asset url.",
            docUrl = "https://statamic.dev/tags/mix",
            isPair = false
        ),
        TagDef(
            name = "vite",
            description = "Vite asset tags.",
            docUrl = "https://statamic.dev/tags/vite",
            isPair = false,
            methods = listOf("content")
        ),
        TagDef(
            name = "svg",
            description = "Inline an SVG.",
            docUrl = "https://statamic.dev/tags/svg",
            isPair = false
        ),
        TagDef(
            name = "trans",
            description = "Translate a string.",
            docUrl = "https://statamic.dev/tags/trans",
            isPair = false
        ),
        TagDef(
            name = "link",
            description = "Generate a URL.",
            docUrl = "https://statamic.dev/tags/link",
            isPair = false
        ),
        TagDef(
            name = "dump",
            description = "Dump variables for debugging.",
            docUrl = "https://statamic.dev/tags/dump",
            isPair = false
        ),
        TagDef(
            name = "get_content",
            description = "Fetch content by id/url.",
            docUrl = "https://statamic.dev/tags/get_content",
            isPair = false
        ),
        TagDef(
            name = "search",
            description = "Query a search index.",
            docUrl = "https://statamic.dev/tags/search",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "index", description = "The search index handle."),
                ParamDef(name = "query", description = "The search query variable.")
            )
        ),
        // New tags from Appendix A
        TagDef(
            name = "404",
            description = "Abort with a 404 response.",
            docUrl = "https://statamic.dev/tags/404",
            isPair = false
        ),
        TagDef(
            name = "children",
            description = "Loop over the children of the current page.",
            docUrl = "https://statamic.dev/tags/children",
            isPair = true
        ),
        TagDef(
            name = "cookie",
            description = "Read or write cookies.",
            docUrl = "https://statamic.dev/tags/cookie",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "name", description = "The cookie name.", required = true),
                ParamDef(name = "value", description = "The value to set."),
                ParamDef(name = "minutes", description = "Expiry in minutes.", type = "integer")
            )
        ),
        TagDef(
            name = "dictionary",
            description = "Loop over entries in a dictionary.",
            docUrl = "https://statamic.dev/tags/dictionary",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "from", description = "Dictionary handle.", required = true),
                ParamDef(name = "limit", description = "Maximum number of items.", type = "integer"),
                ParamDef(name = "sort", description = "Sort field and direction.")
            )
        ),
        TagDef(
            name = "foreach",
            description = "Loop over items in a variable.",
            docUrl = "https://statamic.dev/tags/foreach",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "in", description = "The variable to iterate over.", required = true)
            )
        ),
        TagDef(
            name = "get_error",
            description = "Retrieve a single error message.",
            docUrl = "https://statamic.dev/tags/get_error",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "name", description = "The field name to get the error for.", required = true),
                ParamDef(name = "bag", description = "The error bag to look in.")
            )
        ),
        TagDef(
            name = "get_errors",
            description = "Loop over validation error messages.",
            docUrl = "https://statamic.dev/tags/get_errors",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "bag", description = "The error bag to look in.")
            )
        ),
        TagDef(
            name = "get_files",
            description = "Loop over files in a directory.",
            docUrl = "https://statamic.dev/tags/get_files",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "from", description = "Directory path.", required = true)
            )
        ),
        TagDef(
            name = "get_site",
            description = "Output data from a specific site.",
            docUrl = "https://statamic.dev/tags/get_site",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "handle", description = "The site handle.", required = true)
            )
        ),
        TagDef(
            name = "installed",
            description = "Check whether an addon is installed.",
            docUrl = "https://statamic.dev/tags/installed",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "name", description = "Addon package name.", required = true)
            )
        ),
        TagDef(
            name = "locales",
            description = "Loop over the available locales/sites.",
            docUrl = "https://statamic.dev/tags/locales",
            isPair = true,
            methods = listOf("count"),
            parameters = listOf(
                ParamDef(name = "all", description = "Include all locales.", type = "boolean")
            )
        ),
        TagDef(
            name = "loop",
            description = "Loop a specified number of times.",
            docUrl = "https://statamic.dev/tags/loop",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "times", description = "How many times to loop.", type = "integer", required = true),
                ParamDef(name = "from", description = "Start from this number.", type = "integer")
            )
        ),
        TagDef(
            name = "mount_url",
            description = "Output the URL of a mounted collection.",
            docUrl = "https://statamic.dev/tags/mount_url",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "handle", description = "The collection handle.", required = true)
            )
        ),
        TagDef(
            name = "nocache",
            description = "Exclude a region from static caching.",
            docUrl = "https://statamic.dev/tags/nocache",
            isPair = true
        ),
        TagDef(
            name = "oauth",
            description = "Output OAuth login links.",
            docUrl = "https://statamic.dev/tags/oauth",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "provider", description = "OAuth provider name.", required = true)
            )
        ),
        TagDef(
            name = "parent",
            description = "Loop over the ancestor pages of the current page.",
            docUrl = "https://statamic.dev/tags/parent",
            isPair = true
        ),
        TagDef(
            name = "protect",
            description = "Protect content behind authentication.",
            docUrl = "https://statamic.dev/tags/protect",
            isPair = true,
            methods = listOf("password_form"),
            parameters = listOf(
                ParamDef(name = "scheme", description = "The protection scheme handle.")
            )
        ),
        TagDef(
            name = "route",
            description = "Generate a URL for a named route.",
            docUrl = "https://statamic.dev/tags/route",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "name", description = "The route name.", required = true)
            )
        ),
        TagDef(
            name = "scope",
            description = "Scope variables to a tag pair.",
            docUrl = "https://statamic.dev/tags/scope",
            isPair = true
        ),
        TagDef(
            name = "switch",
            description = "Alternate between values on each iteration.",
            docUrl = "https://statamic.dev/tags/switch",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "between", description = "Pipe-delimited list of values to cycle through.", required = true)
            )
        ),
        TagDef(
            name = "user_groups",
            description = "Loop over user groups.",
            docUrl = "https://statamic.dev/tags/user_groups",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "handle", description = "Filter by group handle.")
            )
        ),
        TagDef(
            name = "user_roles",
            description = "Loop over user roles.",
            docUrl = "https://statamic.dev/tags/user_roles",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "handle", description = "Filter by role handle.")
            )
        ),
        TagDef(
            name = "users",
            description = "Loop over users.",
            docUrl = "https://statamic.dev/tags/users",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "limit", description = "Maximum number of users.", type = "integer"),
                ParamDef(name = "sort", description = "Sort field and direction."),
                ParamDef(name = "group", description = "Filter by user group."),
                ParamDef(name = "role", description = "Filter by user role.")
            )
        )
    )
}
