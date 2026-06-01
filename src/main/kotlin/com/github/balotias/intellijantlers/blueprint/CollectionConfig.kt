package com.github.balotias.intellijantlers.blueprint

/** A Statamic collection's config (`content/collections/<handle>.yaml`): handle + default template. */
data class CollectionConfig(val handle: String, val template: String)
