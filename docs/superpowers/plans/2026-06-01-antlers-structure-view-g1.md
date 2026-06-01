# Antlers Structure View G1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Structure-view outline of paired tags/conditions/partials, backed by one shared `AntlersNestingTreeBuilder` that folding and balance also consume (removing the duplicated stack walk).

**Architecture:** `AntlersNestingTreeBuilder.build(file)` returns a `NestingTree` (nested `NestingNode`s + unmatched closers), reproducing the folding/balance walk exactly (catalog-`isPair` tags + `if`/`unless`, child-hoisting on drop). Folding and balance are refactored onto it; the structure view is a thin third consumer.

**Tech Stack:** Kotlin, IntelliJ Platform (`PsiStructureViewFactory`, `PsiTreeElementBase`, `FoldingBuilderEx`, `Annotator`), JUnit `BasePlatformTestCase`.

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-structure-view-g1-design.md`

**TDD note:** `./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`. Full suite is currently **169/169 green**. Keep it green. The folding/balance refactors are guarded by the **existing** `AntlersFoldingTest` and `AntlersBalanceAnnotatorTest` — they must stay green.

---

## Task 1: Shared nesting builder

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilder.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilderTest.kt` (new)

- [ ] **Step 1: Write the failing builder test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilderTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersNestingTreeBuilderTest : BasePlatformTestCase() {

    private fun tree(text: String): NestingTree {
        myFixture.configureByText("p.antlers.html", text)
        return AntlersNestingTreeBuilder.build(myFixture.file, project)
    }

    fun testNestedPair() {
        val t = tree("{{ collection:blog }}{{ if x }}{{ /if }}{{ /collection }}")
        assertEquals(1, t.roots.size)
        val collection = t.roots[0]
        assertEquals("collection", collection.name)
        assertNotNull("collection closed", collection.closer)
        assertEquals(1, collection.children.size)
        assertEquals("if", collection.children[0].name)
        assertNotNull("if closed", collection.children[0].closer)
        assertTrue(t.unmatchedClosers.isEmpty())
    }

    fun testUnmatchedCloser() {
        val t = tree("{{ /collection }}")
        assertTrue(t.roots.isEmpty())
        assertEquals(1, t.unmatchedClosers.size)
    }

    fun testUnclosedOpener() {
        val t = tree("{{ collection:blog }}")
        assertEquals(1, t.roots.size)
        assertNull("collection is unclosed", t.roots[0].closer)
    }

    fun testInnerUnmatchedDropped() {
        // {{ collection }}{{ if }}{{ /collection }} — the unclosed inner if is DROPPED when /collection
        // pops past it (matching the original folding/balance behavior: it had no closed children to
        // hoist, so it just vanishes — no fold, no warning, not a structure node).
        val t = tree("{{ collection:blog }}{{ if x }}{{ /collection }}")
        assertEquals(1, t.roots.size)
        assertNotNull(t.roots[0].closer)               // collection closed
        assertTrue("inner unclosed if dropped", t.roots[0].children.isEmpty())
        assertTrue(t.unmatchedClosers.isEmpty())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilderTest" --no-configuration-cache`
Expected: FAIL — `AntlersNestingTreeBuilder`/`NestingTree`/`NestingNode` don't exist.

- [ ] **Step 3: Create the builder**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilder.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** One paired tag/condition: its opener, matched closer (null = unclosed), and nested children. */
data class NestingNode(
    val opener: AntlersStatement,
    val name: String,
    val closer: AntlersStatement?,
    val children: List<NestingNode>
)

/** Reconstructed tag/condition nesting for a file. */
data class NestingTree(
    val roots: List<NestingNode>,
    val unmatchedClosers: List<AntlersStatement>
)

/**
 * The single source of truth for paired-tag/condition nesting, shared by folding, balance diagnostics,
 * and the structure view. Reproduces the original folding/balance stack walk: only catalog-isPair tags
 * and if/unless conditions open; a closer pops the nearest matching opener, dropping inner unmatched
 * openers (hoisting their already-closed children to the parent); openers left at EOF are unclosed;
 * closers with no opener are unmatched. Tolerant; never throws.
 */
object AntlersNestingTreeBuilder {

    private val CONDITION_OPENERS = setOf("if", "unless")
    private val CONDITION_CLOSERS = mapOf("endif" to "if", "endunless" to "unless")

    private class Frame(val name: String, val opener: AntlersStatement, val children: MutableList<NestingNode> = mutableListOf())

    fun build(root: PsiElement, project: Project): NestingTree {
        val catalog = if (project.isDefault) null else AntlersCatalogService.getInstance(project)
        val roots = mutableListOf<NestingNode>()
        val unmatched = mutableListOf<AntlersStatement>()
        val stack = ArrayDeque<Frame>()

        val statements = PsiTreeUtil.findChildrenOfType(root, AntlersStatement::class.java)
            .sortedBy { it.textRange.startOffset }
        for (stmt in statements) {
            val closing = stmt.closingTag
            if (closing != null) {
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':') ?: continue
                val idx = stack.indexOfLast { it.name == name }
                if (idx >= 0) closeMatched(stack, idx, stmt, roots) else unmatched.add(stmt)
                continue
            }
            val condition = stmt.condition
            if (condition != null) {
                val kw = (condition as? AntlersConditionMixin)?.keyword ?: continue
                val openerName = CONDITION_CLOSERS[kw]
                if (openerName != null) {
                    val idx = stack.indexOfLast { it.name == openerName }
                    if (idx >= 0) closeMatched(stack, idx, stmt, roots) else unmatched.add(stmt)
                } else if (kw in CONDITION_OPENERS) {
                    stack.addLast(Frame(kw, stmt))
                }
                continue
            }
            val namePath = stmt.namePath as? AntlersNamePathMixin ?: continue
            val head = namePath.head
            if (head.isBlank()) continue
            if (catalog?.tag(head)?.isPair == true) stack.addLast(Frame(head, stmt))
        }
        // Openers still open at EOF are unclosed; attach innermost-first to their parent (or roots).
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            val node = NestingNode(f.opener, f.name, null, f.children)
            (stack.lastOrNull()?.children ?: roots).add(node)
        }
        return NestingTree(roots, unmatched)
    }

    /** Close the frame at [idx] with [closer]; drop inner frames but hoist their closed children up. */
    private fun closeMatched(stack: ArrayDeque<Frame>, idx: Int, closer: AntlersStatement, roots: MutableList<NestingNode>) {
        // Inner unmatched openers are DROPPED (not flagged — matching the original walk), but their
        // already-closed children are hoisted to the parent so a properly-closed inner construct keeps
        // its fold.
        while (stack.size > idx + 1) {
            val dropped = stack.removeLast()
            stack.last().children.addAll(dropped.children)
        }
        val f = stack.removeLast()
        val node = NestingNode(f.opener, f.name, closer, f.children)
        (stack.lastOrNull()?.children ?: roots).add(node)
    }
}
```

- [ ] **Step 4: Run the builder test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilderTest" --no-configuration-cache`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilder.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersNestingTreeBuilderTest.kt
git commit -m "Add shared AntlersNestingTreeBuilder (tag/condition nesting tree)"
```

---

## Task 2: Refactor the folding builder onto the shared builder

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFoldingBuilder.kt`
- Test: existing `src/test/kotlin/com/github/balotias/intellijantlers/AntlersFoldingTest.kt` (must stay green)

