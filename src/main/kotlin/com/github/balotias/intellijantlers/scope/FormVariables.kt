package com.github.balotias.intellijantlers.scope

/** A runtime variable available inside a `{{ form:… }}` block. */
data class FormVariable(val name: String, val description: String)

object FormVariables {
    val ALL: List<FormVariable> = listOf(
        FormVariable("fields", "Array of the form's fields, for dynamic rendering."),
        FormVariable("errors", "Indexed array of validation error messages after submission."),
        FormVariable("error", "Validation errors indexed by field handle."),
        FormVariable("old", "Submitted values from the previous request, to re-populate fields."),
        FormVariable("success", "Success message — truthy after a successful submission."),
        FormVariable("submission_created", "Boolean success (falsey when the honeypot is filled)."),
    )
}
