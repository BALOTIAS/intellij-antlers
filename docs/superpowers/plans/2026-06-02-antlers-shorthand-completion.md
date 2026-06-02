# Antlers Shorthand Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the colon shorthand opt-in (typed `:coll`) with a synced, mirrored closer; keep the param form as the default; and warn on a shorthand-handle mismatch.

**Architecture:** Revert the default colon insert; add a `TAG_SHORTHAND` completion context for `{{ :…`; offer shorthand tags whose insert starts a `TemplateManager` template with a synced `HANDLE`; mirror the `coll` live template closer; and add a handle-mismatch WARNING to the balance annotator. Additive; no parser/grammar change.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`TemplateManager`, `Annotator`), `BasePlatformTestCase`. Tests run via `./gradlew test`; build cache returns cached results → use `--rerun-tasks`, gate on `build/test-results/test/*.xml`.

**Verified facts:**
- `AntlersTagInsertHandler` has a `colonHandleTags` set + a colon-form branch (to remove). Its normal pair path → `{{ name <caret> }}{{ /name }}`; single → caret after name.
- `AntlersCompletionContext.classify` `T_COLON` branch: `headOf(...)?.let{ TAG_METHOD } ?: NONE`. `prevSignificantLeaf(leaf, statement)` exists. `AntlersTypes.T_LDOUBLE`/`T_COLON`/`T_IDENT`.
- Provider's `when (info.kind)` is exhaustive — adding `TAG_SHORTHAND` needs a matching arm.
- Shorthand tags present in the catalog: `collection,taxonomy,nav,form,foreach,section,dictionary` (pair) + `partial` (single). `catalog.tag(name)?.isPair` gives pair-ness.
- `AntlersBalanceAnnotator` runs once on `AntlersFile`, walks `AntlersNestingTreeBuilder.build(...)` → `NestingTree(roots, unmatchedClosers)`; `NestingNode(opener, name, closer, children)`. `opener.namePath as AntlersNamePathMixin` (`.segments` = ident list), `(closer.closingTag as AntlersClosingTagMixin).closedName`.
- `AntlersCompletionTest.testCollectionInsertsClosingTag` (root package) currently asserts the colon form (from earlier work) — must revert.
- Branch: `antlers-shorthand-completion` (current).

---

### Task 1: Revert the default colon-form insert

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt`
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt`
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/AntlersCompletionTest.kt`

- [ ] **Step 1: Update the tests to the reverted (param-slot) default**

In `AntlersTagInsertTest`, replace `testCollectionPrefillsColonForm` and `testPartialPrefillsColonForm` with:
```kotlin
    fun testCollectionDefaultIsParamSlot() {
        completeTag("{{ collection<caret> }}", "collection")
        assertEquals("{{ collection  }}{{ /collection }}", myFixture.file.text)
        assertEquals("{{ collection ".length, myFixture.caretOffset)
    }

    fun testPartialDefaultSingle() {
        completeTag("{{ partial<caret> }}", "partial")
        assertEquals("{{ partial }}", myFixture.file.text)
        assertEquals("{{ partial".length, myFixture.caretOffset)
    }
```
In `AntlersCompletionTest`, change `testCollectionInsertsClosingTag`'s expected back to the param form:
```kotlin
        myFixture.checkResult("{{ collection <caret> }}{{ /collection }}")
```

- [ ] **Step 2: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersTagInsertTest" --tests "com.github.balotias.intellijantlers.AntlersCompletionTest"`
Expected: FAIL — handler still produces the colon form.

- [ ] **Step 3: Remove the colon-form branch from `AntlersTagInsertHandler`**

Delete the `private val colonHandleTags = …` property AND the entire `if (name in colonHandleTags) { … return }` block (the first branch in `handleInsert`). Leave the rest (single + pair logic) untouched.

