package com.github.balotias.intellijantlers.parser

import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase

/**
 * Parser helper methods referenced from Antlers.bnf via the `<<name>>` external-rule syntax.
 */
object AntlersParserUtil : GeneratedParserUtilBase() {

    /** The condition keywords the grammar recognizes; also reused for semantic highlighting. */
    val CONDITION_KEYWORDS = setOf("if", "elseif", "else", "unless", "endif", "endunless")

    /**
     * Non-consuming predicate: true when the current token is a T_IDENT whose text is a
     * condition keyword. Used to gate the `condition` rule so ordinary tags/variables that
     * start with an identifier are not misparsed as conditions.
     */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun atConditionKeyword(builder: PsiBuilder, level: Int): Boolean {
        if (builder.tokenType != AntlersTypes.T_IDENT) return false
        return builder.tokenText in CONDITION_KEYWORDS
    }

    /**
     * Non-consuming predicate: true when the current token directly abuts the previous one (no
     * whitespace between). Gates the name-path `:segment`/`.segment` extension so `collection:events`
     * keeps extending but `collection:events :event_date.start` does NOT — a whitespace-separated `:…`
     * is a bound-parameter condition, not part of the tag's path. (rawLookup does not skip whitespace.)
     */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun adjacent(builder: PsiBuilder, level: Int): Boolean {
        val prev = builder.rawLookup(-1) ?: return false
        return prev != AntlersTypes.T_WS && prev != com.intellij.psi.TokenType.WHITE_SPACE
    }
}
