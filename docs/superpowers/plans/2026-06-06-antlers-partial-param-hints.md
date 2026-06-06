# Antlers Partial `@param` Hints — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At a partial include site (`{{ partial:components/button … }}`), surface the included partial's declared `@param`s as parameter-name autocomplete and quick documentation.

**Architecture:** A small new module `AntlersPartialParams` parses a partial file's leading `{{# @param … #}}` directives into `PartialParam(name, required, description)`. A reused resolver `AntlersPartialReferenceHelper.includedPartialFile(statement)` returns the included partial's `PsiFile`. Two existing extension points are extended: the completion `PARAMETER` branch and the documentation provider's parameter case. No new caching (deferred).

**Tech Stack:** Kotlin, IntelliJ Platform SDK; tests via JUnit (pure) and `BasePlatformTestCase` (platform). Build/test: `./gradlew --rerun-tasks test`.

**Spec:** `docs/superpowers/specs/2026-06-06-antlers-partial-param-hints-design.md`

**Conventions for every commit:** branch off `main` first (a feature branch already in use is fine); end each commit message with the trailer:
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```
Gate the full suite with: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

---

## File Structure

- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParams.kt` — `PartialParam` data class + pure `fromDirectiveValue` + PSI reader `of(partialFile)`.
- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceHelper.kt` — add `includedPartialFile(statement)` and `hasColonPath(statement)`.
- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt` — extend the `PARAMETER` branch.
- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt` — partial-param doc case.
- **Modify** `README.md` — note the new behavior.
- **Create tests** under `references/`, `completion/`, `documentation/`.

---

## Task 1: `PartialParam` + pure `fromDirectiveValue`

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParams.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParamsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParamsTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.references.AntlersPartialParams.PartialParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AntlersPartialParamsTest {

    @Test fun requiredStarOnDirective() =
        assertEquals(
            PartialParam("label", true, "The caption label."),
            AntlersPartialParams.fromDirectiveValue("* label The caption label.")
        )

    @Test fun optionalParam() =
        assertEquals(
            PartialParam("as", false, "The wrapping element. Defaults to `a`."),
            AntlersPartialParams.fromDirectiveValue("as The wrapping element. Defaults to `a`.")
        )

    @Test fun nameOnlyNoDescription() =
        assertEquals(PartialParam("faux", false, ""), AntlersPartialParams.fromDirectiveValue("faux"))

    @Test fun starAttachedToName() =
        assertEquals(PartialParam("label", true, "x"), AntlersPartialParams.fromDirectiveValue("*label x"))

    @Test fun emptyOrStarOnlyIsNull() {
        assertNull(AntlersPartialParams.fromDirectiveValue(""))
        assertNull(AntlersPartialParams.fromDirectiveValue("   "))
        assertNull(AntlersPartialParams.fromDirectiveValue("*"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersPartialParamsTest"`
Expected: FAIL to compile — `AntlersPartialParams` is unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParams.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersFrontMatter
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersHintParser
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

/**
 * Reads a partial's declared parameters from its leading `{{# @param … #}}` directive comment.
 * Mirrors [com.github.balotias.intellijantlers.blueprint.AntlersViewHints]: the directive block is the
 * first comment before any real content. `fromDirectiveValue` is pure (unit-testable, IntelliJ-free).
 */
object AntlersPartialParams {

    /** One `@param[*] <name> <description>` declaration. */
    data class PartialParam(val name: String, val required: Boolean, val description: String)

    /** Decompose a `@param` directive value into a [PartialParam]; null when there is no name token. */
    fun fromDirectiveValue(value: String): PartialParam? {
        var v = value.trim()
        var required = false
        if (v.startsWith("*")) { required = true; v = v.removePrefix("*").trim() }
        if (v.isEmpty()) return null
        val parts = v.split(Regex("\\s+"), limit = 2)
        val name = parts[0]
        if (name.isEmpty()) return null
        return PartialParam(name, required, parts.getOrElse(1) { "" }.trim())
    }

