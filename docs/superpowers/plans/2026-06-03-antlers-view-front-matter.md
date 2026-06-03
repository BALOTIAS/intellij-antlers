# Antlers View Front Matter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Statamic Antlers view front matter (`---…---` at the top of a `.antlers.html` file) smart — `view:` key completion, go-to-definition, hover docs — and highlight the block as YAML-style colors.

**Architecture:** One pure scanner (`ViewFrontMatterScanner`) extracts the leading front-matter block; a cached project service exposes it; four existing seams (completion / reference / docs) and one new annotator (B-visual highlighting) consume it. No lexer/parser/grammar/regen — exactly the blueprint-variables pattern plus a 4b-style overlay annotator.

**Tech Stack:** Kotlin, IntelliJ Platform 2025.2 (`@Service`, `CachedValuesManager`, `Annotator`, `FakePsiElement`, `PsiReferenceBase`, `AntlersSyntaxHighlighter` color keys), JUnit / `BasePlatformTestCase`.

**Spec:** `docs/superpowers/specs/2026-06-03-antlers-view-front-matter-design.md`
**Branch:** `antlers-view-front-matter` (already created from `main`; spec already committed).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty). The `--rerun-tasks` flag is REQUIRED (Gradle caches).

## File Structure

- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatter.kt` — data classes `TextSpan`/`FmEntry`/`FrontMatter` + `object ViewFrontMatterScanner`. Pure, IntelliJ-free → unit-testable.
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterService.kt` — `@Service(PROJECT)`, cached per-file access.
- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt` — `view:` key completion + `view` head suggestion.
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVarDeclaration.kt` — synthetic navigation target.
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVariableReference.kt` — soft reference.
- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt` — wire the view reference.
- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt` — `view:foo` doc branch.
- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt` — 3 color keys.
- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt` — register the 3 keys + demo.
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightAnnotator.kt` — B-visual overlay.
- **Modify** `src/main/resources/META-INF/plugin.xml` — register the annotator.
- Tests: one pure scanner test + four fixture tests (service, completion, reference+docs, highlight).

---

