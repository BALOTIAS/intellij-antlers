# Antlers Diagnostics F — Balance Annotator + Unknown-Modifier Inspection — Design

**Date:** 2026-06-01
**Status:** Approved
**Scope:** Sub-project **F** of the "fully done" roadmap. Surface real Antlers errors/warnings the
platform doesn't already catch: **unbalanced/mismatched paired tags & conditions** (Annotator, the core
net-new check) and **unknown modifiers** (suppressible inspection). Excludes checks the platform already
provides (unclosed delimiters → parser errors; unresolved partials → non-soft reference highlighting) and
the false-positive-prone unknown-tag check.

## 1. Background & Goal

The plugin has lexer/grammar/PSI, completion, docs, navigation, scoping, and a comprehensive catalog
(J1), but no **diagnostics** — the thing that makes a language plugin feel finished. The platform already
flags unclosed delimiters (parser `PsiErrorElement` via `pin=1`) and unresolved partials (the
`AntlersPartialReference` is non-soft, so the IDE highlights a null resolve). What it cannot see is
**semantic balance**: `{{ collection }}` and `{{ /collection }}` parse as two independent valid
statements, so only a stack walk over the file detects an imbalance.

F adds a balance Annotator (reusing the folding builder's proven stack algorithm) and a weak
unknown-modifier inspection. Both judge only **known constructs**, so they don't false-positive on
unknown/addon tags or variables.

## 2. Principles

1. **Reuse the folding stack-walk.** Balance is the folding builder's pairing algorithm, emitting
   problems instead of fold regions.
2. **Only judge known constructs.** Balance considers conditions (`if`/`unless`) and catalog-`isPair`
   tags; unknown tags are ignored → no false positives. Unknown-modifier checks against the catalog +
   project scan.
3. **Don't double-flag.** Unclosed delimiters and unresolved partials are already platform-surfaced — F
   stays out of their way.
4. **Definitive → Annotator (ERROR/WARNING, always on); soft → Inspection (weak, suppressible).**
5. **Tolerant.** Both walk the existing PSI, skip malformed nodes, and never throw.

## 3. Components

### 3.1 Balance annotator (`editor/AntlersBalanceAnnotator.kt`)

Implements `com.intellij.lang.annotation.Annotator`, registered `<annotator language="Antlers">`.

`annotate(element, holder)` runs the full walk **once per file** by gating on the file root:

```kotlin
override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is AntlersFile) return     // AntlersFile = the Antlers PSI file (PsiFileBase)
    // walk all AntlersStatements with a stack, register problems on the holder
}
```

The walk mirrors `AntlersFoldingBuilder` (statements in document order, an `ArrayDeque` stack):
- **Opener pushed** when it is a *known pair construct*: a condition keyword in `CONDITION_OPENERS`
  (`if`,`unless`), or a tag whose head satisfies `catalog.tag(head)?.isPair == true`. (Project default →
  no catalog → push nothing, annotate nothing.) Push `(name, openerStatement)`.
- **Closer** — `stmt.closingTag` (name via `AntlersClosingTagMixin.closedName?.substringBefore(':')`) or a
  condition closer (`endif`→`if`, `endunless`→`unless`): find the nearest matching opener on the stack
  (`indexOfLast`). If found, pop it (and drop inner unmatched openers, as folding does). If **not found**
  → register an **ERROR** on the closer's range: *"Closing `/<name>` has no matching opening tag."*
- **End of walk:** every opener still on the stack is **unclosed** → register a **WARNING** on each
  opener's range: *"`{{ <name> }}` is never closed."*

Tolerant: a statement whose `namePath`/`closingTag`/`condition` is null or malformed is skipped (mirrors
folding's `?: continue`). Cost is one O(statements) pass per file — the folding-builder cost class.

### 3.2 Unknown-modifier inspection (`inspection/AntlersUnknownModifierInspection.kt`)

Extends `com.intellij.codeInspection.LocalInspectionTool`, registered
`<localInspection language="Antlers" … level="WARNING" enabledByDefault="true">`.

```kotlin
override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element !is AntlersModifierMixin) return
            val name = element.modifierName
            if (name.isBlank()) return
            val project = element.project
            if (project.isDefault) return
            val known = AntlersCatalogService.getInstance(project).modifiers().any { it.name == name }
            if (!known) {
                val ident = element.node.findChildByType(AntlersTypes.T_IDENT)?.psi ?: element
                holder.registerProblem(ident, "Unknown modifier '$name'", ProblemHighlightType.WEAK_WARNING)
            }
        }
    }
```

`AntlersCatalogService.modifiers()` already merges the J1 catalog (~190) with project-scanned
`Modifiers/` classes. The problem is registered on the modifier-name ident (`AntlersModifierMixin`'s
`T_IDENT`). Weak-warning + inspection means it is unobtrusive, toggleable in Settings, and suppressible
per line/file.

### 3.3 Registration (`resources/META-INF/plugin.xml`)

Two new entries in the existing `<extensions defaultExtensionNs="com.intellij">` block:

```xml
<annotator language="Antlers"
           implementationClass="com.github.balotias.intellijantlers.editor.AntlersBalanceAnnotator"/>
<localInspection language="Antlers"
                 implementationClass="com.github.balotias.intellijantlers.inspection.AntlersUnknownModifierInspection"
                 shortName="AntlersUnknownModifier" displayName="Unknown Antlers modifier"
                 groupName="Antlers" enabledByDefault="true" level="WARNING"/>
```

## 4. File / Package Layout

```
editor/AntlersBalanceAnnotator.kt                 (new — Annotator)
inspection/AntlersUnknownModifierInspection.kt    (new — LocalInspectionTool)
resources/META-INF/plugin.xml                     (+annotator, +localInspection)
```

Reuses (no change): `AntlersFoldingBuilder` constants pattern, `AntlersFile`, `AntlersStatement`/
`AntlersClosingTagMixin`/`AntlersConditionMixin`/`AntlersNamePathMixin`/`AntlersModifierMixin`,
`AntlersCatalogService`. No grammar/lexer changes.

## 5. Testing

`BasePlatformTestCase` with `myFixture.doHighlighting()` (filtered to F's descriptions, since the HTML
template-data contributes its own highlights — a strict `checkHighlighting()` would over-assert). A
helper collects `HighlightInfo`s whose description starts with our text.

- **Balance — unopened closer:** `{{ /collection }}` → an ERROR on `/collection`; `{{ endif }}` alone →
  ERROR.
- **Balance — unclosed opener:** `{{ if foo }}` alone → a WARNING on the opener; `{{ collection:blog }}`
  alone → WARNING.
- **Balance — balanced:** `{{ if foo }}{{ /if }}`, `{{ collection:blog }}{{ /collection }}`, and nested
  `{{ collection:blog }}{{ if x }}{{ /if }}{{ /collection }}` → **none** of F's diagnostics.
- **Balance — unknown construct ignored:** `{{ /unknownaddon }}` → no F diagnostic (head not a known
  pair construct); `{{ unknownaddon }}` alone → none.
- **Unknown modifier:** `{{ x | unknownmod }}` → a weak-warning on `unknownmod`; `{{ x | upper }}` →
  none (catalog); a project-scanned modifier (a `Modifiers/Foo.php`) → none.
- **Regression:** the full suite stays green; the new EPs don't disturb existing highlighting/completion
  tests.

## 6. Out of Scope (follow-ups)

- Quick-fixes (insert the missing closer; remove the stray closer; suppress).
- Unknown-tag warnings (a single `{{ foo }}` is indistinguishable from a variable).
- Re-flagging unclosed delimiters / unresolved partials (already platform-handled).
- Friendlier annotation of parser `PsiErrorElement`s; cross-partial balance analysis.

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Balance false-positive on an un-scanned addon pair tag | Only condition + catalog-`isPair` constructs are judged; unknown heads ignored entirely |
| A catalog pair tag legitimately used single | Rare; `isPair` is accurate (J1, review-checked); worst case one suppressible WARNING |
| Annotator cost per keystroke | Once-per-file O(statements) walk — folding-builder cost class |
| Unknown-modifier flags an addon modifier in `vendor/` (unscanned) | Weak-warning inspection — toggleable + suppressible; J1's ~190 covers core |
| Malformed/partial PSI while typing | Walk skips unparseable statements (`?: continue`), never throws — mirrors folding |
| Double-flagging partials/unclosed delimiters | Explicitly excluded — already platform-surfaced |
| `doHighlighting` picks up HTML template-data highlights | Tests filter to F's descriptions rather than strict `checkHighlighting` |
