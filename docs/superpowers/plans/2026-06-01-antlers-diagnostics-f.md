# Antlers Diagnostics F — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Flag semantic balance errors (unmatched/unclosed paired tags & conditions) via an Annotator, and unknown modifiers via a suppressible inspection.

**Architecture:** `AntlersBalanceAnnotator` runs once per `AntlersFile`, replaying statements through the folding builder's stack algorithm and emitting ERROR (stray closer) / WARNING (unclosed opener) — judging only known constructs (conditions + catalog-`isPair` tags). `AntlersUnknownModifierInspection` flags a modifier name not in the catalog (+project scan) as a weak warning. Two `plugin.xml` registrations.

**Tech Stack:** Kotlin, IntelliJ Platform (`Annotator`, `LocalInspectionTool`), JUnit `BasePlatformTestCase` + `myFixture.doHighlighting()`.

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-diagnostics-f-design.md`

**TDD note:** `./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`. Full suite is currently **160/160 green**. Keep it green. Tests use `myFixture.doHighlighting()` and **filter to F's descriptions** (the HTML template-data adds its own highlights, so strict `checkHighlighting` would over-assert).

---

## Task 1: Balance annotator

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotator.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotatorTest.kt` (new)

- [ ] **Step 1: Write the failing balance tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotatorTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersBalanceAnnotatorTest : BasePlatformTestCase() {

    /** Highlights whose description is one of F's balance diagnostics. */
    private fun balance(text: String): List<HighlightInfo> {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting().filter {
            val d = it.description ?: ""
            d.contains("has no matching") || d.contains("is never closed")
        }
    }

    fun testUnopenedTagCloser() {
        val d = balance("{{ /collection }}")
        assertTrue("expected ERROR: ${d.map { it.description }}",
            d.any { it.severity == HighlightSeverity.ERROR && it.description.contains("no matching") })
    }

    fun testUnopenedConditionCloser() {
        val d = balance("{{ endif }}")
        assertTrue(d.any { it.severity == HighlightSeverity.ERROR && it.description.contains("no matching") })
    }

    fun testUnclosedTagOpener() {
        val d = balance("{{ collection:blog }}")
        assertTrue(d.any { it.severity == HighlightSeverity.WARNING && it.description.contains("never closed") })
    }

    fun testUnclosedConditionOpener() {
        val d = balance("{{ if foo }}")
        assertTrue(d.any { it.severity == HighlightSeverity.WARNING && it.description.contains("never closed") })
    }

    fun testBalancedHasNoDiagnostics() {
        assertTrue(balance("{{ if foo }}{{ /if }}").isEmpty())
        assertTrue(balance("{{ collection:blog }}{{ /collection }}").isEmpty())
        assertTrue(balance("{{ collection:blog }}{{ if x }}{{ /if }}{{ /collection }}").isEmpty())
    }

    fun testUnknownConstructIgnored() {
        assertTrue("stray /unknownaddon must not be flagged", balance("{{ /unknownaddon }}").isEmpty())
        assertTrue("bare unknown tag must not be flagged", balance("{{ unknownaddon }}").isEmpty())
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.editor.AntlersBalanceAnnotatorTest" --no-configuration-cache`
Expected: FAIL — no annotator registered, so the `assertTrue(... isNotEmpty / any)` cases fail.

- [ ] **Step 3: Create the annotator**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotator.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Flags unbalanced paired tags / conditions by replaying the file's statements through a name stack
 * (the same algorithm as AntlersFoldingBuilder). Only judges KNOWN pair constructs — conditions and
 * catalog-isPair tags — so unknown/addon tags never produce false positives. Tolerant; never throws.
 */
class AntlersBalanceAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is AntlersFile) return            // run once, on the Antlers file root
        if (element.project.isDefault) return
        val catalog = AntlersCatalogService.getInstance(element.project)

        val stack = ArrayDeque<Pair<String, AntlersStatement>>()
        val statements = PsiTreeUtil.findChildrenOfType(element, AntlersStatement::class.java)
            .sortedBy { it.textRange.startOffset }
        for (stmt in statements) {
            val closing = stmt.closingTag
            if (closing != null) {
                val name = (closing as? AntlersClosingTagMixin)?.closedName?.substringBefore(':') ?: continue
                val idx = stack.indexOfLast { it.first == name }
                if (idx >= 0) {
                    while (stack.size > idx) stack.removeLast()
                } else if (name in CONDITION_OPENERS || catalog.tag(name)?.isPair == true) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Closing '/$name' has no matching opening tag.")
                        .range(stmt).create()
                }
                continue
            }
            val condition = stmt.condition
            if (condition != null) {
                val kw = (condition as? AntlersConditionMixin)?.keyword ?: continue
                val openerName = CONDITION_CLOSERS[kw]
                if (openerName != null) {
                    val idx = stack.indexOfLast { it.first == openerName }
                    if (idx >= 0) {
                        while (stack.size > idx) stack.removeLast()
                    } else {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Closing '$kw' has no matching opening tag.")
                            .range(stmt).create()
                    }
                } else if (kw in CONDITION_OPENERS) {
                    stack.addLast(kw to stmt)
                }
                continue
            }
            val namePath = stmt.namePath as? AntlersNamePathMixin ?: continue
            val head = namePath.head
            if (head.isBlank()) continue
            if (catalog.tag(head)?.isPair == true) stack.addLast(head to stmt)
        }
        for ((name, open) in stack) {
            holder.newAnnotation(HighlightSeverity.WARNING, "'{{ $name }}' is never closed.")
                .range(open).create()
        }
    }

    companion object {
        private val CONDITION_OPENERS = setOf("if", "unless")
        private val CONDITION_CLOSERS = mapOf("endif" to "if", "endunless" to "unless")
    }
}
```

- [ ] **Step 4: Register the annotator**

Edit `src/main/resources/META-INF/plugin.xml` — add after the `lang.foldingBuilder` line (inside `<extensions>`):

```xml
        <lang.foldingBuilder language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersFoldingBuilder"/>
