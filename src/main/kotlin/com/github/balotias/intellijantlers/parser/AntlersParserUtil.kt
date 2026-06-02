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
}
