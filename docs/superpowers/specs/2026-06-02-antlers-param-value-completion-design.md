# Antlers Parameter-Value Completion — Design

**Date:** 2026-06-02
**Branch:** `antlers-grammar-completion`
**Status:** Approved, ready for implementation plan

## Goal

Offer completions for tag-parameter **values** (today the plugin completes parameter *names* but
offers nothing after `=`). Cover the four highest-value sources:

1. `{{ partial:src="<caret>" }}` → available partial template paths.
2. `{{ collection from="<caret>" }}` (and `in`/`folder`/`collection`/`use`/`handle`) → collection &
   taxonomy handles.
3. `{{ collection:blog sort="<caret>" }}` (and `order_by`) → in-scope blueprint field handles +
   `title`/`date`/`random` + `:asc`/`:desc` variants.
4. Known boolean params (`paginate`, `show_unpublished`, `show_future`, `show_past`,
   `disable_paging`) → `true`/`false`.

## Background — current state

- `completion/AntlersCompletionContext.classify(position)` returns
  `AntlersCompletionInfo(kind, tagHead, pathPrefix)`. It keys on the **previous significant leaf**.
  The `T_EQUALS` branch currently returns `NONE` (`AntlersCompletionContext.kt:46`).
- `completion/AntlersCompletionProvider` dispatches on `info.kind`.
- `completion/AntlersCompletionContributor` extends with `psiElement()` (fires anywhere; routing is
  the `classify` gate), so it already reaches inside string literals.
- `references/StatamicProject.viewsRoot(element)` locates `resources/views`. The partial-listing
  logic currently lives inline in `references/AntlersPartialReference.getVariants` /
  `collectPartials`.
- `scope/AntlersFieldContext.fieldsInScope(position, project)` returns the in-scope fields (or null
  → global fallback). `blueprint/BlueprintService.fields()` is the global list.

### Why one `T_EQUALS` branch covers both value positions

When completing inside a quoted value `from="<caret>"`, the platform inserts its dummy identifier
into the existing `T_STRING`, so the caret's `position` element is that `T_STRING`, and its previous
significant leaf is the `T_EQUALS`. When completing unquoted (`from=<caret>`), the dummy becomes a
`T_IDENT` whose previous leaf is also the `T_EQUALS`. So the single `T_EQUALS` branch handles both.
(Conditions use `==` = `T_OP`, never `T_EQUALS`, so there is no collision.)

## Components

### 1. Context detection — `AntlersCompletionContext`

- Add `PARAMETER_VALUE` to `AntlersCompletionKind`.
- Add `paramName: String? = null` to `AntlersCompletionInfo`.
- Replace the `T_EQUALS -> NONE` branch with: find the param name = the nearest preceding `T_IDENT`
  before the `=` within the statement; if that ident is itself preceded by `T_COLON` (a **bound**
  param `:from=…`, whose value is a variable expression) return `NONE`; otherwise return
  `AntlersCompletionInfo(PARAMETER_VALUE, tagHead = headOf(statement), paramName = thatIdent)`.

### 2. Value source — `completion/AntlersParamValueSource` (new)

```
data class ParamValue(val text: String, val typeText: String)

object AntlersParamValueSource {
    fun valuesFor(tagHead: String?, paramName: String?, position: PsiElement, project: Project): List<ParamValue>
}
```

Routing (case-insensitive param match):
- `paramName == "src"` && `tagHead == "partial"` → partials → `ParamValue(path, "Partial")`.
- `paramName ∈ {from, in, folder, collection, use, handle}` → collection handles
  (`"Collection"`) + taxonomy handles (`"Taxonomy"`).
- `paramName ∈ {sort, order_by}` → in-scope field handles (`"Field"`) + `title`/`date`/`random`
  (`"Sort"`), each also offered with `:asc` and `:desc` suffixes (`"Sort"`).
- `paramName ∈ BOOLEAN_PARAMS` → `true`/`false` (`"Boolean"`).
- otherwise → empty list.

`BOOLEAN_PARAMS = setOf("paginate", "show_unpublished", "show_future", "show_past", "disable_paging")`.

### 3. Value listers (DRY — reuse existing data)

- **Partials:** extract the recursion in `AntlersPartialReference.collectPartials`/`getVariants`
  into `StatamicProject.listPartials(element: PsiElement): List<String>` (computes `viewsRoot`,
  recurses, strips `.antlers.html`/`.html`). `AntlersPartialReference.getVariants` is refactored to
  call it (behavior-preserving), and the value source calls it too.
- **Collection / taxonomy handles:** add `StatamicProject.listCollectionHandles(element)` and
  `listTaxonomyHandles(element)` — locate the `resources` root (the parent of `viewsRoot`, or by an
  ancestor walk mirroring `viewsRoot`), then list the subdirectory names under
  `resources/blueprints/collections/` and `resources/blueprints/taxonomies/` respectively
  (collections/taxonomies are directory-named, per `BlueprintNamespace.fromPath`). Directory-based
  so it is complete even for collections whose blueprint declares no fields.