- [ ] **Step 4: Run, confirm pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersTagInsertTest" --tests "com.github.balotias.intellijantlers.AntlersCompletionTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/AntlersCompletionTest.kt
git commit -m "Revert colon shorthand as the default tag-completion form (param slot is default)"
```

---

### Task 2: `:`-shorthand completion context + synced-template insert

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/completion/ShorthandTagInsertHandler.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContextTest.kt` (append)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersShorthandInsertTest.kt` (create)

- [ ] **Step 1: Append context tests**

```kotlin
    fun testShorthandColonAfterBrace() =
        assertEquals(AntlersCompletionKind.TAG_SHORTHAND, kindAt("{{ :coll<caret> }}"))

    fun testShorthandBareColon() =
        assertEquals(AntlersCompletionKind.TAG_SHORTHAND, kindAt("{{ :<caret> }}"))

    fun testMethodColonAfterHeadUnchanged() =
        assertEquals(AntlersCompletionKind.TAG_METHOD, kindAt("{{ collection:<caret> }}"))
```

- [ ] **Step 2: Write the shorthand insert test** `AntlersShorthandInsertTest.kt`

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersShorthandInsertTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    private fun completeShorthand(textWithCaret: String, lookupString: String) {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        myFixture.lookup?.let { lk ->
            val item = lk.items.firstOrNull { it.lookupString == lookupString } ?: return
            lk.currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }

    fun testCollectionShorthandMirrorsCloser() {
        completeShorthand("{{ :coll<caret> }}", "collection")
        myFixture.type("blog")
        assertEquals("{{ collection:blog }}{{ /collection:blog }}", myFixture.file.text)
    }

    fun testPartialShorthandSingleNoCloser() {
        completeShorthand("{{ :part<caret> }}", "partial")
        myFixture.type("cards")
        assertEquals("{{ partial:cards }}", myFixture.file.text)
    }
}
```
SDK note: `TemplateManagerImpl.setTemplateTesting` has had `(Disposable)` and `(Project, Disposable)` overloads across versions — use whichever compiles (`testRootDisposable` is available on the fixture base). If the live-template typing doesn't mirror in the test harness, fall back to asserting the template TEXT the insert handler produced (the doc right after `finishLookup`, before typing) contains both `{{ collection:` and `{{ /collection:` — the mirror is a platform guarantee for a repeated variable; the contract is "closer includes the handle placeholder".

- [ ] **Step 3: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersCompletionContextTest" --tests "com.github.balotias.intellijantlers.completion.AntlersShorthandInsertTest"`
Expected: FAIL — `TAG_SHORTHAND` doesn't exist; no shorthand offered.

- [ ] **Step 4: Add `TAG_SHORTHAND` to the context**

In `AntlersCompletionContext.kt`: add `TAG_SHORTHAND` to `AntlersCompletionKind`. Replace the `T_COLON` branch with:
```kotlin
            AntlersTypes.T_COLON ->
                if (prevSignificantLeaf(prev, statement)?.node?.elementType == AntlersTypes.T_LDOUBLE)
                    AntlersCompletionInfo(AntlersCompletionKind.TAG_SHORTHAND)
                else headOf(statement)?.let {
                    AntlersCompletionInfo(AntlersCompletionKind.TAG_METHOD, it, segmentsBeforeCaret(statement, position))
                } ?: AntlersCompletionInfo(AntlersCompletionKind.NONE)
```

- [ ] **Step 5: Create `ShorthandTagInsertHandler.kt` + `SHORTHAND_TAGS`**

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.TemplateManager

/** Tags that take a project handle via the `{{ tag:handle }}` colon shorthand. */
val SHORTHAND_TAGS = setOf("collection", "taxonomy", "nav", "form", "foreach", "section", "dictionary", "partial")

/**
 * Inserts a colon-shorthand tag as a live template with a synced `HANDLE` variable, so the handle
 * (typed once) fills both the opener and the mirrored closer: `{{ collection:blog }}{{ /collection:blog }}`.
 */