### Task 1: Front-matter scanner

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatter.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterScannerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterScannerTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewFrontMatterScannerTest {

    private fun fm(text: String) = ViewFrontMatterScanner.scan(text)

    @Test fun basicTopLevelKeys() {
        val r = fm("---\nfoo: bar\ntitle: \"My Page\"\n---\n{{ view:foo }}")!!
        assertEquals(listOf("foo", "title"), r.entries.filter { it.indent == 0 }.map { it.name })
        assertEquals("bar", r.entries[0].valuePreview)
        assertEquals("My Page", r.entries[1].valuePreview)        // surrounding quotes stripped
    }

    @Test fun keyAndValueOffsetsAreCorrect() {
        val text = "---\nfoo: bar\n---\n"
        val e = fm(text)!!.entries.single()
        assertEquals("foo", text.substring(e.key.start, e.key.end))
        assertEquals("bar", text.substring(e.value!!.start, e.value!!.end))
        assertEquals(4, e.key.start)                              // after "---\n"
    }

    @Test fun fenceSpans() {
        val text = "---\nfoo: bar\n---\nbody"
        val r = fm(text)!!
        assertEquals("---", text.substring(r.openFence.start, r.openFence.end))
        assertEquals("---", text.substring(r.closeFence.start, r.closeFence.end))
        assertEquals(0, r.openFence.start)
    }

    @Test fun nestedKeysKeptButNotTopLevel() {
        val r = fm("---\nfoo: bar\nnested:\n  child: x\n---\n")!!
        assertEquals(listOf("foo", "nested", "child"), r.entries.map { it.name })
        assertEquals(listOf("foo", "nested"), r.entries.filter { it.indent == 0 }.map { it.name })
    }

    @Test fun keyWithoutValueHasNullValueSpan() {
        val e = fm("---\nempty:\n---\n")!!.entries.single()
        assertNull(e.value)
        assertEquals("", e.valuePreview)
    }

    @Test fun noLeadingFenceReturnsNull() {
        assertNull(fm("{{ title }}\nfoo: bar"))
        assertNull(fm("<div>---</div>\nfoo: bar\n---"))            // --- not on line 0
    }

    @Test fun unclosedFrontMatterReturnsNull() {
        assertNull(fm("---\nfoo: bar\n"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*ViewFrontMatterScannerTest"`
Expected: FAIL — compile error (`ViewFrontMatterScanner` unresolved).

- [ ] **Step 3: Write the scanner**

Create `src/main/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatter.kt`:
```kotlin
package com.github.balotias.intellijantlers.view

/** Half-open [start, end) — maps directly to com.intellij.openapi.util.TextRange(start, end). */
data class TextSpan(val start: Int, val end: Int)

data class FmEntry(
    val name: String,
    val indent: Int,
    val key: TextSpan,
    val value: TextSpan?,
    val valuePreview: String
)

data class FrontMatter(
    val block: TextSpan,
    val openFence: TextSpan,
    val closeFence: TextSpan,
    val entries: List<FmEntry>
)

/**
 * Hand-rolled extractor for Statamic Antlers view front matter — the leading `---…---` YAML block.
 * Pure / IntelliJ-free so it can be unit-tested directly. Tolerant; never throws. Returns null unless
 * the text starts with a `---` fence (column 0) and has a closing `---` fence.
 */
object ViewFrontMatterScanner {

    private val KEY_RE = Regex("""^(\s*)([A-Za-z_][A-Za-z0-9_-]*):(.*)$""")

    fun scan(text: String): FrontMatter? {
        val body = if (text.isNotEmpty() && text[0] == '﻿') text.substring(1) else text
        val shift = text.length - body.length        // 0 or 1 (leading UTF-8 BOM)
        val lines = body.split("\n")
        if (lines.isEmpty() || lines[0].trimEnd() != "---") return null

        val lineStart = IntArray(lines.size)
        var pos = 0
        for (i in lines.indices) { lineStart[i] = pos; pos += lines[i].length + 1 }

        var closeLine = -1
        for (i in 1 until lines.size) if (lines[i].trimEnd() == "---") { closeLine = i; break }
        if (closeLine == -1) return null

        val openFence = TextSpan(shift, shift + 3)
        val closeFence = TextSpan(shift + lineStart[closeLine], shift + lineStart[closeLine] + 3)

        val entries = ArrayList<FmEntry>()
        for (i in 1 until closeLine) {
            val line = lines[i]
            val m = KEY_RE.find(line) ?: continue
            val indent = m.groupValues[1].length
            val name = m.groupValues[2]
            val keyStart = lineStart[i] + indent
            val key = TextSpan(shift + keyStart, shift + keyStart + name.length)
            val rawValue = m.groupValues[3]                                   // text after the colon
            val lead = rawValue.length - rawValue.trimStart().length
            val valueText = rawValue.trim()
            val value = if (valueText.isEmpty()) null else {
                val vStart = lineStart[i] + indent + name.length + 1 + lead   // +1 for the ':'
                TextSpan(shift + vStart, shift + vStart + valueText.length)
            }
            entries.add(FmEntry(name, indent, key, value, stripQuotes(valueText)))
        }

        return FrontMatter(TextSpan(0, closeFence.end), openFence, closeFence, entries)
    }

    private fun stripQuotes(s: String): String =
        if (s.length >= 2 && ((s.first() == '"' && s.last() == '"') || (s.first() == '\'' && s.last() == '\'')))
            s.substring(1, s.length - 1) else s
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*ViewFrontMatterScannerTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatter.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterScannerTest.kt
git commit -m "$(cat <<'EOF'
feat: view front-matter scanner (leading ---…--- YAML block)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Front-matter service

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterService.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterServiceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterServiceTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.view

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ViewFrontMatterServiceTest : BasePlatformTestCase() {

    fun testTopLevelNamesFromFrontMatter() {
        val file = myFixture.configureByText("p.antlers.html", "---\nfoo: bar\nbaz: qux\n---\n{{ view:foo }}")
        val names = ViewFrontMatterService.getInstance(project).topLevel(file).map { it.name }
        assertEquals(listOf("foo", "baz"), names)
    }

    fun testNoFrontMatterIsNull() {
        val file = myFixture.configureByText("p.antlers.html", "{{ title }}")
        assertNull(ViewFrontMatterService.getInstance(project).frontMatter(file))
        assertEquals(emptyList<FmEntry>(), ViewFrontMatterService.getInstance(project).topLevel(file))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*ViewFrontMatterServiceTest"`
Expected: FAIL — compile error (`ViewFrontMatterService` unresolved).

- [ ] **Step 3: Write the service**

Create `src/main/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterService.kt`:
```kotlin
package com.github.balotias.intellijantlers.view

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/** Cached, per-file access to a view's front matter. */
@Service(Service.Level.PROJECT)
class ViewFrontMatterService(private val project: Project) {

    fun frontMatter(file: PsiFile): FrontMatter? =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(
                ViewFrontMatterScanner.scan(file.text),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }

    /** Top-level keys only — the names accessible as `{{ view:<name> }}`. */
    fun topLevel(file: PsiFile): List<FmEntry> =
        frontMatter(file)?.entries?.filter { it.indent == 0 } ?: emptyList()

    companion object {
        fun getInstance(project: Project): ViewFrontMatterService = project.service()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*ViewFrontMatterServiceTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterService.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/view/ViewFrontMatterServiceTest.kt
git commit -m "$(cat <<'EOF'
feat: cached per-file view front-matter service

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `view:` completion (keys + head namespace)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersViewCompletionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersViewCompletionTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersViewCompletionTest : BasePlatformTestCase() {

    private fun complete(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testViewColonOffersFrontMatterKeys() {
        val items = complete("---\nfoo: bar\nbaz: qux\n---\n{{ view:<caret> }}")
        assertTrue("offers front-matter keys, got $items", items.containsAll(listOf("foo", "baz")))
    }

    fun testViewColonWithoutFrontMatterOffersNothing() {
        val items = complete("{{ view:<caret> }}")
        assertTrue("no view keys without front matter, got $items", items.none { it == "foo" })
    }

    fun testHeadOffersViewNamespaceWhenFrontMatterPresent() {
        val items = complete("---\nfoo: bar\n---\n{{ <caret> }}")
        assertTrue("offers the 'view' namespace, got ${items.take(40)}", items.contains("view"))
    }

    fun testHeadOmitsViewNamespaceWithoutFrontMatter() {
        val items = complete("{{ <caret> }}")
        assertFalse("no 'view' namespace without front matter", items.contains("view"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersViewCompletionTest"`
Expected: FAIL — `testViewColonOffersFrontMatterKeys` and `testHeadOffersViewNamespaceWhenFrontMatterPresent` fail (no `view` handling yet).

- [ ] **Step 3a: Add the `view` head suggestion (TAG_NAME branch)**

In `AntlersCompletionProvider.kt`, the `AntlersCompletionKind.TAG_NAME` branch ends with a `for (sv in SystemVariables.ALL) { … }` loop. Immediately **after** that loop (still inside the `TAG_NAME` branch, where `file` and `seen` are in scope) add:
```kotlin
                // `view` namespace — only when this file actually has front matter.
                if (com.github.balotias.intellijantlers.view.ViewFrontMatterService.getInstance(project)
                        .frontMatter(file) != null && seen.add("view")
                ) {
                    result.addElement(
                        LookupElementBuilder.create("view")
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText("Namespace")
                    )
                }
```

- [ ] **Step 3b: Add `view:` key completion (TAG_METHOD branch)**

In the `AntlersCompletionKind.TAG_METHOD` branch, the non-catalog-tag path is:
```kotlin
                } else {
                    // Not a catalog tag: a blueprint field member access via colon (e.g. {{ group:sub }}).
                    AntlersMemberResolver.resolveField(parameters.position, info.pathPrefix, project)
                        ?.let { offerMembers(it, project, result) }
                }
```
Replace that `else { … }` block with:
```kotlin
                } else if (info.pathPrefix == listOf("view")) {
                    // `{{ view:<caret> }}` — offer the view's front-matter keys.
                    val file = parameters.position.containingFile
                    for (e in com.github.balotias.intellijantlers.view.ViewFrontMatterService.getInstance(project).topLevel(file)) {
                        result.addElement(
                            LookupElementBuilder.create(e.name)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("View")
                                .withTailText(if (e.valuePreview.isNotBlank()) "  ${e.valuePreview}" else null, true)
                        )
                    }
                } else {
                    // Not a catalog tag: a blueprint field member access via colon (e.g. {{ group:sub }}).
                    AntlersMemberResolver.resolveField(parameters.position, info.pathPrefix, project)
                        ?.let { offerMembers(it, project, result) }
                }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersViewCompletionTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersViewCompletionTest.kt
git commit -m "$(cat <<'EOF'
feat: complete view front-matter keys after `view:` (+ head namespace)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Go-to-definition reference

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVarDeclaration.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVariableReference.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVariableReferenceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVariableReferenceTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersViewVariableReferenceTest : BasePlatformTestCase() {

    fun testViewVarResolvesToFrontMatterKey() {
        myFixture.configureByText("p.antlers.html", "---\nfoo: bar\n---\n{{ view:fo<caret>o }}")
        val ref = myFixture.getReferenceAtCaretPosition()
        assertNotNull("a reference is attached to view:foo", ref)
        val target = ref!!.resolve()
        assertNotNull("view:foo resolves", target)
        assertEquals("lands on the `foo:` key (offset 4)", 4, target!!.textOffset)
    }

    fun testUndefinedViewVarUnresolved() {
        myFixture.configureByText("p.antlers.html", "---\nfoo: bar\n---\n{{ view:no<caret>pe }}")
        val ref = myFixture.getReferenceAtCaretPosition()
        assertNull("undefined key does not resolve", ref?.resolve())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersViewVariableReferenceTest"`
Expected: FAIL — compile error (`AntlersViewVariableReference` unresolved) / no reference attached.

- [ ] **Step 3a: Write the synthetic declaration**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVarDeclaration.kt`:
```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.IncorrectOperationException

/**
 * Synthetic, renamable declaration for a front-matter key, so go-to-def lands precisely on the
 * `key:` line (the front matter is part of a large outer-HTML leaf, so resolving to the raw PSI
 * element would jump to the top of the file). Identity is by (file, offset).
 */
class AntlersViewVarDeclaration(
    private val project: Project,
    val varName: String,
    val file: VirtualFile,
    val offset: Int
) : FakePsiElement(), PsiNamedElement {

    override fun getParent(): PsiElement? = containingFile
    override fun getContainingFile(): PsiFile? = PsiManager.getInstance(project).findFile(file)
    override fun getProject(): Project = project
    override fun getName(): String = varName
    override fun getTextOffset(): Int = offset
    override fun getTextRange(): TextRange = TextRange(offset, offset + varName.length)
    override fun isValid(): Boolean = file.isValid
    override fun getLanguage(): Language = AntlersLanguage.INSTANCE

    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(project, file, offset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = file.isValid
    override fun canNavigateToSource(): Boolean = file.isValid

    override fun setName(newName: String): PsiElement {
        val doc = FileDocumentManager.getInstance().getDocument(file)
            ?: throw IncorrectOperationException("no document for ${file.name}")
        doc.replaceString(offset, offset + varName.length, newName)
        PsiDocumentManager.getInstance(project).commitDocument(doc)
        return AntlersViewVarDeclaration(project, newName, file, offset)
    }

    override fun isEquivalentTo(another: PsiElement?): Boolean = this == another
    override fun equals(other: Any?): Boolean =
        other is AntlersViewVarDeclaration && file == other.file && offset == other.offset
    override fun hashCode(): Int = file.hashCode() * 31 + offset
}
```

- [ ] **Step 3b: Write the reference**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVariableReference.kt`:
```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.view.ViewFrontMatterService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafPsiElement

/** Soft reference from `{{ view:<name> }}` to the front-matter `<name>:` key in the same file. */
class AntlersViewVariableReference(
    element: PsiElement,
    private val name: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val file = element.containingFile ?: return null
        val entry = ViewFrontMatterService.getInstance(element.project).topLevel(file)
            .firstOrNull { it.name == name } ?: return null
        val vf = file.virtualFile ?: return null
        return AntlersViewVarDeclaration(element.project, name, vf, entry.key.start)
    }

    override fun isReferenceTo(target: PsiElement): Boolean =
        target is AntlersViewVarDeclaration && target.varName == name && resolve() == target

    override fun handleElementRename(newElementName: String): PsiElement {
        val leaf = element as? LeafPsiElement ?: return element
        return leaf.replaceWithText(newElementName).psi
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
```

- [ ] **Step 3c: Wire it into the reference helper**

In `AntlersDefinitionReferenceHelper.kt`, find:
```kotlin
        val idents = path.node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }
        val index = idents.indexOfFirst { it.psi == element }

        if (index == 0) {
```
and insert this block **between** the `val index = …` line and the `if (index == 0) {` line:
```kotlin
        // `{{ view:<name> }}` — the second ident resolves to the front-matter key.
        if (index == 1 && idents.firstOrNull()?.psi?.text == "view") {
            return arrayOf(AntlersViewVariableReference(element, name))
        }

```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersViewVariableReferenceTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVarDeclaration.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVariableReference.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersViewVariableReferenceTest.kt
git commit -m "$(cat <<'EOF'
feat: go-to-definition from `view:foo` to its front-matter key

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Hover docs

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersViewVarDocTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersViewVarDocTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersViewVarDocTest : BasePlatformTestCase() {

    fun testViewVarDocShowsNameAndValue() {
        myFixture.configureByText("p.antlers.html", "---\nfoo: bar\n---\n{{ view:fo<caret>o }}")
        val ident = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val doc = AntlersDocumentationProvider().generateDoc(ident, ident)
        assertNotNull("view:foo has documentation", doc)
        assertTrue("mentions it is a view variable, got: $doc", doc!!.contains("View variable"))
        assertTrue("shows the value, got: $doc", doc.contains("bar"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersViewVarDocTest"`
Expected: FAIL — `doc` is null (no `view:` doc branch yet).

- [ ] **Step 3: Add the doc branch**

In `AntlersDocumentationProvider.generateDoc`, the non-head path begins with:
```kotlin
            val idents = path.node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }
            val index = idents.indexOfFirst { it.psi == ident }
            if (index > 0) {
```
Insert this block **between** the `val index = …` line and the `if (index > 0) {` line:
```kotlin
            if (index == 1 && idents.firstOrNull()?.psi?.text == "view") {
                val entry = com.github.balotias.intellijantlers.view.ViewFrontMatterService
                    .getInstance(ident.project).topLevel(ident.containingFile)
                    .firstOrNull { it.name == name } ?: return null
                return section("View variable <b>${esc(name)}</b>", entry.valuePreview, "")
            }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersViewVarDocTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersViewVarDocTest.kt
git commit -m "$(cat <<'EOF'
feat: hover docs for `view:` front-matter variables

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: B-visual — YAML-style block highlighting

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightAnnotator.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFrontMatterHighlightTest : BasePlatformTestCase() {

    private fun keyOver(text: String, token: String): TextAttributesKey? {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .firstOrNull { it.text == token && it.forcedTextAttributesKey != null }
            ?.forcedTextAttributesKey
    }

    fun testFenceColored() =
        assertEquals(AntlersSyntaxHighlighter.FRONTMATTER_FENCE, keyOver("---\nfoo: bar\n---\n{{ title }}", "---"))

    fun testKeyColored() =
        assertEquals(AntlersSyntaxHighlighter.FRONTMATTER_KEY, keyOver("---\nfoo: bar\n---\n{{ title }}", "foo"))

    fun testValueColored() =
        assertEquals(AntlersSyntaxHighlighter.FRONTMATTER_VALUE, keyOver("---\nfoo: bar\n---\n{{ title }}", "bar"))

    fun testNoFrontMatterNotColored() =
        assertNull(keyOver("{{ title }}\nfoo: bar", "foo"))

    fun testOnlyFrontMatterKeyIsKeyColored() {
        // The `foo` in the body `{{ view:foo }}` must NOT get the front-matter key color.
        myFixture.configureByText("p.antlers.html", "---\nfoo: bar\n---\n{{ view:foo }}")
        val keyColored = myFixture.doHighlighting()
            .filter { it.text == "foo" && it.forcedTextAttributesKey == AntlersSyntaxHighlighter.FRONTMATTER_KEY }
        assertEquals("only the front-matter key is FM-key-colored", 1, keyColored.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersFrontMatterHighlightTest"`
Expected: FAIL — compile error (`FRONTMATTER_FENCE` unresolved).

- [ ] **Step 3a: Add the 3 color keys**

In `AntlersSyntaxHighlighter.kt`, after the `val PARAMETER = …` line in the companion object, add:
```kotlin
        val FRONTMATTER_FENCE = TextAttributesKey.createTextAttributesKey("ANTLERS_FRONTMATTER_FENCE", DefaultLanguageHighlighterColors.METADATA)
        val FRONTMATTER_KEY = TextAttributesKey.createTextAttributesKey("ANTLERS_FRONTMATTER_KEY", DefaultLanguageHighlighterColors.KEYWORD)
        val FRONTMATTER_VALUE = TextAttributesKey.createTextAttributesKey("ANTLERS_FRONTMATTER_VALUE", DefaultLanguageHighlighterColors.STRING)
```

- [ ] **Step 3b: Write the annotator**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightAnnotator.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.psi.AntlersFile
import com.github.balotias.intellijantlers.view.TextSpan
import com.github.balotias.intellijantlers.view.ViewFrontMatterService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * YAML-style highlighting of a view's `---…---` front-matter block (B-visual). The block flows to HTML
 * as plain text; this overlays colors on the fences/keys/values (the same INFORMATION-overlay mechanism
 * as the other Antlers annotators). Coloring only — not a real YAML editor (see spec: B-real follow-up).
 */
class AntlersFrontMatterHighlightAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is AntlersFile) return                    // run once per file
        val fm = ViewFrontMatterService.getInstance(element.project).frontMatter(element) ?: return
        paint(holder, fm.openFence, AntlersSyntaxHighlighter.FRONTMATTER_FENCE)
        paint(holder, fm.closeFence, AntlersSyntaxHighlighter.FRONTMATTER_FENCE)
        for (e in fm.entries) {
            paint(holder, e.key, AntlersSyntaxHighlighter.FRONTMATTER_KEY)
            e.value?.let { paint(holder, it, AntlersSyntaxHighlighter.FRONTMATTER_VALUE) }
        }
    }

    private fun paint(holder: AnnotationHolder, span: TextSpan, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(span.start, span.end))
            .textAttributes(key)
            .create()
    }
}
```

- [ ] **Step 3c: Register the annotator**

In `src/main/resources/META-INF/plugin.xml`, immediately after the line:
```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersStringInterpolationAnnotator"/>
```
add:
```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersFrontMatterHighlightAnnotator"/>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersFrontMatterHighlightTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Register the 3 keys in the color settings page**

In `AntlersColorSettingsPage.kt`:

(i) add three entries to the map returned by `getAdditionalHighlightingTagToDescriptorMap()` (after the `"param"` entry):
```kotlin
            "fmfence" to AntlersSyntaxHighlighter.FRONTMATTER_FENCE,
            "fmkey" to AntlersSyntaxHighlighter.FRONTMATTER_KEY,
            "fmval" to AntlersSyntaxHighlighter.FRONTMATTER_VALUE,
```

(ii) add three `AttributesDescriptor`s to `DESCRIPTORS` (after the `"Operator"` one):
```kotlin
            AttributesDescriptor("Front matter//Fence", AntlersSyntaxHighlighter.FRONTMATTER_FENCE),
            AttributesDescriptor("Front matter//Key", AntlersSyntaxHighlighter.FRONTMATTER_KEY),
            AttributesDescriptor("Front matter//Value", AntlersSyntaxHighlighter.FRONTMATTER_VALUE),
```

(iii) replace the `DEMO` string with one that includes a front-matter block:
```kotlin
        private val DEMO = """
            <fmfence>---</fmfence>
            <fmkey>title</fmkey>: <fmval>"My Page"</fmval>
            <fmfence>---</fmfence>
            {{# Featured posts #}}
            {{ <tag>collection</tag>:blog <param>limit</param>="3" <param>as</param>="posts" }}
              {{ title | <mod>upper</mod> }}
              {{ <kw>if</kw> count > 0 }}{{ price }}{{ /collection }}
        """.trimIndent()
```

- [ ] **Step 6: Verify the color-settings page still loads + full feature compiles**

Run: `./gradlew --rerun-tasks test --tests "*AntlersFrontMatterHighlightTest" --tests "*AntlersColorSettingsPage*"`
Expected: PASS (the highlight test still green; if there is no color-settings test, this just re-runs the highlight test and compiles the changed page — BUILD SUCCESSFUL). If `compileKotlin` fails on the page edit, fix the edit; do not skip it.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightAnnotator.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightTest.kt
git commit -m "$(cat <<'EOF'
feat: YAML-style highlighting of the view front-matter block (B-visual)

3 themeable color keys (fence/key/value) overlaid by an AntlersFile-gated
annotator; registered in the color settings page. Highlighting only.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Full-suite regression gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml && echo FAIL || echo "GATE CLEAN"`
Expected: `GATE CLEAN`.

If a pre-existing completion / reference / docs / highlight test changed: this feature is additive and gated
behind `view:`/front-matter presence, so it must not alter other behavior. Investigate before proceeding
(most likely the `view` head suggestion leaking into a no-front-matter completion, or the annotator painting
outside the block). Do not edit other tests to go green.

## Self-Review

- **Spec coverage:** scanner (§1, strict leading+closing fence, top-level vs nested, offsets, BOM) → Task 1;
  service (§2, cached per-file) → Task 2; completion `view:` keys + `view` head (§3) → Task 3; reference (§4,
  precise navigation via synthetic declaration) → Task 4; docs (§5) → Task 5; B-visual annotator + 3 color
  keys + color-settings registration (§6, §7) → Task 6; no-flag (§ "no-flag") needs no code (verified, noted
  in spec); full-suite gate → Task 7. No gaps.
- **Placeholder scan:** none — every code step shows complete code; no "handle edge cases"/TBD.
- **Type consistency:** `ViewFrontMatterScanner.scan(String): FrontMatter?`, `FrontMatter(block, openFence,
  closeFence, entries)`, `FmEntry(name, indent, key, value, valuePreview)`, `TextSpan(start, end)` defined in
  Task 1 and used identically in Tasks 2/4/5/6. `ViewFrontMatterService.frontMatter(PsiFile)`/`topLevel(PsiFile)`
  defined in Task 2, used in 3/4/5/6. `AntlersViewVariableReference(element, name)` /
  `AntlersViewVarDeclaration(project, varName, file, offset)` defined and used consistently in Task 4.
  `FRONTMATTER_FENCE`/`KEY`/`VALUE` defined in Task 6 Step 3a, used in 3b/5 and the test. The completion seam
  matches the verified classifier (`view:` → `TAG_METHOD`, `tagHead="view"`, `pathPrefix=["view"]`); the
  reference/docs seams match the verified index-based namePath wiring (head `view` = index 0, key = index 1).