- **Sort fields:** `AntlersFieldContext.fieldsInScope(position, project)?.map { it.handle }`
  `?: BlueprintService.getInstance(project).fields().map { it.handle }`, deduped.
- **Booleans:** static.

### 4. Provider — `AntlersCompletionProvider`

Add a `PARAMETER_VALUE` case:
```
AntlersCompletionKind.PARAMETER_VALUE -> {
    val matched = result.withParamValuePrefix(parameters)   // see prefix note
    for (v in AntlersParamValueSource.valuesFor(info.tagHead, info.paramName, parameters.position, project)) {
        matched.addElement(LookupElementBuilder.create(v.text).withIcon(AntlersIcons.FILE).withTypeText(v.typeText))
    }
}
```

**Prefix nuance (the one real fiddle):** when the caret is inside a quoted string, the default
prefix matcher includes the opening quote, so nothing matches. Compute the inner prefix = the string
text from just after the opening quote up to the caret (with the platform dummy
`CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED` removed) and call
`result.withPrefixMatcher(innerPrefix)`. When unquoted (caret is a `T_IDENT`), the default prefix is
correct; detect the position element type and only override for `T_STRING`.

Insert is plain text (the user is normally already inside `""`); no custom insert handler. Partial
paths with slashes insert verbatim.

## Data flow

```
{{ collection from="bl|" }}  --completion-->
  classify(position=T_STRING, prev=T_EQUALS) -> PARAMETER_VALUE(tagHead="collection", paramName="from")
    AntlersParamValueSource.valuesFor("collection","from",…) -> [blog(Collection), tags(Taxonomy), …]
      provider sets prefix "bl" -> offers "blog"
```

## Edge cases

- Bound params `:from="$var"` → `NONE` (variable-valued; not a literal source). Documented, deferred.
- A param the source doesn't recognize → empty list (no offers), not an error.
- Non-Statamic project (no `resources/blueprints`) → listers return empty; no errors.
- `sort` outside any scope → global field list (fallback), still useful.
- The same `from`/`handle` param name appears on many tags; offering collection+taxonomy handles for
  all of them is harmless (small lists) and avoids brittle per-tag tables. Acceptable v1 imprecision.

## Testing (`BasePlatformTestCase`, runnable via `./gradlew test`)

**Context (`AntlersCompletionContextTest` additions):**
- `from=<caret>` → `PARAMETER_VALUE`, `paramName == "from"`, `tagHead == "collection"`.
- `from="<caret>"` (caret inside the string) → same.
- bound `:from=<caret>` → `NONE`.
- existing `testNoneInValue` (asserts NONE inside `limit="…"`) is **updated/removed** — it now
  becomes a `PARAMETER_VALUE` (limit isn't a recognized source, so the source returns empty, but the
  KIND changes). Adjust that test to reflect the new classification.

**Value source (`AntlersParamValueSourceTest`):**
- partials: a project with `resources/views/blog/card.antlers.html` → `valuesFor("partial","src",…)`
  contains `blog/card`.
- handles: `resources/blueprints/collections/blog/blog.yaml` + `…/taxonomies/topics/topics.yaml` →
  `valuesFor("collection","from",…)` contains `blog` and `topics`.
- sort: a blueprint with field `title` → `valuesFor("collection","sort",…)` contains `title`,
  `title:asc`, `date`.
- boolean: `valuesFor("collection","paginate",…)` == `[true, false]` text-wise.
- unknown: `valuesFor("collection","limit",…)` is empty.

**End-to-end (`AntlersParamValueCompletionTest`):**
- `{{ partial:src="<caret>" }}` → `myFixture.completeBasic()` / `lookupElementStrings` contains a
  partial path.
- `{{ collection from="<caret>" }}` → contains a collection handle.
- `{{ collection:blog sort="<caret>" }}` → contains a field handle and `…:asc`.
- a boolean param → contains `true`/`false`.
- value completion NOT offered where it shouldn't be (e.g. plain `{{ <caret> }}` still offers tags,
  not param values — guards against over-firing).

## Risks

- **Prefix-inside-string:** if the prefix matcher isn't set, completion appears to "not work" inside
  quotes (offers nothing matching). The end-to-end tests guard this.
- **`resources` root location:** reusing the `viewsRoot` walk; if a project nests `resources`
  unusually the listers return empty (no false offers). Acceptable.
- **`AntlersPartialReference.getVariants` refactor** must stay behavior-preserving — the existing
  partial-reference/ completion tests guard it.

## Out of scope

- Bound-param (`:param=`) variable-expression completion.
- Per-tag precise param→source tables (we use a shared param-name map).
- Value completion for arbitrary/addon-tag params not in the four sources.
- `as=`/free-text params (no meaningful value set).
