package com.github.balotias.intellijantlers.blueprint

/**
 * Which Statamic data namespace a blueprint/fieldset belongs to, derived from its file path.
 * The handle is the collection/taxonomy/form/etc. handle (or "user" for the user singleton).
 */
data class BlueprintNamespace(val kind: Kind, val handle: String) {
    enum class Kind { COLLECTION, TAXONOMY, USER, FORM, ASSET, GLOBAL, FIELDSET, UNKNOWN }

    companion object {
        val UNKNOWN = BlueprintNamespace(Kind.UNKNOWN, "")

        /** Tolerant path → namespace mapping. Never throws; unrecognized layouts yield UNKNOWN. */
        fun fromPath(path: String): BlueprintNamespace {
            val p = path.replace('\\', '/')
            // collections/taxonomies: handle is the DIRECTORY name (a collection may have several files)
            dirAfter(p, "/resources/blueprints/collections/")?.let { return BlueprintNamespace(Kind.COLLECTION, it) }
            dirAfter(p, "/resources/blueprints/taxonomies/")?.let { return BlueprintNamespace(Kind.TAXONOMY, it) }
            // forms/assets/globals/fieldsets: handle is the FILE name (without .yaml)
            fileAfter(p, "/resources/blueprints/forms/")?.let { return BlueprintNamespace(Kind.FORM, it) }
            fileAfter(p, "/resources/blueprints/assets/")?.let { return BlueprintNamespace(Kind.ASSET, it) }
            fileAfter(p, "/resources/blueprints/globals/")?.let { return BlueprintNamespace(Kind.GLOBAL, it) }
            if (p.endsWith("/resources/blueprints/user.yaml")) return BlueprintNamespace(Kind.USER, "user")
            fileAfter(p, "/resources/fieldsets/")?.let { return BlueprintNamespace(Kind.FIELDSET, it) }
            return UNKNOWN
        }

        /** First path segment after [marker], e.g. "blog" in ".../collections/blog/article.yaml". */
        private fun dirAfter(path: String, marker: String): String? {
            val i = path.indexOf(marker)
            if (i < 0) return null
            return path.substring(i + marker.length).substringBefore('/').takeIf { it.isNotBlank() }
        }

        /** File handle after [marker]: the immediate filename without its .yaml extension. */
        private fun fileAfter(path: String, marker: String): String? {
            val i = path.indexOf(marker)
            if (i < 0) return null
            val rest = path.substring(i + marker.length)
            // must be a direct file under the marker dir (no further slash)
            if (rest.contains('/')) return null
            return rest.removeSuffix(".yaml").takeIf { it.isNotBlank() }
        }
    }
}