- [ ] **Step 1: Replace the inline stack walk with the builder**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFoldingBuilder.kt`.

Remove the two top-level condition constants (they move into the builder):

```kotlin
private val CONDITION_OPENERS = setOf("if", "unless")
private val CONDITION_CLOSERS = mapOf("endif" to "if", "endunless" to "unless")
```

Replace the paired-tag/condition walk — from the `// Paired tag / condition folds via a name stack…`
comment through the end of the `for (stmt in statements)` loop (the whole `val catalog … }` block, i.e.
the lines that build `stack`, iterate `statements`, and push/pop) — with:

```kotlin
        // Paired tag / condition folds via the shared nesting builder.
        addPairFolds(AntlersNestingTreeBuilder.build(root, root.project).roots, out)
```

Add the recursive helper (e.g. just above `addNode`):

```kotlin
    private fun addPairFolds(nodes: List<com.github.balotias.intellijantlers.scope.NestingNode>, out: MutableList<FoldingDescriptor>) {
        for (n in nodes) {
            val closer = n.closer
            if (closer != null) {
                val range = TextRange(n.opener.textRange.startOffset, closer.textRange.endOffset)
                if (range.length > 0) out.add(FoldingDescriptor(n.opener.node, range))
            }
            addPairFolds(n.children, out)
        }
    }
```