class ShorthandTagInsertHandler(private val tag: String, private val isPair: Boolean) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val text = document.charsSequence.toString()
        val caret = context.tailOffset
        val stmtStart = text.lastIndexOf("{{", caret)
        if (stmtStart < 0) return
        val closeIdx = text.indexOf("}}", caret)
        val stmtEnd = if (closeIdx in stmtStart until text.length) closeIdx + 2 else caret
        document.deleteString(stmtStart, stmtEnd)
        context.commitDocument()
        context.editor.caretModel.moveToOffset(stmtStart)

        val tm = TemplateManager.getInstance(context.project)
        val body = if (isPair) "{{ $tag:\$HANDLE\$ }}\$END\${{ /$tag:\$HANDLE\$ }}"
                   else "{{ $tag:\$HANDLE\$ }}\$END\$"
        val template = tm.createTemplate("", "", body)
        template.addVariable("HANDLE", "", "", true)
        template.isToReformat = false
        tm.startTemplate(context.editor, template)
    }
}
```

- [ ] **Step 6: Add the `TAG_SHORTHAND` arm to the provider**

In `AntlersCompletionProvider.addCompletions`'s `when (info.kind)`:
```kotlin
            AntlersCompletionKind.TAG_SHORTHAND -> {
                for (name in SHORTHAND_TAGS) {
                    val tag = catalog.tag(name) ?: continue
                    result.addElement(
                        LookupElementBuilder.create(name)
                            .withPresentableText(":$name")
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(if (tag.isPair) "Shorthand (block)" else "Shorthand")
                            .withInsertHandler(ShorthandTagInsertHandler(name, tag.isPair))
                    )
                }
            }
```

- [ ] **Step 7: Run, confirm pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersCompletionContextTest" --tests "com.github.balotias.intellijantlers.completion.AntlersShorthandInsertTest"`
Expected: PASS. If `testMethodColonAfterHeadUnchanged` fails, the `prevSignificantLeaf(prev)` for `{{ collection:<caret> }}` must be the `collection` ident (T_IDENT), not T_LDOUBLE — verify. If the shorthand insert test fails on the template mirror, apply the Step-2 fallback assertion.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/ShorthandTagInsertHandler.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContextTest.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersShorthandInsertTest.kt
git commit -m "Add :-shorthand tag completion with synced HANDLE + mirrored closer"
```

---

### Task 3: Mirror the `coll` live-template closer

**Files:**
- Modify: `src/main/resources/liveTemplates/Antlers.xml`
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/template/AntlersLiveTemplatesTest.kt`

- [ ] **Step 1: Append the failing assertion** to `testBundledTemplatesParseAndAreAntlersScoped` (or add a new test):

```kotlin
    fun testCollTemplateMirrorsCloser() {
        val text = javaClass.classLoader.getResourceAsStream("liveTemplates/Antlers.xml")!!
            .bufferedReader().readText()
        assertTrue("coll closer mirrors the handle: $text",
            text.contains("{{ /collection:\$HANDLE\$ }}"))
    }
```

- [ ] **Step 2: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.template.AntlersLiveTemplatesTest"`

- [ ] **Step 3: Update the `coll` template** value in `liveTemplates/Antlers.xml`

Change the `coll` template's `value` from `{{ collection:$HANDLE$ }}$END${{ /collection }}` to:
```xml
  <template name="coll" value="{{ collection:$HANDLE$ }}$END${{ /collection:$HANDLE$ }}" description="Antlers collection loop" toReformat="false" toShortenFQNames="false">
```
(Keep the `<variable name="HANDLE" …>` and `<context>` as-is.)

- [ ] **Step 4: Run, confirm pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.template.AntlersLiveTemplatesTest"`

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/liveTemplates/Antlers.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/template/AntlersLiveTemplatesTest.kt
git commit -m "Mirror the handle in the coll live-template closer"
```

---

### Task 4: Handle-mismatch diagnostic (WARNING)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotator.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotatorTest.kt` (append)

- [ ] **Step 1: Append the diagnostic tests** (use the existing test's highlight-filtering helper — inspect how it asserts; it filters `doHighlighting()` to this annotator's messages)

```kotlin
    fun testHandleMismatchWarns() {
        val warnings = warningsFor("{{ collection:blog }}{{ /collection:news }}")
        assertTrue("mismatch flagged: $warnings", warnings.any { it.contains("does not match") })
    }

    fun testHeadOnlyCloserNotFlagged() {
        val warnings = warningsFor("{{ collection:blog }}{{ /collection }}")
        assertTrue("head-only closer is valid: $warnings", warnings.none { it.contains("does not match") })
    }

    fun testMatchingHandleNotFlagged() {
        val warnings = warningsFor("{{ collection:blog }}{{ /collection:blog }}")
        assertTrue("matching handle ok: $warnings", warnings.none { it.contains("does not match") })
    }
```
Add a `warningsFor(text)` helper mirroring the existing test's pattern (configure text, `myFixture.doHighlighting()`, map the infos' descriptions; filter to this annotator). Match the existing test file's exact helper style.

