# Antlers Editor Niceties (Sub-project D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add brace matching, auto-close `}}`, an Antlers commenter, and code folding.

**Architecture:** Four independent platform contributions (`PairedBraceMatcher`, `TypedHandlerDelegate`, `Commenter`, `FoldingBuilderEx`) in an `editor/` package, each registered in plugin.xml. They reuse the existing delimiter tokens and PSI from sub-project A.

**Tech Stack:** Kotlin, IntelliJ Platform 2025.2, `BasePlatformTestCase`.

**Companion spec:** `docs/superpowers/specs/2026-06-01-antlers-editor-niceties-design.md`

**Test note:** `./gradlew test` runs the full suite (works). Follow TDD: write test, run (red), implement, run (green).

---

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `editor/AntlersBraceMatcher.kt` | Pair delimiter tokens | Create |
| `editor/AntlersCommenter.kt` | `{{# #}}` block comment | Create |
| `editor/AntlersTypedHandler.kt` | Auto-insert `}}` on `{{` | Create |
| `editor/AntlersFoldingBuilder.kt` | Fold paired tags + comments/noparse/php | Create |
| `resources/META-INF/plugin.xml` | Register the 4 EPs | Modify |
| tests under `src/test/.../editor/` | brace pairs, auto-close, commenter, folding | Create |

---

