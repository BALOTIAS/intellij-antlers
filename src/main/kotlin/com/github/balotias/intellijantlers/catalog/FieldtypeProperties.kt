package com.github.balotias.intellijantlers.catalog

/** A property exposed by a fieldtype's augmentation (e.g. `url` on an asset), for dotted access. */
data class FieldProperty(val name: String, val description: String)

/** Curated augmentation properties per Statamic fieldtype. Kotlin data (no JSON parser at runtime). */
object FieldtypeProperties {

    private val ASSETS = listOf(
        FieldProperty("url", "The asset's URL."),
        FieldProperty("permalink", "The absolute URL of the asset."),
        FieldProperty("path", "The path relative to the container."),
        FieldProperty("alt", "The alt text."),
        FieldProperty("title", "The asset's title."),
        FieldProperty("basename", "The filename with extension."),
        FieldProperty("filename", "The filename without extension."),
        FieldProperty("extension", "The file extension."),
        FieldProperty("size", "The human-readable file size."),
        FieldProperty("size_bytes", "The file size in bytes."),
        FieldProperty("is_image", "Whether the asset is an image."),
        FieldProperty("width", "The image width in pixels."),
        FieldProperty("height", "The image height in pixels."),
        FieldProperty("mime_type", "The MIME type."),
        FieldProperty("last_modified", "When the asset was last modified.")
    )
    private val ENTRIES = listOf(
        FieldProperty("id", "The entry's id."),
        FieldProperty("title", "The entry's title."),
        FieldProperty("slug", "The entry's slug."),
        FieldProperty("url", "The entry's URL."),
        FieldProperty("permalink", "The absolute URL."),
        FieldProperty("date", "The entry's date."),
        FieldProperty("status", "draft, published, or scheduled."),
        FieldProperty("published", "Whether the entry is published."),
        FieldProperty("author", "The entry's author."),
        FieldProperty("edit_url", "The control-panel edit URL.")
    )
    private val USERS = listOf(
        FieldProperty("id", "The user's id."),
        FieldProperty("name", "The user's name."),
        FieldProperty("email", "The user's email."),
        FieldProperty("avatar", "The user's avatar."),
        FieldProperty("initials", "The user's initials."),
        FieldProperty("is_admin", "Whether the user is a super admin."),
        FieldProperty("last_login", "When the user last logged in."),
        FieldProperty("edit_url", "The control-panel edit URL.")
    )
    private val TERMS = listOf(
        FieldProperty("id", "The term's id."),
        FieldProperty("title", "The term's title."),
        FieldProperty("slug", "The term's slug."),
        FieldProperty("url", "The term's URL."),
        FieldProperty("permalink", "The absolute URL."),
        FieldProperty("entries_count", "Number of entries with this term.")
    )
    private val LINK = listOf(
        FieldProperty("url", "The link URL."),
        FieldProperty("title", "The link title."),
        FieldProperty("element", "The rendered anchor element.")
    )
    private val DATE = listOf(
        FieldProperty("timestamp", "The Unix timestamp."),
        FieldProperty("iso", "The ISO-8601 string."),
        FieldProperty("day", "The day of the month."),
        FieldProperty("month", "The month number."),
        FieldProperty("year", "The year.")
    )

    /** Curated properties for an augmenting fieldtype; empty for types without dotted augmentation. */
    fun forType(type: String): List<FieldProperty> = when (type.lowercase()) {
        "assets", "asset" -> ASSETS
        "entries", "entry" -> ENTRIES
        "users", "user" -> USERS
        "terms", "term", "taxonomy" -> TERMS
        "link" -> LINK
        "date" -> DATE
        else -> emptyList()
    }
}
