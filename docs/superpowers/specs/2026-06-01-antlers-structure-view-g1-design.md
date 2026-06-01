# Antlers Structure View G1 — Outline + Shared Nesting Builder — Design

**Date:** 2026-06-01
**Status:** Approved
**Scope:** Sub-project **G1** of "G (Formatter + Structure view)". Add a Structure-view outline of paired
tags / conditions / partials, and extract the tag-nesting stack walk — currently duplicated in the
folding builder and balance annotator — into one shared `AntlersNestingTreeBuilder` that all three
consume. G2 (the formatter) is a separate follow-up.

## 1. Background & Goal

A finished language plugin has a **Structure tool window** (and ⌘/Ctrl-F12 popup) outlining the file. The
Antlers plugin has none. The nesting it would show — paired tags and conditions — is the exact tree that
`AntlersFoldingBuilder` and `AntlersBalanceAnnotator` already reconstruct via a **copy-pasted** stack
walk.

G1 extracts that walk into one `AntlersNestingTreeBuilder`, refactors folding and balance onto it
(removing the duplication), and adds a Structure view as a thin third consumer. Outcome: a click-to-
navigate outline of tags/conditions/partials, and one source of truth for tag nesting.

## 2. Principles

1. **One nesting algorithm.** Extract the stack walk once; folding, balance, and structure all consume it.
2. **Behavior-preserving refactor.** The builder reproduces the current walk's exact semantics; folding
   and balance output is unchanged (their tests are the guard).
3. **Meaningful outline, not noise.** v1 shows paired constructs + partials; plain variables and
   comment/php blocks are excluded (addable later).
4. **No new icons.** Reuse platform `AllIcons`.
5. **Tolerant.** Mirrors folding's `?: continue`; unclosed/malformed nodes appear gracefully, never throw.

## 3. Components

### 3.1 Shared nesting builder (`scope/AntlersNestingTreeBuilder.kt`)

```kotlin
data class NestingNode(
    val opener: AntlersStatement,                 // {{ collection }} / {{ if }}
    val name: String,                             // "collection" / "if" (closer-match key)
    val closer: AntlersStatement?,                // {{ /collection }} / {{ endif }}; null = unclosed
    val children: List<NestingNode>
)
data class NestingTree(
    val roots: List<NestingNode>,                 // top-level paired constructs, nested
    val unmatchedClosers: List<AntlersStatement>  // {{ /x }} / {{ endif }} with no opener
)

object AntlersNestingTreeBuilder {
    fun build(root: PsiElement, project: Project): NestingTree
}
```

`build` replays the file's `AntlersStatement`s (document order) through a stack, exactly as the folding
builder does:
- **Opener pushed** when it's a known pair construct: a condition keyword in `{if, unless}`, or a tag head
  with `catalog.tag(head)?.isPair == true` (project default → no catalog → push nothing). Each frame
  records the opener statement, its name, and a mutable child list.
- **Closer** (`stmt.closingTag`'s `closedName.substringBefore(':')`, or a condition closer
  `endif`→`if`/`endunless`→`unless`): find the nearest matching opener (`indexOfLast`); if found, set its
  `closer`, attach it as a child of the new top-of-stack (or to `roots` if none), and drop inner unmatched
  frames; if not found, add the statement to `unmatchedClosers`.
- **End of walk:** every frame still on the stack becomes a node with `closer == null`, attached to its
  parent (or `roots`).

Nesting is captured by attaching a completed node to the enclosing frame's child list. Tolerant: a
statement whose `namePath`/`closingTag`/`condition` is null is skipped (`?: continue`).

### 3.2 Folding builder refactor (`editor/AntlersFoldingBuilder.kt`)

Replace the inline paired-tag/condition stack walk (the `ArrayDeque` block) with: `val tree =
AntlersNestingTreeBuilder.build(root, root.project)`; then recursively, for every `NestingNode` with a
non-null `closer`, emit `FoldingDescriptor(opener.node, TextRange(opener.start, closer.end))`. The
**single-node folds** (comments via the token pairing, noparse, php) and `getPlaceholderText`/
`isCollapsedByDefault` are **unchanged**. Net behavior identical; the duplicated walk is gone.

### 3.3 Balance annotator refactor (`editor/AntlersBalanceAnnotator.kt`)

Replace its stack walk with `AntlersNestingTreeBuilder.build`. Then: each `unmatchedClosers` entry whose
name is a known construct (`if`/`unless` or catalog `isPair`) → the existing stray-closer **ERROR**; each
node (recursively) with `closer == null` → the existing unclosed-opener **WARNING**. Messages and ranges
unchanged. (The `unmatchedClosers` already represent only would-be-known closers since unknown closers
that found no opener are added there regardless — the annotator keeps the known-construct filter when
emitting, exactly as today, so unknown closers stay unflagged.)

### 3.4 Structure view (`structure/`)