Add the import:

```kotlin
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
```

Remove now-unused imports (`AntlersCatalogService`, `AntlersClosingTagMixin`, `AntlersConditionMixin`,
`AntlersNamePathMixin`, `AntlersStatement` — verify each is no longer referenced; the comment-fold and
single-node helpers don't use them). `AntlersNoparseBlock`/`AntlersPhpBlock`/`PsiComment`/`PsiWhiteSpace`/
`TextRange`/`PsiTreeUtil` stay.

- [ ] **Step 2: Run the folding regression — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.AntlersFoldingTest" --no-configuration-cache`
Expected: PASS — identical fold count/ranges (matched pairs fold; the builder's child-hoisting preserves
inner closed folds; single-node comment/noparse/php folds are untouched).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFoldingBuilder.kt
git commit -m "Refactor folding builder onto the shared nesting builder"
```

---

## Task 3: Refactor the balance annotator onto the shared builder

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotator.kt`
- Test: existing `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotatorTest.kt` (must stay green)

- [ ] **Step 1: Replace the stack walk with the builder + tree consumers**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotator.kt`. Replace the
body of `annotate` after the `val catalog = …` line — i.e. the whole `val stack = …` block through the
final `for ((name, open) in stack) { … }` — with:

```kotlin
        val tree = AntlersNestingTreeBuilder.build(element, element.project)

        // Closers with no opener — flag only known constructs (conditions or catalog-isPair tags).
        for (stmt in tree.unmatchedClosers) {
            val closing = stmt.closingTag
            if (closing != null) {
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':') ?: continue
                if (name in CONDITION_OPENERS || catalog.tag(name)?.isPair == true) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Closing '/$name' has no matching opening tag.")
                        .range(stmt).create()
                }
            } else {
                val kw = (stmt.condition as? AntlersConditionMixin)?.keyword ?: continue
                holder.newAnnotation(HighlightSeverity.ERROR, "Closing '$kw' has no matching opening tag.")
                    .range(stmt).create()
            }
        }

        // Openers never closed (every node in the tree is already a known construct by construction).
        reportUnclosed(tree.roots, holder)
    }

    private fun reportUnclosed(nodes: List<NestingNode>, holder: AnnotationHolder) {
        for (n in nodes) {
            if (n.closer == null) {
                holder.newAnnotation(HighlightSeverity.WARNING, "'{{ ${n.name} }}' is never closed.")
                    .range(n.opener).create()
            }
            reportUnclosed(n.children, holder)
        }
    }
