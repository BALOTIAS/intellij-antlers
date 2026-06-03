# Antlers Completion Gaps — Design

**Date:** 2026-06-03
**Branch:** `antlers-completion-gaps`
**Status:** Approved approach, pending spec review

Two completion gaps from the feedback batch (sub-project #3):

- **#1** `{{ collection:‹caret› }}` autocompletes only the tag *methods* (`count`, `next`, …), not the
  collection **handles** (`blog`, `news`, …) used by the colon shorthand. Same for `taxonomy:`, `form:`,
  `nav:`.
- **#6** Inside a `{{ form:X }}…{{ /form:X }}` block, the form tag's runtime variables (`fields`,
  `errors`, `error`, `old`, `success`, `submission_created`) are not offered.

## Background (current state)

- The `TAG_METHOD` completion arm offers `catalog.tag(head).methods` for a colon after a known tag head;
  it does not offer handles. (`partial:` is special-cased elsewhere via the partial reference's variants
  and already lists partials.)
- `StatamicProject` already lists handles for the param-value source: `listCollectionHandles`,
  `listTaxonomyHandles` (subdirectories of `resources/blueprints/{collections,taxonomies}`). There is no
  form/nav handle lister yet. Form/nav blueprints live at `resources/blueprints/{forms,navigation}/<handle>.yaml`
  (per `BlueprintNamespace`).
- The TAG_NAME (variable) arm offers `LoopVariables` for any iterating scope and `NavVariables` for nav
  scopes (`scopes.any { it.navMeta }`). `AntlersScopeResolver` creates a `BlueprintScope` with
  `namespace.kind == FORM` inside `{{ form:X }}`. There is no `FormVariables`.

## Components

### 1. Handle listers — `StatamicProject`

Add a `blueprintFiles(element, kind)` helper (mirrors the existing `blueprintSubdirs`, but lists
`*.yaml` **file** basenames) and two public listers:

```kotlin
fun listFormHandles(element: PsiElement): List<String> = blueprintFiles(element, "forms")
fun listNavHandles(element: PsiElement): List<String> = blueprintFiles(element, "navigation")

private fun blueprintFiles(element: PsiElement, kind: String): List<String> {
    val resources = viewsRoot(element)?.parent ?: return emptyList()
    val dir = resources.findChild("blueprints")?.findChild(kind) ?: return emptyList()
    return dir.children.filter { !it.isDirectory && it.name.endsWith(".yaml") }
        .map { it.name.removeSuffix(".yaml") }
}
```

(Graceful empty list when the directory is absent.)

### 2. Offer handles in the colon-method arm — `AntlersCompletionProvider` (TAG_METHOD)

When the colon follows a shorthand-handle tag head, offer that tag's handles **in addition to** the
catalog methods (both `{{ collection:blog }}` and `{{ collection:next }}` are valid). Map head → lister:

| head | handles | type text |
|---|---|---|
| `collection` | `listCollectionHandles` | "Collection" |
| `taxonomy` | `listTaxonomyHandles` | "Taxonomy" |
| `form` | `listFormHandles` | "Form" |
| `nav` | `listNavHandles` | "Nav" |

Concretely, after the existing `tag.methods.forEach { … }`, add a `when (info.tagHead)` that adds the
handle lookups (`LookupElementBuilder.create(handle).withIcon(...).withTypeText(<type>)`). Methods keep
their `"Method"` type text. `partial` is untouched (its reference already lists partials).

### 3. `FormVariables` + offer them in a form scope (#6)

Add `scope/FormVariables.kt` mirroring `NavVariables`:

```kotlin
data class FormVariable(val name: String, val description: String)
object FormVariables {
    val ALL = listOf(
        FormVariable("fields", "Array of the form's fields, for dynamic rendering."),
        FormVariable("errors", "Indexed array of validation error messages after submission."),
        FormVariable("error", "Validation errors indexed by field handle."),
        FormVariable("old", "Submitted values from the previous request, to re-populate fields."),
        FormVariable("success", "Success message — truthy after a successful submission."),
        FormVariable("submission_created", "Boolean success (falsey when the honeypot is filled)."),
    )
}
```

In the TAG_NAME arm, after the nav block, add (using the existing `seen` de-dup set and `scopes`):

```kotlin
if (scopes.any { it.namespace.kind == BlueprintNamespace.Kind.FORM }) {
    for (fv in FormVariables.ALL) if (seen.add(fv.name)) {
        result.addElement(LookupElementBuilder.create(fv.name).withIcon(AntlersIcons.FILE)
            .withTypeText("Form").withTailText("  ${fv.description}", true))
    }
}
```

## Architecture

Additive: two `StatamicProject` listers, a `when` block in the TAG_METHOD arm, a new `FormVariables`
object, and a form-scope block in the TAG_NAME arm. No parser/grammar change. Reuses the existing scope
resolver, blueprint-dir conventions, and `seen` de-dup.

## Testing (`BasePlatformTestCase`, `resources/blueprints/...` fixtures)

- **A1 collection handles:** with `resources/blueprints/collections/blog/blog.yaml` present,
  `{{ collection:<caret> }}` completion contains `blog`; still contains a method (`count`).
- **A2 form handles:** with `resources/blueprints/forms/contact.yaml`, `{{ form:<caret> }}` contains
  `contact`.
- **A3 taxonomy handles:** with `resources/blueprints/taxonomies/tags/…`, `{{ taxonomy:<caret> }}`
  contains `tags`.
- **B form vars:** `{{ form:contact }}{{ <caret> }}{{ /form:contact }}` completion contains `success`,
  `errors`, and `fields`; a non-form scope (`{{ collection:blog }}{{ <caret> }}{{ /collection }}`) does
  **not** offer `success` (regression guard for scope correctness).
- Full-suite gate.

## Out of scope

- `foreach`/`section`/`dictionary` colon handles (different, non-blueprint sources) — not requested.
- Nav handles sourced from `content/navigation/` (we use the blueprint dir; graceful no-op otherwise).
- `show_field`/`honeypot`/`js_driver` form vars (driver-specific) — kept to the common documented set.