## Task 1: Brace matcher + commenter

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBraceMatcher.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersCommenter.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersEditorBasicsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersEditorBasicsTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersEditorBasicsTest : BasePlatformTestCase() {

    fun testBracePairsConfigured() {
        val pairs = AntlersBraceMatcher().pairs.map { it.leftBraceType to it.rightBraceType }
        assertTrue(pairs.contains(AntlersTypes.T_LDOUBLE to AntlersTypes.T_RDOUBLE))
        assertTrue(pairs.contains(AntlersTypes.T_COMMENT_OPEN to AntlersTypes.T_COMMENT_CLOSE))
    }

    fun testBlockCommentWrapsSelection() {
        myFixture.configureByText("t.antlers.html", "<selection>Hello</selection>")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
        assertTrue("expected {{# wrap, got: ${myFixture.file.text}", myFixture.file.text.contains("{{#") && myFixture.file.text.contains("#}}"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersEditorBasicsTest" --no-configuration-cache` → FAIL (classes don't exist).

- [ ] **Step 3: Create the brace matcher**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBraceMatcher.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class AntlersBraceMatcher : PairedBraceMatcher {
    private val pairs = arrayOf(
        BracePair(AntlersTypes.T_LDOUBLE, AntlersTypes.T_RDOUBLE, true),
        BracePair(AntlersTypes.T_COMMENT_OPEN, AntlersTypes.T_COMMENT_CLOSE, false),
        BracePair(AntlersTypes.T_PHP_RAW_OPEN, AntlersTypes.T_PHP_RAW_CLOSE, false),
        BracePair(AntlersTypes.T_PHP_ECHO_OPEN, AntlersTypes.T_PHP_ECHO_CLOSE, false),
        BracePair(AntlersTypes.T_LPAREN, AntlersTypes.T_RPAREN, false),
        BracePair(AntlersTypes.T_LBRACKET, AntlersTypes.T_RBRACKET, false)
    )

    override fun getPairs(): Array<BracePair> = pairs
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
```

- [ ] **Step 4: Create the commenter**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersCommenter.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.intellij.lang.Commenter

/** Antlers has only block comments: `{{# ... #}}`. */
class AntlersCommenter : Commenter {
    override fun getLineCommentPrefix(): String? = null
    override fun getBlockCommentPrefix(): String = "{{#"
    override fun getBlockCommentSuffix(): String = "#}}"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
```

- [ ] **Step 5: Register in plugin.xml**

Add inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
        <lang.braceMatcher language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersBraceMatcher"/>
        <lang.commenter language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersCommenter"/>
```

- [ ] **Step 6: Verify + commit**

Run: `./gradlew test --tests "*AntlersEditorBasicsTest" --no-configuration-cache` → PASS (2/2). If `ACTION_COMMENT_BLOCK` behaves differently, try `IdeActions.ACTION_COMMENT_LINE`; the goal is the selection wrapped in `{{# … #}}`.

```bash
git add -A
git commit -m "Antlers brace matcher and block commenter"
```

---

## Task 2: Auto-close `}}`

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTypedHandler.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersTypedHandlerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersTypedHandlerTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersTypedHandlerTest : BasePlatformTestCase() {

    fun testAutoCloseOnSecondBrace() {
        myFixture.configureByText("t.antlers.html", "<caret>")
        myFixture.type("{{")
        myFixture.checkResult("{{ <caret> }}")
    }

    fun testNoDoubleCloseWhenAlreadyClosed() {
        myFixture.configureByText("t.antlers.html", "{<caret>}}")
        myFixture.type("{")
        myFixture.checkResult("{{<caret>}}")
    }

    fun testNoTripleBrace() {
        myFixture.configureByText("t.antlers.html", "{{<caret>")
        myFixture.type("{")
        myFixture.checkResult("{{{<caret>")
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersTypedHandlerTest" --no-configuration-cache` → FAIL.

- [ ] **Step 3: Create the typed handler**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersTypedHandler.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Auto-inserts ` }}` and centres the caret when the user completes a `{{` opener: `{{ <caret> }}`. */
class AntlersTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != '{') return Result.CONTINUE
        if (file.viewProvider.baseLanguage !== AntlersLanguage.INSTANCE) return Result.CONTINUE

        val doc = editor.document
        val offset = editor.caretModel.offset
        val text = doc.charsSequence
        // Just typed the second '{' of a "{{" opener.
        if (offset < 2 || text[offset - 1] != '{' || text[offset - 2] != '{') return Result.CONTINUE
        // Not part of "{{{".
        if (offset >= 3 && text[offset - 3] == '{') return Result.CONTINUE
        // Don't double if "}}" already follows.
        val after = text.subSequence(offset, minOf(text.length, offset + 4)).toString().trimStart()
        if (after.startsWith("}}")) return Result.CONTINUE

        doc.insertString(offset, "  }}")
        editor.caretModel.moveToOffset(offset + 1)
        return Result.STOP
    }
}
```

- [ ] **Step 4: Register in plugin.xml**

Add inside `<extensions>`:

```xml
        <typedHandler implementation="com.github.balotias.intellijantlers.editor.AntlersTypedHandler"/>
```

- [ ] **Step 5: Verify + commit**

Run: `./gradlew test --tests "*AntlersTypedHandlerTest" --no-configuration-cache` → PASS (3/3). If `testNoTripleBrace` or `testNoDoubleClose` fail because another typed handler (HTML data language) interferes, adjust the guards so the three asserted behaviours hold; the auto-close must only fire for a clean `{{` completion.

```bash
git add -A
git commit -m "Auto-close Antlers braces: {{ -> {{ | }}"
```

---

## Task 3: Folding

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFoldingBuilder.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersFoldingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersFoldingTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFoldingTest : BasePlatformTestCase() {

    private fun foldCount(text: String): Int {
        val file = myFixture.configureByText("t.antlers.html", text)
        return AntlersFoldingBuilder().buildFoldRegions(file, myFixture.editor.document, false).size
    }

    fun testPairedTagFolds() {
        // {{ collection }} ... {{ /collection }} is one foldable region
        assertTrue(foldCount("{{ collection }}\n  hi\n{{ /collection }}") >= 1)
    }

    fun testConditionFolds() {
        assertTrue(foldCount("{{ if x }}\n  hi\n{{ /if }}") >= 1)
    }

    fun testCommentFolds() {
        assertTrue(foldCount("{{# a long hidden comment here #}}") >= 1)
    }

    fun testNoFoldForSingleVariable() {
        assertEquals(0, foldCount("{{ title }}"))
    }

    fun testUnbalancedDoesNotThrow() {
        // just must not throw and produce no bogus fold
        foldCount("{{ collection }}\n{{ /nav }}")
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersFoldingTest" --no-configuration-cache` → FAIL.

- [ ] **Step 3: Create the folding builder**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFoldingBuilder.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.psi.AntlersClosingTag
import com.github.balotias.intellijantlers.psi.AntlersComment
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersNoparseBlock
import com.github.balotias.intellijantlers.psi.AntlersPhpBlock
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

private val CONDITION_OPENERS = setOf("if", "unless")

class AntlersFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val out = mutableListOf<FoldingDescriptor>()

        // Single-node folds: comments, noparse, php — when they span more than the bare delimiters.
        PsiTreeUtil.findChildrenOfType(root, AntlersComment::class.java).forEach { addNode(out, it) }
        PsiTreeUtil.findChildrenOfType(root, AntlersNoparseBlock::class.java).forEach { addNode(out, it) }
        PsiTreeUtil.findChildrenOfType(root, AntlersPhpBlock::class.java).forEach { addNode(out, it) }

        // Paired tag / condition folds via a name stack over statements in document order.
        val catalog = if (root.project.isDefault) null else AntlersCatalogService.getInstance(root.project)
        val stack = ArrayDeque<Pair<String, AntlersStatement>>()
        for (stmt in PsiTreeUtil.findChildrenOfType(root, AntlersStatement::class.java)) {
            val closing = PsiTreeUtil.getChildOfType(stmt, AntlersClosingTag::class.java)
            if (closing != null) {
                val name = (closing.closedName ?: continue).substringBefore(':')
                // pop the nearest matching open
                val idx = stack.indexOfLast { it.first == name }
                if (idx >= 0) {
                    val (_, open) = stack.removeAt(idx)
                    while (stack.size > idx) stack.removeLast() // drop unmatched inner opens
                    val range = TextRange(open.textRange.startOffset, stmt.textRange.endOffset)
                    if (range.length > 0) out.add(FoldingDescriptor(open.node, range))
                }
            } else {
                val head = PsiTreeUtil.getChildOfType(stmt, AntlersNamePathMixin::class.java)?.head ?: continue
                val isPair = head in CONDITION_OPENERS || catalog?.tag(head)?.isPair == true
                if (isPair) stack.addLast(head to stmt)
            }
        }
        return out.toTypedArray()
    }

    private fun addNode(out: MutableList<FoldingDescriptor>, element: PsiElement) {
        if (element.textLength > 6) out.add(FoldingDescriptor(element.node, element.textRange))
    }

    override fun getPlaceholderText(node: ASTNode): String {
        return when (node.psi) {
            is AntlersComment -> "{{# … #}}"
            is AntlersNoparseBlock -> "{{ noparse … }}"
            is AntlersPhpBlock -> "{{ php … }}"
            else -> "…"
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
```

- [ ] **Step 4: Register in plugin.xml**

Add inside `<extensions>`:

```xml
        <lang.foldingBuilder language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersFoldingBuilder"/>
```

- [ ] **Step 5: Verify + commit**

Run: `./gradlew test --tests "*AntlersFoldingTest" --no-configuration-cache` → PASS (5/5). If `findChildrenOfType` returns statements out of document order, wrap with `.sortedBy { it.textRange.startOffset }`. The `testPairedTagFolds`/`testConditionFolds` rely on `collection` being a catalog pair tag and `if` being a condition opener.

```bash
git add -A
git commit -m "Fold paired Antlers tags/conditions and comments/noparse/php"
```

---

## Task 4: Full build, regression, review

- [ ] **Step 1: Full suite**

Run: `./gradlew test --no-configuration-cache` → all green (A+B+C plus the new D test classes). Sandbox: `./gradlew compileKotlin compileTestKotlin`.

- [ ] **Step 2: Manual smoke (developer env)**

`./gradlew runIde`: type `{{` (auto-closes), Ctrl-Shift-/ on a selection (wraps in `{{# #}}`), put the caret on `{{` (matching `}}` highlights), and fold a paired tag and a comment.

- [ ] **Step 3: Commit touch-ups**

```bash
git add -A
git commit -m "Antlers editor niceties complete (sub-project D)" || echo "nothing to commit"
```

---

## Self-Review (against the spec)

- §3.1 brace matcher pairs (LDOUBLE/RDOUBLE, comment, php, paren, bracket) → Task 1 ✓
- §3.2 auto-close `{{ }}` (with no-double / no-triple guards, Antlers-only) → Task 2 ✓
- §3.3 commenter `{{# #}}`, no line comment → Task 1 ✓
- §3.4 folding (paired tags/conditions via stack; comment/noparse/php single-node; expanded by default) → Task 3 ✓
- §5 tests for each via `BasePlatformTestCase` → Tasks 1–3 ✓
- §6 formatter/structure view out of scope → not built ✓

**Placeholder scan:** every step has complete code. **Type consistency:** `AntlersBraceMatcher().pairs` exposed for the test; `AntlersCatalogService.getInstance(project).tag(name).isPair`, `AntlersClosingTag.closedName`, `AntlersNamePathMixin.head` reused from A/B/C; folding uses `FoldingDescriptor(node, range)`.
```