```

(Note: this replaces the trailing `}` of `annotate` with the new `reportUnclosed` helper, then the class
continues with the existing `companion object`.)

Update imports: add

```kotlin
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.github.balotias.intellijantlers.scope.NestingNode
```

Remove now-unused imports (`AntlersNamePathMixin`, `AntlersStatement`, `PsiTreeUtil` — verify none remain
referenced). Keep `AntlersClosingTagMixin`, `AntlersConditionMixin`, `AntlersCatalogService`, `AntlersFile`,
`AnnotationHolder`, `Annotator`, `HighlightSeverity`, `PsiElement`. The `companion object` (`CONDITION_OPENERS`/
`CONDITION_CLOSERS`) stays.

- [ ] **Step 2: Run the balance regression — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.editor.AntlersBalanceAnnotatorTest" --no-configuration-cache`
Expected: PASS — identical ERROR (stray closer, known construct) / WARNING (unclosed opener) set and
messages. The `testUnknownConstructIgnored` case still passes (unknown stray closers are in
`unmatchedClosers` but skipped by the known-construct filter).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotator.kt
git commit -m "Refactor balance annotator onto the shared nesting builder"
```

---

## Task 4: Structure view

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/structure/AntlersStructureViewElement.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/structure/AntlersStructureViewModel.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/structure/AntlersStructureViewFactory.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/structure/AntlersStructureViewTest.kt` (new)

- [ ] **Step 1: Write the failing structure tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/structure/AntlersStructureViewTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.structure

