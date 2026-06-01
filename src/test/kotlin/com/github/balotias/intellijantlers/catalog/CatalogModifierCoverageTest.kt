package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogModifierCoverageTest {

    // The Statamic 6 core modifier set (spec Appendix B). The catalog must cover all of these.
    private val expected = setOf(
        "add", "add_slashes", "ampersand_list", "antlers", "ascii", "as", "at", "attribute",
        "background_position", "backspace", "bard_html", "bard_items", "bard_text", "bool_string",
        "camelize", "cdata", "ceil", "chunk", "classes", "collapse", "collapse_whitespace", "compact",
        "contains", "contains_all", "contains_any", "console_log", "count", "count_substring", "dashify",
        "days_ago", "decode", "deslugify", "dl", "doesnt_overlap", "divide", "dump", "embed_url",
        "ensure_left", "ensure_right", "entities", "excerpt", "explode", "favicon", "filter_empty",
        "first", "flatten", "flip", "floor", "format", "format_number", "format_translated", "full_urls",
        "get", "gravatar", "group_by", "headline", "hex_to_rgb", "hours_ago", "image", "in_array",
        "insert", "is_after", "is_alpha", "is_alphanumeric", "is_array", "is_before", "is_between",
        "is_blank", "is_email", "is_embeddable", "is_external_url", "is_future", "is_json", "is_leap_year",
        "is_lowercase", "is_numberwang", "is_numeric", "is_past", "is_today", "is_tomorrow", "is_uppercase",
        "is_url", "is_weekday", "is_weekend", "is_yesterday", "iso_format", "join", "kebab", "key_by",
        "keys", "last", "lcfirst", "length", "limit", "link", "list", "lower", "macro", "mark", "markdown",
        "md5", "minutes_ago", "mod", "modify_date", "months_ago", "multiply", "nl2br", "obfuscate",
        "obfuscate_email", "offset", "ol", "option_list", "output", "overlaps", "pad", "parse_url",
        "partial", "pathinfo", "piped", "pluck", "plural", "random", "raw", "rawurlencode",
        "rawurlencode_except_slashes", "ray", "read_time", "regex_mark", "regex_replace", "relative",
        "remove_left", "remove_right", "repeat", "replace", "resolve", "reverse", "round", "safe_truncate",
        "sanitize", "scope", "seconds_ago", "segment", "select", "sentence_list", "shuffle", "singular",
        "slugify", "smartypants", "snake", "sort", "spaceless", "split", "str_pad_left", "strip_tags",
        "studly", "substr", "subtract", "sum", "surround", "swap_case", "table", "tidy", "timezone",
        "title", "to_json", "to_qs", "to_spaces", "to_tabs", "trim", "truncate", "ucfirst", "ul",
        "underscored", "unique", "upper", "url", "urldecode", "urlencode", "urlencode_except_slashes",
        "values", "weeks_ago", "where", "where-in", "widont", "word_count", "wrap", "years_ago"
    )

    @Test fun coversAllCoreModifiers() {
        val have = CatalogModifiers.ALL.map { it.name }.toSet()
        val missing = expected - have
        assertTrue("missing modifiers: $missing", missing.isEmpty())
    }

    @Test fun noDuplicatesAndDescribed() {
        val names = CatalogModifiers.ALL.map { it.name }
        assertTrue("duplicate modifier names", names.size == names.toSet().size)
        assertTrue("every modifier has a description",
            CatalogModifiers.ALL.all { it.description.isNotBlank() })
    }

    @Test fun docUrlsWellFormed() {
        assertTrue(CatalogModifiers.ALL.all { it.docUrl.startsWith("https://statamic.dev/") })
    }
}
