package com.github.balotias.intellijantlers.completion

/**
 * Sensible right-hand-side values for a tag query condition `field:operator="value"`. Driven by the
 * operator (truthy operators take true/false; date operators take date keywords) and a few well-known
 * fields (`status`). Returns (value, typeText) pairs; empty when nothing useful can be suggested.
 */
object AntlersConditionValues {

    private val BOOLEAN = listOf("true" to "Boolean", "false" to "Boolean")
    private val DATES = listOf("now" to "Date", "today" to "Date", "tomorrow" to "Date", "yesterday" to "Date")
    private val STATUS = listOf("published" to "Status", "draft" to "Status", "scheduled" to "Status")

    /** Operators that test a boolean/existence condition — the value is true/false. */
    private val BOOLEAN_OPS = setOf(
        "exists", "isset", "doesnt_exist", "is_empty", "is_blank", "not_set", "isnt_set", "null",
        "is_alpha", "is_alpha_numeric", "is_numeric", "is_url", "is_embeddable", "is_email", "is_numberwang",
    )

    /** Operators that compare against a date. */
    private val DATE_OPS = setOf("is_after", "is_future", "is_before", "is_past")

    fun valuesFor(field: String, operator: String): List<Pair<String, String>> = when {
        operator in BOOLEAN_OPS -> BOOLEAN
        operator in DATE_OPS -> DATES
        field == "status" -> STATUS
        else -> emptyList()
    }
}