import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersStructureViewTest : BasePlatformTestCase() {

    private fun rootChildren(text: String): List<StructureViewTreeElement> {
        myFixture.configureByText("p.antlers.html", text)
        return AntlersStructureViewModel(myFixture.file).root.children
            .toList().filterIsInstance<StructureViewTreeElement>()
    }

    private fun label(e: StructureViewTreeElement) = e.presentation.presentableText ?: ""

    fun testPairedTagWithNestedCondition() {
        val children = rootChildren("{{ collection:blog }}{{ if x }}{{ /if }}{{ /collection }}")
        val collection = children.firstOrNull { label(it) == "collection:blog" }
        assertNotNull("top-level collection:blog: ${children.map { label(it) }}", collection)
        val childLabels = collection!!.children.toList()
            .filterIsInstance<StructureViewTreeElement>().map { label(it) }
        assertTrue("nested if: $childLabels", childLabels.any { it == "if" })
    }

    fun testPartialLeaf() {
        val labels = rootChildren("{{ partial:src=\"blog/card\" }}").map { label(it) }
        assertTrue("partial leaf: $labels", labels.any { it.contains("partial") && it.contains("blog/card") })
    }

    fun testPlainVariableNotShown() {
        val labels = rootChildren("{{ title }}").map { label(it) }
        assertTrue("no plain variable nodes: $labels", labels.none { it == "title" })
    }

    fun testValueIsOpenerStatement() {
        val node = rootChildren("{{ collection:blog }}{{ /collection }}")
            .first { label(it) == "collection:blog" }
        assertTrue(node.value is AntlersStatement)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.structure.AntlersStructureViewTest" --no-configuration-cache`
Expected: FAIL — `AntlersStructureViewModel` doesn't exist.

- [ ] **Step 3: Create the structure-view element**

Create `src/main/kotlin/com/github/balotias/intellijantlers/structure/AntlersStructureViewElement.kt`:

```kotlin
package com.github.balotias.intellijantlers.structure

import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.github.balotias.intellijantlers.scope.NestingNode
import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

/** A structure-view node: the file root, a paired-construct node, or a partial leaf. */
class AntlersStructureViewElement(
    element: PsiElement,
    private val label: String,
    private val nodeIcon: Icon?,
    private val childProvider: () -> Collection<StructureViewTreeElement>
) : PsiTreeElementBase<PsiElement>(element) {

    override fun getPresentableText(): String = label
    override fun getIcon(open: Boolean): Icon? = nodeIcon
    override fun getChildrenBase(): Collection<StructureViewTreeElement> = childProvider()

    companion object {
        fun forFile(file: PsiFile): AntlersStructureViewElement =
            AntlersStructureViewElement(file, file.name, AllIcons.Nodes.Tag) {
                val out = mutableListOf<StructureViewTreeElement>()
                AntlersNestingTreeBuilder.build(file, file.project).roots.forEach { out.add(fromNode(it)) }
                PsiTreeUtil.findChildrenOfType(file, AntlersStatement::class.java)
                    .filter { (it.namePath as? AntlersNamePathMixin)?.head == "partial" }
                    .sortedBy { it.textRange.startOffset }
                    .forEach { out.add(fromPartial(it)) }
                out
            }

        private fun fromNode(n: NestingNode): AntlersStructureViewElement {
            val cond = n.opener.condition
            val label = if (cond != null) (cond as? AntlersConditionMixin)?.keyword ?: n.name
            else (n.opener.namePath as? AntlersNamePathMixin)?.pathText ?: n.name
            val icon = if (cond != null) AllIcons.Nodes.Lambda else AllIcons.Nodes.Tag
            return AntlersStructureViewElement(n.opener, label, icon) { n.children.map { fromNode(it) } }
        }

        private fun fromPartial(stmt: AntlersStatement): AntlersStructureViewElement {
            val inner = stmt.text.trim().removePrefix("{{").removeSuffix("}}").trim()
            return AntlersStructureViewElement(stmt, inner, AllIcons.Nodes.Include) { emptyList() }
        }
    }
}
```

- [ ] **Step 4: Create the model + factory**

Create `src/main/kotlin/com/github/balotias/intellijantlers/structure/AntlersStructureViewModel.kt`:

```kotlin
package com.github.balotias.intellijantlers.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.psi.PsiFile

class AntlersStructureViewModel(psiFile: PsiFile) :
    StructureViewModelBase(psiFile, AntlersStructureViewElement.forFile(psiFile)),
    StructureViewModel.ElementInfoProvider {

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean = false
    override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean = false
}
```

Create `src/main/kotlin/com/github/balotias/intellijantlers/structure/AntlersStructureViewFactory.kt`:

```kotlin
package com.github.balotias.intellijantlers.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class AntlersStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder =
        object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                AntlersStructureViewModel(psiFile)
        }
}
```

- [ ] **Step 5: Register the factory**

Edit `src/main/resources/META-INF/plugin.xml` — add after the `localInspection` line (inside `<extensions>`):

```xml
        <lang.psiStructureViewFactory language="Antlers"
            implementationClass="com.github.balotias.intellijantlers.structure.AntlersStructureViewFactory"/>
```

- [ ] **Step 6: Run the structure tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.structure.AntlersStructureViewTest" --no-configuration-cache`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/structure/ src/main/resources/META-INF/plugin.xml src/test/kotlin/com/github/balotias/intellijantlers/structure/AntlersStructureViewTest.kt
git commit -m "Add Antlers structure view (outline of tags/conditions/partials)"
```

---

## Task 5: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew test --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm no failures**

Run: `grep -lo 'failures="[1-9]' build/test-results/test/*.xml | wc -l`
Expected: `0`.

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc`
Expected: **~177** (169 prior + 8 new). Exact count may differ; **0 failures** is the gate — and the
existing folding/balance tests being green proves the refactor is behavior-preserving.

- [ ] **Step 3: Verify production compilation**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL (no unresolved references after the import cleanups).

---

## Notes for the implementer

- The builder reproduces the folding/balance walk semantics EXACTLY, including child-hoisting: when a
  closer pops past inner unmatched openers, their already-closed descendants are preserved (so a properly
  closed inner construct still gets its fold). The existing `AntlersFoldingTest`/`AntlersBalanceAnnotatorTest`
  are the behavioral guard — if either regresses, the builder semantics diverged.
- `myFixture.file` for a `.antlers.html` is the Antlers `AntlersFile` (base language), so the builder finds
  the `AntlersStatement`s.
- `PsiTreeElementBase` provides `getValue()`/navigation for free; the structure element only supplies the
  label, icon, and children.
- Partials are labelled with the statement's inner text (e.g. `partial:src="blog/card"`) — simple and
  unambiguous; a prettier `partial: <path>` extraction is a follow-up.
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
```
