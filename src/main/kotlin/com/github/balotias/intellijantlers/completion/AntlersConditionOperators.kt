package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Statamic "tag conditions": query filters written as `field:operator="value"` parameters on the
 * collection/taxonomy/users tags (e.g. `{{ collection:blog title:contains="tao" }}`). The operator names
 * are verified against statamic/cms `Tags/Concerns/QueriesConditions.php`.
 */
object AntlersConditionOperators {

    /** Tags whose parameters support `field:operator=` query conditions. */
    val CONDITION_TAGS = setOf("collection", "taxonomy", "users")

    /** Primary operators offered in completion, name → short description (also used for hover docs). */
    val PRIMARY: Map<String, String> = linkedMapOf(
        "is" to "Field equals the value.",
        "not" to "Field does not equal the value.",
        "contains" to "Field contains the substring.",
        "doesnt_contain" to "Field does not contain the substring.",
        "in" to "Field is one of the pipe-separated values.",
        "not_in" to "Field is none of the pipe-separated values.",
        "starts_with" to "Field begins with the value.",
        "doesnt_start_with" to "Field does not begin with the value.",
        "ends_with" to "Field ends with the value.",
        "doesnt_end_with" to "Field does not end with the value.",
        "exists" to "Field is set / not empty.",
        "doesnt_exist" to "Field is empty / not set.",
        "is_empty" to "Field is empty.",
        "gt" to "Field is greater than the value.",
        "gte" to "Field is greater than or equal to the value.",
        "lt" to "Field is less than the value.",
        "lte" to "Field is less than or equal to the value.",
        "matches" to "Field matches the regular expression.",
        "doesnt_match" to "Field does not match the regular expression.",
        "is_after" to "Date field is after the value.",
        "is_before" to "Date field is before the value.",
        "is_alpha" to "Field contains only letters.",
        "is_numeric" to "Field is numeric.",
        "is_alpha_numeric" to "Field is alphanumeric.",
        "is_url" to "Field is a URL.",
        "is_email" to "Field is an email address.",
        "is_embeddable" to "Field is an embeddable URL.",
        "overlaps" to "Array field overlaps the pipe-separated values.",
        "doesnt_overlap" to "Array field does not overlap the values.",
    )

    /** Every recognised operator name, including aliases — used for highlighting and hover-doc lookup. */
    val ALL: Set<String> = PRIMARY.keys + setOf(
        "equals", "isnt", "aint", "is_blank", "not_set", "isnt_set", "null", "isset",
        "begins_with", "doesnt_begin_with", "greater_than", "less_than",
        "greater_than_or_equal_to", "less_than_or_equal_to", "match", "regex",
        "is_future", "is_past", "is_numberwang",
    )

    /** Description for [operator], falling back to a generic line for an alias not in [PRIMARY]. */
    fun describe(operator: String): String? =
        if (operator in ALL) PRIMARY[operator] ?: "A tag query condition operator." else null

    /**
     * True when [ident] is the operator name of a `field:operator="value"` query condition: a bound
     * parameter whose name is a known operator, immediately preceded by a floating field ident, on a
     * condition-capable tag. Distinguishes it from a genuine bound parameter like `:src=`.
     */
    fun isConditionOperatorIdent(ident: PsiElement): Boolean {
        if (ident.node?.elementType != AntlersTypes.T_IDENT || ident.text !in ALL) return false
        val param = ident.parent as? AntlersParameterMixin ?: return false
        if (param.parameterName != ident.text) return false
        val stmt = param.parent as? AntlersStatement ?: return false
        if (PsiTreeUtil.findChildOfType(stmt, AntlersNamePathMixin::class.java)?.head !in CONDITION_TAGS) return false
        var sib = param.prevSibling
        while (sib != null && sib.text.isBlank()) sib = sib.prevSibling
        return sib?.node?.elementType == AntlersTypes.T_IDENT && sib.parent is AntlersStatement
    }

    /**
     * If [fieldIdent] is the field (left-hand side) of a `field:operator="value"` condition, the blueprint
     * namespace of the tag being queried (`collection:blog` → the `blog` collection, etc.); else null.
     * Lets references resolve a condition field to its declaration without a grammar change.
     */
    fun conditionFieldNamespace(fieldIdent: PsiElement): BlueprintNamespace? {
        if (fieldIdent.node?.elementType != AntlersTypes.T_IDENT) return null
        val stmt = fieldIdent.parent as? AntlersStatement ?: return null
        var sib = fieldIdent.nextSibling
        while (sib != null && sib.text.isBlank()) sib = sib.nextSibling
        val param = sib as? AntlersParameterMixin ?: return null
        val opIdent = param.node.findChildByType(AntlersTypes.T_IDENT)?.psi ?: return null
        if (!isConditionOperatorIdent(opIdent)) return null
        val namePath = PsiTreeUtil.findChildOfType(stmt, AntlersNamePathMixin::class.java) ?: return null
        return when (namePath.head) {
            "collection" -> namePath.method?.let { BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, it) }
            "taxonomy" -> namePath.method?.let { BlueprintNamespace(BlueprintNamespace.Kind.TAXONOMY, it) }
            "users" -> BlueprintNamespace(BlueprintNamespace.Kind.USER, "user")
            else -> null
        }
    }
}
