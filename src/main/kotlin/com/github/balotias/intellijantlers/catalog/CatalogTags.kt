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
                ParamDef(name = "scope", description = "Scope each item under a variable."),
                ParamDef(name = "query_scope", description = "Apply a query scope class to the results.")
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
                ParamDef(name = "max_depth", description = "Maximum nesting depth.", type = "integer"),
                ParamDef(name = "reverse", description = "Reverse the order of the tree.", type = "boolean"),
                ParamDef(name = "trim", description = "Trim whitespace in the output.", type = "boolean")
            )
        ),
        TagDef(
            name = "partial",
            description = "Include another template.",
            docUrl = "https://statamic.dev/tags/partial",
            isPair = false,
            methods = listOf("if_exists", "exists"),
            parameters = listOf(
                ParamDef(name = "src", description = "Path to the partial.", required = true),
                ParamDef(name = "when", description = "Only render the partial when this is truthy.", type = "boolean"),
                ParamDef(name = "unless", description = "Only render the partial unless this is truthy.", type = "boolean")
            )
        ),
        // The asset tag retrieves a single asset by URL and exposes its data inside the tag pair
        // (`{{ asset url=… }} {{ url }} {{ /asset }}`). Source: Tags/Asset.php — `hasAny(['url','src'])`.
        TagDef(
            name = "asset",
            description = "Fetch a single asset by URL and expose its data inside the pair.",
            docUrl = "https://statamic.dev/tags/asset",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "url", description = "The asset URL.", required = true),
                ParamDef(name = "src", description = "Alias of url.")
            )
        ),
        TagDef(
            name = "assets",
            description = "Loop over multiple assets.",
            docUrl = "https://statamic.dev/tags/assets",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "handle", description = "The field/variable holding assets."),
                ParamDef(name = "limit", description = "Maximum number of assets.", type = "integer"),
                ParamDef(name = "query_scope", description = "Apply a query scope class to the results.")
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
                ParamDef(name = "sort", description = "Sort field/direction."),
                ParamDef(name = "min_count", description = "Only include terms with at least this many entries.", type = "integer"),
                ParamDef(name = "site", description = "Limit to a specific site."),
                ParamDef(name = "query_scope", description = "Apply a query scope class to the results.")
            )
        ),
        TagDef(
            name = "cache",
            description = "Cache a portion of a template.",
            docUrl = "https://statamic.dev/tags/cache",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "for", description = "Duration, e.g. 60 minutes."),
                ParamDef(name = "key", description = "A custom cache key."),
                ParamDef(name = "scope", description = "Cache scope (site, page, or a custom value)."),
                ParamDef(name = "store", description = "The cache store to use.")
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
        // Stacks: push/prepend code from anywhere, render it once with {{ stack:name }}.
        // (Antlers language constructs — documented on the frontend page, not under /tags/.)
        TagDef(
            name = "stack",
            description = "Render the complete contents of a named stack.",
            docUrl = "https://statamic.dev/frontend/antlers#stacks",
            isPair = false
        ),
        TagDef(
            name = "push",
            description = "Push template code onto a named stack to render elsewhere in the layout.",
            docUrl = "https://statamic.dev/frontend/antlers#stacks",
            isPair = true
        ),
        TagDef(
            name = "prepend",
            description = "Prepend template code onto the beginning of a named stack.",
            docUrl = "https://statamic.dev/frontend/antlers#stacks",
            isPair = true
        ),
        TagDef(
            name = "once",
            description = "Render the enclosed template only once per rendering cycle.",
            docUrl = "https://statamic.dev/frontend/antlers#once",
            isPair = true
        ),
        // Single: `{{ slot }}` / `{{ slot:name }}` renders content passed into a partial pair. Kept
        // single so the bare render form isn't flagged unclosed; the named `{{ slot:x }}…{{ /slot:x }}`
        // definition still colors/completes, it just isn't balance-checked.
        TagDef(
            name = "slot",
            description = "Output content passed into a partial via its tag pair (named or default slot).",
            docUrl = "https://statamic.dev/frontend/antlers#slots",
            isPair = false
        ),
        TagDef(
            name = "redirect",
            description = "Redirect the response.",
            docUrl = "https://statamic.dev/tags/redirect",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "to", description = "URL to redirect to."),
                ParamDef(name = "url", description = "Alias of to."),
                ParamDef(name = "route", description = "A named route to redirect to."),
                ParamDef(name = "response", description = "HTTP status code (default 302).", type = "integer")
            )
        ),
        TagDef(
            name = "increment",
            description = "An auto-incrementing counter.",
            docUrl = "https://statamic.dev/tags/increment",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "from", description = "Start value.", type = "integer"),
                ParamDef(name = "by", description = "Step amount.", type = "integer"),
                ParamDef(name = "to", description = "Reset after this value.", type = "integer")
            )
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
            isPair = false,
            parameters = listOf(
                ParamDef(name = "src", description = "Path to the asset."),
                ParamDef(name = "from", description = "Manifest directory.")
            )
        ),
        TagDef(
            name = "vite",
            description = "Vite asset tags.",
            docUrl = "https://statamic.dev/tags/vite",
            isPair = false,
            methods = listOf("content"),
            parameters = listOf(
                ParamDef(name = "src", description = "Entry point(s) to include."),
                ParamDef(name = "directory", description = "Build output directory."),
                ParamDef(name = "hot", description = "Path to the hot file.")
            )
        ),
        TagDef(
            name = "svg",
            description = "Inline an SVG.",
            docUrl = "https://statamic.dev/tags/svg",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "src", description = "The SVG asset/path."),
                ParamDef(name = "sanitize", description = "Sanitize the SVG markup.", type = "boolean"),
                ParamDef(name = "allow_attrs", description = "Extra attributes to allow when sanitizing."),
                ParamDef(name = "allow_tags", description = "Extra tags to allow when sanitizing."),
                ParamDef(name = "title", description = "Accessible <title> for the SVG."),
                ParamDef(name = "desc", description = "Accessible <desc> for the SVG.")
            )
        ),
        TagDef(
            name = "trans",
            description = "Translate a string.",
            docUrl = "https://statamic.dev/tags/trans",
            isPair = false,
            parameters = listOf(
                ParamDef(name = "key", description = "The translation key."),
                ParamDef(name = "fallback", description = "Fallback string if the key is missing.")
            )
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
            isPair = false,
            parameters = listOf(
                ParamDef(name = "from", description = "The id/URI to fetch."),
                ParamDef(name = "site", description = "The site to fetch from.")
            )
        ),
        TagDef(
            name = "search",
            description = "Query a search index.",
            docUrl = "https://statamic.dev/tags/search",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "index", description = "The search index handle."),
                ParamDef(name = "query", description = "The search query variable."),
                ParamDef(name = "site", description = "Limit results to a specific site.")
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
            isPair = true,
            parameters = listOf(
                ParamDef(name = "of", description = "Fetch children of another entry/URI."),
                ParamDef(name = "collection", description = "Limit to a collection.")
            )
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
                ParamDef(name = "sort", description = "Sort field and direction."),
                ParamDef(name = "query_scope", description = "Apply a query scope class to the results.")
            )
        ),
        TagDef(
            // The array is passed as the tag part (`{{ foreach:my_array }}`) or via `array=`/`:array=`.
            // Source: Tags/Iterate.php — there is no `in` parameter.
            name = "foreach",
            description = "Loop over items in a variable (key/value via as=\"key|value\").",
            docUrl = "https://statamic.dev/tags/foreach",
            isPair = true,
            parameters = listOf(
                ParamDef(name = "array", description = "The array to iterate (alternative to the tag part)."),
                ParamDef(name = "as", description = "Alias the key/value, e.g. as=\"key|value\"."),
                ParamDef(name = "limit", description = "Maximum number of items.", type = "integer")
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
                ParamDef(name = "in", description = "Directory path to read.", required = true),
                ParamDef(name = "from", description = "Alias of in."),
                ParamDef(name = "depth", description = "How deep to recurse.", type = "integer"),
                ParamDef(name = "extension", description = "Filter by file extension(s)."),
                ParamDef(name = "include", description = "Glob(s) to include."),
                ParamDef(name = "exclude", description = "Glob(s) to exclude."),
                ParamDef(name = "not_in", description = "Directories to skip."),
                ParamDef(name = "file_size", description = "Filter by file size."),
                ParamDef(name = "file_date", description = "Filter by file date."),
                ParamDef(name = "limit", description = "Maximum number of files.", type = "integer"),
                ParamDef(name = "offset", description = "Skip this many files.", type = "integer"),
                ParamDef(name = "sort", description = "Sort field and direction.")
            )
        ),
        TagDef(
            // Pair: exposes the site's data inside `{{ get_site:handle }} … {{ /get_site:handle }}`.
            name = "get_site",
            description = "Output data from a specific site (inside the tag pair).",
            docUrl = "https://statamic.dev/tags/get_site",
            isPair = true,
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
                ParamDef(name = "role", description = "Filter by user role."),
                ParamDef(name = "query_scope", description = "Apply a query scope class to the results.")
            )
        )
    )
}