- [ ] **Step 2: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.editor.AntlersBalanceAnnotatorTest"`
Expected: FAIL — `testHandleMismatchWarns` (no mismatch check yet).

- [ ] **Step 3: Add the mismatch walk to `AntlersBalanceAnnotator`**

Add the import `com.github.balotias.intellijantlers.psi.AntlersNamePathMixin`. After `reportUnclosed(tree.roots, holder)` in `annotate`, add `reportHandleMismatch(tree.roots, holder)`. Add:
```kotlin
    private fun reportHandleMismatch(nodes: List<NestingNode>, holder: AnnotationHolder) {
        for (n in nodes) {
            val closer = n.closer
            if (closer != null) {
                val openerHandle = (n.opener.namePath as? AntlersNamePathMixin)
                    ?.segments?.drop(1)?.joinToString(":") ?: ""
                val closerName = (closer.closingTag as? AntlersClosingTagMixin)?.closedName ?: ""
                val closerHandle = closerName.substringAfter(":", "")
                if (openerHandle.isNotEmpty() && closerHandle.isNotEmpty() && openerHandle != closerHandle) {
                    holder.newAnnotation(HighlightSeverity.WARNING,
                        "Closing handle ':$closerHandle' does not match the opening '{{ ${n.name}:$openerHandle }}'.")
                        .range(closer).create()
                }
            }
            reportHandleMismatch(n.children, holder)
        }
    }
```
(`n.opener.namePath` and `closer.closingTag` are the `AntlersStatement` accessors the nesting builder already uses.)

- [ ] **Step 4: Run, confirm pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.editor.AntlersBalanceAnnotatorTest"`
Expected: PASS. If `testHeadOnlyCloserNotFlagged` fails, the guard `closerHandle.isNotEmpty()` isn't holding — confirm `closedName.substringAfter(":", "")` returns `""` for `collection` (no colon).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotator.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersBalanceAnnotatorTest.kt
git commit -m "Warn on shorthand-handle mismatch between opener and closer"
```

---

### Task 5: Full-suite verification

- [ ] **Step 1:** `./gradlew --rerun-tasks test` → BUILD SUCCESSFUL.
- [ ] **Step 2:** `echo "failed_files=$(grep -lo 'failures=\"[1-9]\|errors=\"[1-9]' build/test-results/test/*.xml | wc -l | tr -d ' ')"` → `failed_files=0`. Note the total (prior 255 + new).
- [ ] **Step 3:** `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL.

---

## Self-Review

**Spec coverage:** revert default → Task 1; `TAG_SHORTHAND` context + synced-template insert (full shorthand set) → Task 2; live-template closer mirror → Task 3; handle-mismatch WARNING → Task 4; verification → Task 5. Out-of-scope (method colon, nearest-unclosed full path, escalating unclosed/stray to error) untouched.

**Placeholder scan:** none — all code shown; tests have concrete assertions, with documented fallbacks for the two SDK-shape unknowns.

**Type/name consistency:** `TAG_SHORTHAND` defined in Task 2 (context) and consumed in Task 2 (provider); `SHORTHAND_TAGS` + `ShorthandTagInsertHandler(tag, isPair)` defined and used in Task 2; the diagnostic uses `NestingNode.opener.namePath`/`closer.closingTag` exactly as `AntlersNestingTreeBuilder.build()` does. The `coll` template value string matches the test assertion.

**Known SDK-shape risks flagged inline:** `TemplateManagerImpl.setTemplateTesting` overload + the template-mirror-in-test behavior (Task 2 Step 2 fallback); `TemplateManager.createTemplate`/`addVariable` String overload (Task 2 Step 5 — if it wants `Expression`, use the String overload that exists). The implementer matches whatever compiles; the behavior contracts are pinned by the tests.