- **`AntlersStructureViewFactory.kt`** (`PsiStructureViewFactory`): `getStructureViewBuilder(psiFile)` →
  `object : TreeBasedStructureViewBuilder() { override fun createStructureViewModel(editor) =
  AntlersStructureViewModel(psiFile) }`. Registered `<lang.psiStructureViewFactory language="Antlers">`.
- **`AntlersStructureViewModel.kt`** (extends `StructureViewModelBase`, implements
  `StructureViewModel.ElementInfoProvider`): root element wraps the file and exposes top-level structure
  elements; `isAlwaysLeaf(node)` = node has no children, `isAlwaysShowsPlus(node)` = node has children.
- **`AntlersStructureViewElement.kt`** (`StructureViewTreeElement`, `NavigationItem`): wraps either a
  `NestingNode` or a standalone partial `AntlersStatement`.
  - `getValue()` = the opener `AntlersStatement` (a `NavigatablePsiElement` → platform handles jump-to).
  - `getChildren()` = child `NestingNode`s' elements (file-root element returns the top-level nodes +
    partial leaves).
  - `getPresentation()` = `PresentationData(label, null, icon, null)`.

**Shown nodes** (built from the file root):
- Every `NestingTree.roots` node (recursively), labelled by the opener's name path
  (`AntlersNamePathMixin.pathText` for tags, `condition.keyword` for conditions), icon `AllIcons.Nodes.Tag`
  for tags / `AllIcons.Nodes.Lambda` for conditions.
- **Partial leaves:** ALL `AntlersStatement`s in the file whose `namePath.head == "partial"`, shown as
  flat leaf nodes at the root level (alongside the top-level paired-construct nodes), in document order.
  Labelled `partial: <path>` (path via the same extraction `AntlersPartialReferenceHelper` uses —
  method/segments or the `src=` string), icon `AllIcons.Nodes.Include`. v1 shows partials flat (a useful
  "list of includes"); nesting a partial under its enclosing tag is a documented follow-up.
- **Excluded:** plain variable statements, comment/noparse/php blocks.

Icons via `com.intellij.icons.AllIcons` — no new SVGs.

### 3.5 Registration (`resources/META-INF/plugin.xml`)

```xml
<lang.psiStructureViewFactory language="Antlers"
    implementationClass="com.github.balotias.intellijantlers.structure.AntlersStructureViewFactory"/>
```

## 4. File / Package Layout

```
scope/AntlersNestingTreeBuilder.kt           (new — shared stack walk + NestingTree/NestingNode)
editor/AntlersFoldingBuilder.kt              (refactor: use the builder for paired folds)
editor/AntlersBalanceAnnotator.kt            (refactor: use the builder for balance)
structure/AntlersStructureViewFactory.kt     (new)
structure/AntlersStructureViewModel.kt       (new)
structure/AntlersStructureViewElement.kt     (new)
resources/META-INF/plugin.xml                (+lang.psiStructureViewFactory)
```

No grammar/lexer changes.

## 5. Testing

`BasePlatformTestCase`, asserting on the builder + model directly (more robust than the `testStructureView`
harness).

- **Nesting builder** (`AntlersNestingTreeBuilderTest`): `{{ collection:blog }}{{ if x }}{{ /if }}{{ /collection }}`
  → one root `collection` with one child `if`, both `closer != null`; `{{ /collection }}` alone → empty
  roots, one `unmatchedClosers`; `{{ collection }}` alone → one root, `closer == null`; nested same-name
  matches innermost-first.
- **Structure model** (`AntlersStructureViewTest`): build `AntlersStructureViewModel(file)`, walk
  `root.children`; assert top-level labels + nesting (`collection:blog` node has child `if`); a
  `{{ partial:src="blog/card" }}` appears as a `partial: blog/card` element; a plain `{{ title }}` does
  **not** appear; a node's `getValue()` is its opener statement.
- **Folding regression** (`AntlersFoldingTest`, existing): green — identical fold count/ranges.
- **Balance regression** (`AntlersBalanceAnnotatorTest`, existing): green — identical ERROR/WARNING set.
- **Full suite** green.

## 6. Out of Scope (G2 / follow-ups)

- The formatter (G2 — safe in-delimiter spacing normalization).
- Showing comment/noparse/php blocks and plain variables in the outline.
- Custom Antlers SVG icons; structure-view sorters/filters; nesting partials under their enclosing tag.

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Refactor regresses folding or balance | Their existing tests guard it; builder reproduces the walk's exact semantics; single-node comment/php folds stay untouched |
| Outline too noisy | v1 shows only paired constructs + partials; variables and comment/php excluded |
| Unclosed/malformed nodes mid-edit | Builder tolerant (`?: continue`); unclosed nodes appear with `closer == null`, never crash |
| Platform-icon choices imperfect | Cosmetic; `AllIcons` chosen where obvious; refinable; no SVG authoring |
| New shared dependency for 3 consumers | One focused object + a small data model; each consumer is a thin adapter; all three test suites cover it |
| Structure navigation | `getValue()` returns the opener `AntlersStatement` (navigatable) → platform handles jump-to |