```

becomes:

```xml
        <lang.foldingBuilder language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersFoldingBuilder"/>
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersBalanceAnnotator"/>
```

- [ ] **Step 5: Run the balance tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.editor.AntlersBalanceAnnotatorTest" --no-configuration-cache`
Expected: PASS (6 tests).

- [ ] **Step 6: Run the editor/highlighting regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.LexerTest" --tests "com.github.balotias.intellijantlers.editor.*" --no-configuration-cache`
Expected: PASS — the annotator only adds balance diagnostics; folding/brace/commenter unaffected.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotator.kt src/main/resources/META-INF/plugin.xml src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotatorTest.kt
git commit -m "Add balance annotator (unmatched/unclosed paired tags & conditions)"
```

---

## Task 2: Unknown-modifier inspection

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/inspection/AntlersUnknownModifierInspection.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/inspection/AntlersUnknownModifierInspectionTest.kt` (new)

- [ ] **Step 1: Write the failing inspection tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/inspection/AntlersUnknownModifierInspectionTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersUnknownModifierInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(AntlersUnknownModifierInspection())
    }

    private fun unknownModifierWarnings(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Unknown modifier") }
    }

    fun testUnknownModifierFlagged() {
        assertTrue("expected an unknown-modifier warning",
            unknownModifierWarnings("{{ x | unknownmod }}").isNotEmpty())
    }

    fun testKnownModifierNotFlagged() {
        assertTrue("upper is a catalog modifier",
            unknownModifierWarnings("{{ x | upper }}").isEmpty())
    }

    fun testKnownModifierWithArgsNotFlagged() {
        assertTrue("truncate is a catalog modifier",
            unknownModifierWarnings("{{ x | truncate(10) }}").isEmpty())
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.inspection.AntlersUnknownModifierInspectionTest" --no-configuration-cache`
Expected: FAIL — unresolved reference `AntlersUnknownModifierInspection`.

- [ ] **Step 3: Create the inspection**

Create `src/main/kotlin/com/github/balotias/intellijantlers/inspection/AntlersUnknownModifierInspection.kt`:

```kotlin
package com.github.balotias.intellijantlers.inspection

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/** Weak warning on a `| modifier` whose name is in neither the bundled catalog nor the project scan. */
class AntlersUnknownModifierInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is AntlersModifierMixin) return
                val name = element.modifierName
                if (name.isBlank()) return
                val project = element.project
                if (project.isDefault) return
                if (AntlersCatalogService.getInstance(project).modifiers().any { it.name == name }) return
                val ident = element.node.findChildByType(AntlersTypes.T_IDENT)?.psi ?: element
                holder.registerProblem(ident, "Unknown modifier '$name'", ProblemHighlightType.WEAK_WARNING)
            }
        }
}
```

- [ ] **Step 4: Register the inspection**

Edit `src/main/resources/META-INF/plugin.xml` — add after the `annotator` line from Task 1:

```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersBalanceAnnotator"/>
```

becomes:

```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersBalanceAnnotator"/>
        <localInspection language="Antlers"
                         implementationClass="com.github.balotias.intellijantlers.inspection.AntlersUnknownModifierInspection"
                         shortName="AntlersUnknownModifier" displayName="Unknown Antlers modifier"
                         groupName="Antlers" enabledByDefault="true" level="WARNING"/>
```

- [ ] **Step 5: Run the inspection tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.inspection.AntlersUnknownModifierInspectionTest" --no-configuration-cache`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/inspection/AntlersUnknownModifierInspection.kt src/main/resources/META-INF/plugin.xml src/test/kotlin/com/github/balotias/intellijantlers/inspection/AntlersUnknownModifierInspectionTest.kt
git commit -m "Add unknown-modifier inspection (weak warning)"
```

---

## Task 3: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew test --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm no failures**

Run: `grep -lo 'failures="[1-9]' build/test-results/test/*.xml | wc -l`
Expected: `0`.

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc`
Expected: **~169** (160 prior + 9 new). Exact count may differ; **0 failures** is the gate.

- [ ] **Step 3: Verify production compilation + plugin.xml validity**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL. (The new `<annotator>`/`<localInspection>` reference real classes; a typo'd implementationClass would surface as a runtime EP error in the highlighting tests, which pass.)

---

## Notes for the implementer

- The annotator gates on `element is AntlersFile` so the stack walk runs exactly once per file (not per element). `AntlersFile` is in `parser/AntlersParserDefinition.kt`.
- Balance only flags KNOWN constructs: a stray `{{ /x }}` is an error only when `x` is `if`/`unless` or a catalog-`isPair` tag; otherwise it's ignored (no false positive on addon/unknown tags). `testUnknownConstructIgnored` guards this.
- Both `{{ /if }}` (a closing tag) and `{{ endif }}` (a condition closer) close an `{{ if }}` — the annotator handles both forms (push happens in the condition-opener branch; pop happens in either the closing-tag or condition-closer branch).
- Tests filter `doHighlighting()` to F's descriptions because the HTML template-data and parser contribute other highlights; never use strict `checkHighlighting`.
- The inspection test uses `myFixture.enableInspections(...)`; the production `<localInspection>` registration (Task 2 Step 4) is what enables it for real users.
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
```
