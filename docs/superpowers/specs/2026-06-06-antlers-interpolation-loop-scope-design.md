# Loop-Scope-Aware Completion Inside Interpolation — Design

**Date:** 2026-06-06
**Branch:** `antlers-interpolation-loop-scope`
**Status:** Approved approach, pending spec review

## Goal

Make blueprint field completion / go-to-def / hover inside string interpolation (`"{ … }"`) resolve against
the **enclosing `{{ collection }}` loop scope**, not just global/page scope — closing the documented
limitation of the just-shipped interpolation-injection feature.

## Background

Interpolation is now real PSI via Antlers self-injection: each `{ … }` span is an injected
`{{ <expr> }}` fragment (see `2026-06-06-antlers-interpolation-injection`). But the injected fragment is
its own tiny file, so the scope resolver — which reads `element.containingFile` at
`element.textRange.startOffset` — sees a file with no enclosing loop and falls back to global/page scope.

## Approach (chosen)

**Normalize the element to its injection host before computing scope.** An element inside an
interpolation fragment maps via `InjectedLanguageManager.getInjectionHost` to the `AntlersStringLeaf` in
the **outer** file, whose offset is inside the enclosing `{{ collection }}…{{ /collection }}`. The
existing scope machinery then computes the loop scope from there, unchanged.

Rejected: making each caller (completion/doc/etc.) injection-aware (scattered); threading host context
through injection metadata (over-engineered). The single-normalization approach reuses all existing scope
logic (loops, aliases, nesting, hint, page mapping) for free.

## Components

### A. `AntlersScopeResolver.hostOrSelf` (new helper)

```kotlin
/** The injection host in the outer file for an injected element, else the element itself. */
fun hostOrSelf(element: PsiElement): PsiElement =
    InjectedLanguageManager.getInstance(element.project).getInjectionHost(element) ?: element
```

### B. `AntlersScopeResolver.scopesAt` — resolve against the host

At the top of `scopesAt`, replace `element` with `hostOrSelf(element)` for deriving `file` and `caret`:
```kotlin
fun scopesAt(element: PsiElement): List<BlueprintScope> {
    val target = hostOrSelf(element)
    val file = target.containingFile ?: return emptyList()
    val caret = target.textRange.startOffset
    … (memoize on the host file + host caret; computeScopesAt(file, caret, target.project) unchanged) …
}
```
For a non-injected element `target == element`, so behavior is identical to today. For an injected
element, `file`/`caret` are the host string's, so the enclosing-loop scope is computed in the outer file.

### C. `AntlersFieldContext.namespacesFor` — normalize all three precedence levels

```kotlin
fun namespacesFor(element: PsiElement): List<BlueprintNamespace>? {
    val target = AntlersScopeResolver.hostOrSelf(element)
    val scopes = AntlersScopeResolver.scopesAt(target)
    if (scopes.isNotEmpty()) return scopes.map { it.namespace }
    val hints = target.containingFile?.let { AntlersViewHints.declaredNamespaces(it) }
    if (!hints.isNullOrEmpty()) return hints
    val page = PageBlueprintResolver.namespacesFor(target)
    if (page.isNotEmpty()) return page
    return null
}
```
So loop scope, the front-matter `@collection` hint, and the page-blueprint mapping all resolve from the
outer host file. `fieldsInScope` / `resolveField` call `namespacesFor`, so completion, go-to-def, and
hover inside interpolation all inherit loop scope.

## Data flow

injected field element → `getInjectionHost` → `AntlersStringLeaf` in the outer file → existing
`scopesAt`/`namespacesFor` compute the enclosing-loop blueprint → loop-scoped fields offered/resolved.
Interpolation now behaves exactly like a plain `{{ }}` at the same position.

## Error handling / edge cases

- **Interpolation not inside a loop** → host `scopesAt` empty → falls through to hint/page/global as
  today (no regression).
- **Nested loops / `as`-aliases** → handled by the existing resolver from the host offset.
- **Non-injected elements** → `getInjectionHost` returns null → `hostOrSelf` returns the element →
  behavior unchanged (the common case; verified by the existing scope suite staying green).
- **Deeply nested interpolation** (a string inside the injected expression that itself interpolates) →
  `getInjectionHost` returns the immediate (one-level) host; the common single level is covered.
- **Performance** → `getInjectionHost` is a cheap check; for non-injected elements (the hot path) it
  returns null immediately. Memoization keys off the host file + host offset.

## Testing (at the resolver level — avoids the flaky completion harness)

- **Loop scope crosses the injection:** a `{{ collection:blog }}\n…\"{…}\"…\n{{ /collection }}` template
  with a `blog` blueprint (field `hero_title`); get an element inside the injected interpolation fragment
  (via `InjectedLanguageManager.enumerate` → the fragment's Antlers root); `AntlersFieldContext.fieldsInScope(el, project)`
  returns `["hero_title"]` (blog's field), NOT the global set.
- **Negative:** a field that exists only in a *different* collection's blueprint is not offered inside the
  `blog` loop's interpolation.
- **No loop → global:** the same `"{…}"` interpolation at top level (no enclosing loop) →
  `fieldsInScope` is null / global (unchanged).
- **No regression:** the existing `AntlersScopeResolver` / `AntlersFieldContext` / completion / docs suites
  stay green (non-injected scoping unchanged); full-suite gate.

## Out of scope

- Anything beyond loop scope (aliases/nesting/page mapping already inherited).
- Fixing the completion-harness flakiness (test at the resolver level instead).
- Multi-level injection-host chaining.
