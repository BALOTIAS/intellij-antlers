# Antlers Partials: `views/partials/` Convention — Design

**Date:** 2026-06-03
**Branch:** `antlers-partials-folder`
**Status:** Approved approach, pending spec review

Feedback items #2 (`{{ partial:‹caret› }}` lists no partials) and #8 (Cmd+click on `partial:btn` doesn't
navigate) — both because the resolver only looks at the views **root**. Add support for a
`resources/views/partials/` subfolder: a partial referenced by short name (`{{ partial:btn }}`) resolves
to `views/partials/btn` as well as `views/btn`, with **`partials/` taking precedence** when both exist.

## Background (current state)

- `StatamicProject.viewsRoot(element)` finds `resources/views` (walk-up, or an ancestor with a
  `resources/views` child).
- `AntlersPartialReference.resolve()` resolves `path` against `viewsRoot` directly:
  `viewsRoot.findFileByRelativePath("$path.antlers.html" | "$path.html")`.
- `StatamicProject.listPartials(element)` recursively lists every `*.antlers.html`/`*.html` under
  `viewsRoot`, returning each path **relative to viewsRoot** (so a file at `views/partials/btn` is listed
  as `partials/btn`, and `{{ partial:btn }}` — short name — neither lists nor resolves).
- `AntlersPartialReference.getVariants()` = `listPartials(...)`.

Both partial features already work for the standard `views/` layout (verified); they simply don't follow
a `partials/` subfolder when partials are referenced by short name.

## Components

### 1. `StatamicProject.resolvePartial(element, path): VirtualFile?` (new)

Centralizes partial-path → file resolution with `partials/` precedence:

```kotlin
fun resolvePartial(element: PsiElement, path: String): VirtualFile? {
    val root = viewsRoot(element) ?: return null
    val exts = listOf("antlers.html", "html")
    for (ext in exts) root.findFileByRelativePath("partials/$path.$ext")?.let { return it }  // partials/ wins
    for (ext in exts) root.findFileByRelativePath("$path.$ext")?.let { return it }
    return null
}
```

(All `partials/` candidates are tried before any views-root candidate, so `partials/` wins regardless of
extension.)

### 2. `AntlersPartialReference.resolve()` uses the helper

Replace the inline `viewsRoot`/`findFileByRelativePath` loop with
`StatamicProject.resolvePartial(element, path)?.let { PsiManager.getInstance(element.project).findFile(it) }`.
This is what gives Cmd+click / go-to-declaration the `partials/` fallback (#8).

### 3. `StatamicProject.listPartials` offers `partials/` files by short name (#2)

When collecting, compute each partial's offered name as: if the file is under the `views/partials/`
subtree, the path **relative to `views/partials/`** (`btn`, `blog/card`); otherwise the path relative to
`viewsRoot` (`page`, `blog/card`). De-duplicate (a `LinkedHashSet`), so a name present in both `views/`
and `views/partials/` appears once. Concretely: keep the recursive walk, but when the relative-to-root
path starts with `partials/`, strip that prefix for the offered name.

> Effect: a partial at `views/partials/btn` is offered as `btn` (matching how it's referenced and now
> resolved), not `partials/btn`. The full `partials/btn` form still **resolves** if typed (resolvePartial
> tries `partials/partials/btn` → no, then `partials/btn` → yes); it's just not separately suggested.

## Architecture

Two `StatamicProject` changes (a new `resolvePartial`; a small tweak to `listPartials`'s offered names)
and a one-line switch in `AntlersPartialReference.resolve()`. No grammar/catalog/parser change. The
reference's rename/find-usages/bind logic is unchanged (it operates on the typed text, not the resolved
location).

## Testing (`BasePlatformTestCase`, `resources/views/...` fixtures)

Extend `AntlersPartialReferenceTest` / `AntlersPartialReferenceTest`-style and `StatamicProject` coverage:

- **Resolve from partials/ by short name:** `views/partials/btn.antlers.html` present →
  `{{ partial:b<caret>tn }}` resolves to `btn.antlers.html` (cmd+click / `findReferenceAt(...).resolve()`).
- **Precedence:** with **both** `views/btn.antlers.html` and `views/partials/btn.antlers.html`,
  `{{ partial:btn }}` resolves to the one under `partials/` (assert the resolved file's parent dir is
  `partials`).
- **Root fallback still works:** only `views/btn.antlers.html` → `{{ partial:btn }}` resolves to it.
- **Listing short name:** with `views/partials/btn` present, `{{ partial:<caret> }}` /
  `{{ partial:src="<caret>" }}` completion offers `btn` (not `partials/btn`).
- **Regression:** existing `views/blog/card` resolution + `src=` form + non-partial negative stay green.
- Full-suite gate.

## Out of scope

- Arbitrary/configurable partial directories (only the `partials/` convention) — YAGNI.
- Statamic view-namespace/addon partial paths.
- Changing rename/find-usages behavior.