    /** The `@param` declarations in [partialFile]'s leading directive comment, in declaration order. */
    fun of(partialFile: PsiFile): List<PartialParam> {
        val antlers = partialFile.viewProvider.getPsi(AntlersLanguage.INSTANCE) ?: partialFile
        val body = leadingCommentBody(antlers) ?: return emptyList()
        return AntlersHintParser.parse(body)
            .filter { it.name == "@param" }
            .mapNotNull { fromDirectiveValue(it.value) }
            .distinctBy { it.name }
    }

    private fun leadingCommentBody(file: PsiFile): String? {
        for (child in file.children) {
            if (child is PsiWhiteSpace || child is AntlersFrontMatter) continue
            if (child is PsiComment) {
                if (child.node.elementType == AntlersTypes.T_COMMENT_TEXT) return child.text
                continue
            }
            break
        }
        return null
    }
}
```

Note: `AntlersHintParser.Directive.name` keeps the leading `@` (e.g. `"@param"`), so the filter compares to `"@param"`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersPartialParamsTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParams.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParamsTest.kt
git commit -m "feat(partials): parse @param declarations into PartialParam

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `of(partialFile)` reads a real partial file

**Files:**
- Modify: (none — `of` already written in Task 1)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParamsFileTest.kt`

This task verifies `of` against a real PSI file (Task 1's unit tests only covered the pure decomposition).

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParamsFileTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialParamsFileTest : BasePlatformTestCase() {

    private val button = """
        {{#
            @name Button attributes
            @desc A single button component.
            @param* label The caption label.
            @param as The wrapping element. Defaults to `a`.
            @param button_type `Inline` if the button needs to be rendered as an inline button.
            @param faux Boolean. For faux button wrapped in an actual button/anchor.
        #}}

        <!-- /components/_button.antlers.html -->
        {{ if label }}<span>{{ label }}</span>{{ /if }}
    """.trimIndent()

    fun testReadsParamsInOrder() {
        val file = myFixture.addFileToProject("resources/views/components/_button.antlers.html", button)
        val params = AntlersPartialParams.of(file)
        assertEquals(listOf("label", "as", "button_type", "faux"), params.map { it.name })
        assertTrue("label is required", params.first { it.name == "label" }.required)
        assertFalse("as is optional", params.first { it.name == "as" }.required)
        assertEquals("The wrapping element. Defaults to `a`.", params.first { it.name == "as" }.description)
    }

    fun testNoDirectiveCommentYieldsEmpty() {
        val file = myFixture.addFileToProject("resources/views/components/_plain.antlers.html", "<div>no hints</div>")
        assertTrue(AntlersPartialParams.of(file).isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersPartialParamsFileTest"`
Expected: PASS already if Task 1's `of` is correct — but if it FAILS (e.g. `@param` filter or comment reading wrong), fix `of` in `AntlersPartialParams.kt` until green. (This test is the real-PSI proof for `of`; do not skip running it.)

- [ ] **Step 3: (only if Step 2 failed) fix `of`**

If the filter or `leadingCommentBody` is wrong, correct it in `AntlersPartialParams.kt`. No other code should change.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersPartialParamsFileTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialParamsFileTest.kt
git commit -m "test(partials): AntlersPartialParams.of reads a real partial file

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: `includedPartialFile` + `hasColonPath` resolver helpers

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceHelper.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersIncludedPartialFileTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersIncludedPartialFileTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersIncludedPartialFileTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject("resources/views/components/_button.antlers.html", "{{# @param* label x #}}")
    }

    private fun statementOf(text: String): AntlersStatement {
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text)
        return PsiTreeUtil.findChildOfType(file, AntlersStatement::class.java)!!
    }

    fun testResolvesColonForm() {
        setup()
        val stmt = statementOf("{{ partial:components/button }}")
        assertEquals("_button.antlers.html", AntlersPartialReferenceHelper.includedPartialFile(stmt)?.name)
        assertTrue(AntlersPartialReferenceHelper.hasColonPath(stmt))
    }

    fun testResolvesSrcForm() {
        setup()
        val stmt = statementOf("{{ partial src=\"components/button\" }}")
        assertEquals("_button.antlers.html", AntlersPartialReferenceHelper.includedPartialFile(stmt)?.name)
        assertFalse("src form has no colon path", AntlersPartialReferenceHelper.hasColonPath(stmt))
    }

    fun testUnresolvedReturnsNull() {
        setup()
        val stmt = statementOf("{{ partial:does/not/exist }}")
        assertNull(AntlersPartialReferenceHelper.includedPartialFile(stmt))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersIncludedPartialFileTest"`
Expected: FAIL to compile — `includedPartialFile` / `hasColonPath` unresolved.

- [ ] **Step 3: Write minimal implementation**

In `AntlersPartialReferenceHelper.kt`, add these imports near the top:

```kotlin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
```

(Note: `AntlersParameterMixin`, `AntlersStatement`, `AntlersNamePathMixin`, `PsiTreeUtil`, `PsiElement` are already imported.)

Change `private fun extractPartialPath(...)` to internal visibility so the helpers below and the completion provider can reuse it — replace the line:

```kotlin
    private fun extractPartialPath(statement: AntlersStatement): String? {
```

with:

```kotlin
    fun extractPartialPath(statement: AntlersStatement): String? {
```

Then add, inside the `AntlersPartialReferenceHelper` object (e.g. after `refsForIdent`):

```kotlin
    /** True when the include uses the colon/slash path form (`{{ partial:components/button }}`). */
    fun hasColonPath(statement: AntlersStatement): Boolean = extractPartialPath(statement) != null

    /**
     * The PsiFile of the partial that [statement] includes — from the colon/slash path or a `src=` value,
     * resolved via [StatamicProject.resolvePartial]. Null when there is no path or it does not resolve.
     */
    fun includedPartialFile(statement: AntlersStatement): PsiFile? {
        val path = extractPartialPath(statement) ?: srcParamValue(statement) ?: return null
        val vf = StatamicProject.resolvePartial(statement, path) ?: return null
        return PsiManager.getInstance(statement.project).findFile(vf)
    }

    private fun srcParamValue(statement: AntlersStatement): String? {
        val param = PsiTreeUtil.findChildrenOfType(statement, AntlersParameterMixin::class.java)
            .firstOrNull { it.parameterName == "src" } ?: return null
        val raw = param.valueElement?.text ?: return null
        return raw.removeSurrounding("\"").removeSurrounding("'").ifBlank { null }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersIncludedPartialFileTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceHelper.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersIncludedPartialFileTest.kt
git commit -m "feat(partials): resolve the included partial file from an include statement

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Parameter-name autocomplete from the partial's `@param`s

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt:218-227`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersPartialParamCompletionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersPartialParamCompletionTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialParamCompletionTest : BasePlatformTestCase() {

    private val button = """
        {{#
            @param* label The caption label.
            @param as The wrapping element.
            @param button_type Inline if needed.
            @param faux Boolean.
        #}}
        <button>{{ label }}</button>
    """.trimIndent()

    private fun setup() {
        myFixture.addFileToProject("resources/views/components/_button.antlers.html", button)
    }

    private fun completeIn(text: String): List<String> {
        setup()
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testColonFormOffersPartialParamsAndSuppressesSrc() {
        val items = completeIn("{{ partial:components/button <caret> }}")
        assertTrue("offers declared params: $items",
            items.containsAll(listOf("label", "as", "button_type", "faux")))
        assertFalse("colon form must not offer src: $items", items.contains("src"))
    }

    fun testSrcFormOffersPartialParamsAndKeepsSrc() {
        val items = completeIn("{{ partial src=\"components/button\" <caret> }}")
        assertTrue("offers declared params: $items", items.containsAll(listOf("label", "as")))
        assertTrue("src form keeps src: $items", items.contains("src"))
    }

    private fun completeElementsIn(text: String): Array<com.intellij.codeInsight.lookup.LookupElement> {
        setup()
        val file = myFixture.addFileToProject("resources/views/p2.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))
        return myFixture.completeBasic() ?: emptyArray()
    }

    fun testRequiredMarkerShown() {
        val label = completeElementsIn("{{ partial:components/button <caret> }}").first { it.lookupString == "label" }
        val p = LookupElementPresentation(); label.renderElement(p)
        assertEquals("Param*", p.typeText)
    }

    fun testUnresolvedPartialDoesNotCrash() {
        val items = completeIn("{{ partial:does/not/exist <caret> }}")
        assertFalse(items.contains("label"))   // no params, and no exception
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersPartialParamCompletionTest"`
Expected: FAIL — `testColonFormOffersPartialParamsAndSuppressesSrc` fails because the params aren't offered and `src` IS offered (current branch only offers `catalog.tag("partial").parameters` = `[src]`).

- [ ] **Step 3: Write minimal implementation**

In `AntlersCompletionProvider.kt`, add imports near the other `references` import:

```kotlin
import com.github.balotias.intellijantlers.references.AntlersPartialParams
import com.github.balotias.intellijantlers.references.AntlersPartialReferenceHelper
```

Replace the `PARAMETER` branch (lines 218-227):

```kotlin
            AntlersCompletionKind.PARAMETER ->
                catalog.tag(info.tagHead ?: "")?.parameters?.forEach { p ->
                    result.addElement(
                        LookupElementBuilder.create(p.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(if (p.required) "Param*" else "Param")
                            .withTailText(if (p.description.isNotBlank()) "  ${p.description}" else null, true)
                            .withInsertHandler(ParameterInsertHandler)
                    )
                }
```

with:

```kotlin
            AntlersCompletionKind.PARAMETER -> {
                val stmt = PsiTreeUtil.getParentOfType(parameters.position, AntlersStatement::class.java)
                val isPartial = info.tagHead == "partial" && stmt != null

                // Params declared by the included partial via `{{# @param … #}}`.
                if (isPartial) {
                    AntlersPartialReferenceHelper.includedPartialFile(stmt!!)?.let { pf ->
                        for (p in AntlersPartialParams.of(pf)) {
                            result.addElement(
                                LookupElementBuilder.create(p.name)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText(if (p.required) "Param*" else "Param")
                                    .withTailText(if (p.description.isNotBlank()) "  ${p.description}" else null, true)
                                    .withInsertHandler(ParameterInsertHandler)
                            )
                        }
                    }
                }

                // Catalog params (e.g. partial's `src`). Suppressed for the colon form so it shows only
                // the component's own params.
                val suppressCatalog = isPartial && AntlersPartialReferenceHelper.hasColonPath(stmt!!)
                if (!suppressCatalog) {
                    catalog.tag(info.tagHead ?: "")?.parameters?.forEach { p ->
                        result.addElement(
                            LookupElementBuilder.create(p.name)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText(if (p.required) "Param*" else "Param")
                                .withTailText(if (p.description.isNotBlank()) "  ${p.description}" else null, true)
                                .withInsertHandler(ParameterInsertHandler)
                        )
                    }
                }
            }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersPartialParamCompletionTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersPartialParamCompletionTest.kt
git commit -m "feat(partials): autocomplete partial @param names at the include site

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Quick documentation on a partial param name

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt:42-54`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersPartialParamDocTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersPartialParamDocTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialParamDocTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject(
            "resources/views/components/_button.antlers.html",
            "{{#\n@param* label The caption label.\n@param as The wrapping element.\n#}}\n<button></button>"
        )
    }

    private fun docAt(text: String): String? {
        setup()
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val element = file.findElementAt(caret) ?: file.findElementAt(caret - 1)!!
        return AntlersDocumentationProvider().generateDoc(element, element)
    }

    fun testOptionalParamDoc() {
        val doc = docAt("{{ partial:components/button a<caret>s=\"h2\" }}")
        assertNotNull("expected doc for partial param `as`", doc)
        assertTrue(doc!!.contains("The wrapping element."))
        assertTrue(doc.contains("optional"))
        assertTrue(doc.contains("_button.antlers.html"))
    }

    fun testRequiredParamDoc() {
        val doc = docAt("{{ partial:components/button l<caret>abel=\"Go\" }}")
        assertNotNull(doc)
        assertTrue(doc!!.contains("The caption label."))
        assertTrue(doc.contains("required"))
    }

    fun testUnknownParamNoDoc() {
        val doc = docAt("{{ partial:components/button no<caret>pe=\"x\" }}")
        assertNull("unknown partial param has no doc", doc)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.documentation.AntlersPartialParamDocTest"`
Expected: FAIL — `testOptionalParamDoc` returns null (current param branch finds no catalog `partial` param named `as` and returns null).

- [ ] **Step 3: Write minimal implementation**

In `AntlersDocumentationProvider.kt`, add imports:

```kotlin
import com.github.balotias.intellijantlers.references.AntlersPartialParams
import com.github.balotias.intellijantlers.references.AntlersPartialReferenceHelper
```

Replace the parameter block (lines 42-54):

```kotlin
        // Parameter: the identifier is the parameter name of a known tag.
        PsiTreeUtil.getParentOfType(ident, AntlersParameterMixin::class.java)?.let { param ->
            if (param.parameterName == name) {
                val tag = enclosingTag(ident, catalog) ?: return null
                val p = tag.parameters.firstOrNull { it.name == name } ?: return null
                val type = if (p.type.isNotBlank()) " : ${esc(p.type)}" else ""
                val req = if (p.required) " (required)" else ""
                return section(
                    "Parameter <b>${esc(name)}</b>$type$req — tag <code>${esc(tag.name)}</code>",
                    p.description,
                    tag.docUrl
                )
            }
        }
```

with:

```kotlin
        // Parameter: the identifier is the parameter name of a known tag, or a partial include param.
        PsiTreeUtil.getParentOfType(ident, AntlersParameterMixin::class.java)?.let { param ->
            if (param.parameterName == name) {
                // Partial include param: doc comes from the included partial's `{{# @param … #}}`.
                val stmt = PsiTreeUtil.getParentOfType(ident, AntlersStatement::class.java)
                val head = stmt?.let { PsiTreeUtil.findChildOfType(it, AntlersNamePathMixin::class.java)?.head }
                if (head == "partial" && stmt != null) {
                    AntlersPartialReferenceHelper.includedPartialFile(stmt)?.let { pf ->
                        AntlersPartialParams.of(pf).firstOrNull { it.name == name }?.let { pp ->
                            val req = if (pp.required) " (required)" else " (optional)"
                            return section(
                                "Parameter <b>${esc(name)}</b>$req — partial <code>${esc(pf.name)}</code>",
                                pp.description,
                                ""
                            )
                        }
                    }
                }
                val tag = enclosingTag(ident, catalog) ?: return null
                val p = tag.parameters.firstOrNull { it.name == name } ?: return null
                val type = if (p.type.isNotBlank()) " : ${esc(p.type)}" else ""
                val req = if (p.required) " (required)" else ""
                return section(
                    "Parameter <b>${esc(name)}</b>$type$req — tag <code>${esc(tag.name)}</code>",
                    p.description,
                    tag.docUrl
                )
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.documentation.AntlersPartialParamDocTest"`
Expected: PASS (3 tests). The provider keys off the T_IDENT under the caret (passed as both `element` and `originalElement`), matching the existing `AntlersDocumentationProviderTest` pattern.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersPartialParamDocTest.kt
git commit -m "feat(partials): quick docs for partial @param at the include site

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: README + full-suite verification

**Files:**
- Modify: `README.md`
- (verify) all tests

- [ ] **Step 1: Update README**

In `README.md`, under **Completion**, add a bullet (after the partial-path values bullet):

```markdown
- **Partial parameters**: at a partial include (`{{ partial:components/button … }}`), the parameters the
  partial declares with `{{# @param* label … #}}` directive comments are completed by name (required ones
  marked `*`), and hovering a param name shows its `@param` description.
```

Under **Navigation & docs**, extend the quick-documentation bullet to mention partial params (append to the existing sentence): `, and for partial-include parameters (from the partial's @param hints)`.

- [ ] **Step 2: Run the full suite**

Run: `./gradlew --rerun-tasks test`
Then gate: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` — must print nothing.
Expected: BUILD SUCCESSFUL, gate prints nothing. Test count up by ~17 from the new tests.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: README — partial @param autocomplete + quick docs

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Definition of Done

- `{{ partial:components/button <caret> }}` autocompletes `label/as/button_type/faux` (required marked `*`), and does **not** offer `src`.
- `{{ partial src="components/button" <caret> }}` autocompletes the same params **and** still offers `src`.
- Hovering / Ctrl-Q on a partial-include param name shows its `@param` description and required/optional status.
- Unresolved or hint-less partials degrade gracefully (no crash, fall back to catalog behavior).
- Full suite green; README updated.
```
