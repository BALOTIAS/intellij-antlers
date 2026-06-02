# Antlers Editor UX Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three live-editor defects — live templates doubling `{{ }}`, pair-tag completion dropping the caret in the body, and tag names not being colored distinctly — plus a live-template default nit. (Real Reformat block-indentation is a deferred follow-up, NOT in this plan.)

**Architecture:** Local/additive changes — one context method (`AntlersTemplateContextType`), one insert handler (`AntlersTagInsertHandler`), a new semantic-highlight annotator + three color keys + ColorSettingsPage descriptors, and one XML default. No parser/grammar/dependency change.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, `BasePlatformTestCase`. Tests run via `./gradlew test`; the build cache returns cached results — use `--rerun-tasks` and gate on `build/test-results/test/*.xml`.

**Verified facts:**
- Live templates embed `{{ }}`; `AntlersTemplateContextType.isInContext` is whole-file (`= context.file.fileType is AntlersFileType`).
- PSI "inside braces" markers: `AntlersStatement`, `AntlersComment`, `AntlersNoparseBlock`, `AntlersPhpBlock` (package `…psi`).
- Mixins: `AntlersConditionMixin.keyword`, `AntlersNamePathMixin.head`, `AntlersModifierMixin.modifierName`; the generated impls extend these mixin classes.
- The normal completion flow has `}}` ALREADY present (the typed-handler auto-inserts it), so `{{ collection<caret> }}` → the gap between the name and `}}` is whitespace-only.
- `AntlersCatalogService.getInstance(project).tag(name)` → non-null for a known tag.
- `AntlersSyntaxHighlighter` companion holds the 6 existing `TextAttributesKey`s (BRACES/IDENTIFIER/STRING/NUMBER/COMMENT/OPERATOR). `AntlersColorSettingsPage` has `getAttributeDescriptors()` (6) and `getAdditionalHighlightingTagToDescriptorMap()` returns `null`; tests `AntlersColorSettingsPageTest.testDescriptorsCoverExactlyTheSixKeys` + `testBasics` assert those.
- `AntlersLiveTemplatesTest` uses `TemplateActionContext.expanding(file, editor)`; `testInContextForAntlersFile` (caret 0 of `{{ x }}`) currently asserts `true` and `testNotInContextForPlainText` asserts a `.txt` is false.

---

### Task 1: Fix 1 — live templates only outside `{{ }}`

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/template/AntlersTemplateContextType.kt`
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/template/AntlersLiveTemplatesTest.kt`

- [ ] **Step 1: Replace the two `isInContext` location tests** in `AntlersLiveTemplatesTest`

Delete `testInContextForAntlersFile` and `testNotInContextForPlainText`; add:

```kotlin
    fun testInContextInHtmlRegion() {
        myFixture.configureByText("page.antlers.html", "<div><caret></div>")
        val ctx = TemplateActionContext.expanding(myFixture.file, myFixture.editor)
        assertTrue(AntlersTemplateContextType().isInContext(ctx))
    }

    fun testNotInContextInsideBraces() {
        myFixture.configureByText("page.antlers.html", "{{ <caret> }}")
        val ctx = TemplateActionContext.expanding(myFixture.file, myFixture.editor)
        assertFalse(AntlersTemplateContextType().isInContext(ctx))
    }

    fun testNotInContextForPlainText() {
        myFixture.configureByText("note.txt", "hel<caret>lo")
        val ctx = TemplateActionContext.expanding(myFixture.file, myFixture.editor)
        assertFalse(AntlersTemplateContextType().isInContext(ctx))
    }
```

Keep `testBundledTemplatesParseAndAreAntlersScoped` unchanged.

- [ ] **Step 2: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.template.AntlersLiveTemplatesTest"`
Expected: FAIL — `testNotInContextInsideBraces` fails (current code returns true everywhere).

- [ ] **Step 3: Implement**

Replace the class body of `AntlersTemplateContextType.kt`:

```kotlin
package com.github.balotias.intellijantlers.template

import com.github.balotias.intellijantlers.AntlersFileType
import com.github.balotias.intellijantlers.psi.AntlersComment
import com.github.balotias.intellijantlers.psi.AntlersNoparseBlock
import com.github.balotias.intellijantlers.psi.AntlersPhpBlock
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.util.PsiTreeUtil

/** Scopes the bundled live templates to Antlers files, but ONLY outside an existing `{{ }}` — the
 *  templates embed their own delimiters, so firing them inside braces would double them. */
class AntlersTemplateContextType : TemplateContextType("Antlers") {
    override fun isInContext(context: TemplateActionContext): Boolean {
        val file = context.file
        if (file.fileType !is AntlersFileType) return false
        val el = file.findElementAt(context.startOffset) ?: return true
        return PsiTreeUtil.getParentOfType(
            el,
            AntlersStatement::class.java,
            AntlersComment::class.java,
            AntlersNoparseBlock::class.java,
            AntlersPhpBlock::class.java
        ) == null
    }
}
```

Note: `AntlersComment`/`AntlersNoparseBlock`/`AntlersPhpBlock`/`AntlersStatement` are the generated PSI interfaces under `…psi`. If any import is the `*Mixin` class rather than the generated interface, use whichever type the generated impls satisfy (the `*Impl` extends the mixin AND implements the interface, so either resolves at runtime — prefer the generated interface). If a class name differs, find it under `src/main/gen/com/github/balotias/intellijantlers/psi/`.

- [ ] **Step 4: Run, confirm pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.template.AntlersLiveTemplatesTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/template/AntlersTemplateContextType.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/template/AntlersLiveTemplatesTest.kt
git commit -m "Scope Antlers live templates to outside {{ }} (fix doubled delimiters)"
```

---

### Task 2: Fix 2 — pair-tag caret to the opening param slot (+ Nit A)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt`
- Modify: `src/main/resources/liveTemplates/Antlers.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersTagInsertTest : BasePlatformTestCase() {

    private fun completeTag(textWithCaret: String, tag: String) {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.completeBasic()
        myFixture.lookup?.let { lookup ->
            val item = lookup.items.firstOrNull { it.lookupString == tag }
            if (item != null) {
                lookup.currentItem = item
                myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
            }
        }
    }

    fun testPairTagCaretInParamSlot() {
        completeTag("{{ collection<caret> }}", "collection")
        assertEquals("{{ collection  }}{{ /collection }}", myFixture.file.text)
        // caret sits between the two spaces of the opening tag (param slot)
        assertEquals("{{ collection ".length, myFixture.caretOffset)
    }

    fun testSingleTagUnchanged() {
        // `partial` is a single (non-pair) tag: no `{{ /partial }}`, caret right after the name.
        completeTag("{{ partial<caret> }}", "partial")
        assertEquals("{{ partial }}", myFixture.file.text)
        assertEquals("{{ partial".length, myFixture.caretOffset)
    }
}
```

- [ ] **Step 2: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersTagInsertTest"`
Expected: FAIL — `testPairTagCaretInParamSlot` gets the old multi-line body output / caret in the body.

- [ ] **Step 3: Rewrite `AntlersTagInsertHandler.handleInsert`**

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Inserts an Antlers tag. The platform has already inserted the bare name with the caret right after
 * it. Single tags: ensure the opener closes, caret stays after the name. Pair tags: produce
 * `{{ name <caret> }}{{ /name }}` with the caret in the opening-tag parameter slot (one space each
 * side), the closer inline — the user expands to a block with Enter; no forced indent.
 */
class AntlersTagInsertHandler(private val isPair: Boolean) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val name = item.lookupString
        val nameEnd = context.tailOffset
        val text = document.charsSequence.toString()

        val nextClose = text.indexOf("}}", nameEnd)
        val nextOpen = text.indexOf("{{", nameEnd)
        val alreadyClosed = nextClose >= 0 && (nextOpen < 0 || nextClose < nextOpen)

        if (!isPair) {
            if (!alreadyClosed) {
                val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
                document.insertString(nameEnd, if (needsSpace) " }}" else "}}")
            }
            context.editor.caretModel.moveToOffset(nameEnd)
            context.commitDocument()
            return
        }

        // Pair tag.
        val openTagEnd: Int
        val paramCaret: Int
        if (alreadyClosed) {
            val gap = text.substring(nameEnd, nextClose)
            if (gap.isBlank()) {
                // `{{ name<gap>}}` → normalize to `{{ name  }}`, caret centred.
                document.replaceString(nameEnd, nextClose, "  ")
                paramCaret = nameEnd + 1
                openTagEnd = nameEnd + 4               // "  " + "}}"
            } else {
                // Existing params in the opener → caret right after the name.
                paramCaret = nameEnd
                openTagEnd = nextClose + 2
            }
        } else {
            val needsSpace = nameEnd > 0 && text[nameEnd - 1] != ' '
            val opener = if (needsSpace) "  }}" else " }}"
            document.insertString(nameEnd, opener)
            paramCaret = nameEnd + (if (needsSpace) 1 else 0)
            openTagEnd = nameEnd + opener.length
        }

        document.insertString(openTagEnd, "{{ /$name }}")
        context.editor.caretModel.moveToOffset(paramCaret)
        context.commitDocument()
    }
}
```

- [ ] **Step 4: Fix Nit A — `coll` template default in `liveTemplates/Antlers.xml`**

Change the `coll` template's variable default from quoted to bare (the colon form takes a bare handle):

```xml
    <variable name="HANDLE" expression="" defaultValue="blog" alwaysStopAt="true"/>
```

(Replace the existing `defaultValue="&quot;blog&quot;"` on the `coll` template's `<variable>`.)

- [ ] **Step 5: Run, confirm pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.completion.AntlersTagInsertTest"`
Expected: PASS (2 tests). If `testSingleTagUnchanged` fails because `partial` completion adds something else, confirm `partial` is `isPair=false` in the catalog and that the single branch leaves `{{ partial }}` with caret after the name.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertHandler.kt \
        src/main/resources/liveTemplates/Antlers.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersTagInsertTest.kt
git commit -m "Pair-tag completion: caret in opening param slot, inline closer (no forced body); fix coll template default"
```

---

### Task 3: Fix 4 — semantic tag/keyword/modifier highlighting

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt` (add 3 keys)
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersSemanticHighlightAnnotator.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt` (descriptors + preview map)
- Modify: `src/main/resources/META-INF/plugin.xml` (register annotator)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersSemanticHighlightTest.kt` (create)
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPageTest.kt`

- [ ] **Step 1: Add the 3 color keys** to `AntlersSyntaxHighlighter` companion (after `OPERATOR`):

```kotlin
        val TAG = TextAttributesKey.createTextAttributesKey("ANTLERS_TAG", DefaultLanguageHighlighterColors.KEYWORD)
        val KEYWORD = TextAttributesKey.createTextAttributesKey("ANTLERS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val MODIFIER = TextAttributesKey.createTextAttributesKey("ANTLERS_MODIFIER", DefaultLanguageHighlighterColors.INSTANCE_METHOD)
```

(These are applied by the annotator, not the lexer — do NOT add them to `getTokenHighlights`.)

- [ ] **Step 2: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersSemanticHighlightTest : BasePlatformTestCase() {

    private fun keyOver(text: String, token: String): TextAttributesKey? {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .firstOrNull { it.text == token && it.forcedTextAttributesKey != null }
            ?.forcedTextAttributesKey
    }

    fun testTagHeadColored() =
        assertEquals(AntlersSyntaxHighlighter.TAG, keyOver("{{ collection:blog }}", "collection"))

    fun testConditionKeywordColored() =
        assertEquals(AntlersSyntaxHighlighter.KEYWORD, keyOver("{{ if count > 0 }}{{ /if }}", "if"))

    fun testModifierColored() =
        assertEquals(AntlersSyntaxHighlighter.MODIFIER, keyOver("{{ title | upper }}", "upper"))

    fun testPlainVariableNotColored() =
        assertNull(keyOver("{{ title }}", "title"))
}
```

- [ ] **Step 2b: Run, confirm failure**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.editor.AntlersSemanticHighlightTest"`
Expected: FAIL — no annotator yet, so `keyOver` returns null.

Note for the implementer: `HighlightInfo.getForcedTextAttributesKey()` is the field set by
`AnnotationBuilder.textAttributes(key)`. If that accessor isn't available in this SDK, find the
HighlightInfo whose range matches the token and assert its attributes key via whatever accessor the
SDK exposes — the contract is "the `collection`/`if`/`upper` token carries the TAG/KEYWORD/MODIFIER
key and a plain `title` carries none."

- [ ] **Step 3: Create the annotator**

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement

/**
 * Semantic highlighting that the lexer can't do: T_IDENT is every identifier, so this paints the
 * tag head, condition keyword, and modifier name distinctly. Variables/fields/params keep the plain
 * IDENTIFIER color.
 */
class AntlersSemanticHighlightAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is AntlersConditionMixin ->
                firstIdent(element)?.let { paint(holder, it, AntlersSyntaxHighlighter.KEYWORD) }

            is AntlersModifierMixin ->
                firstIdent(element)?.let { paint(holder, it, AntlersSyntaxHighlighter.MODIFIER) }

            is AntlersNamePathMixin -> {
                if (AntlersCatalogService.getInstance(element.project).tag(element.head) != null) {
                    firstIdent(element)?.let { paint(holder, it, AntlersSyntaxHighlighter.TAG) }
                }
            }
        }
    }

    private fun firstIdent(element: PsiElement): PsiElement? =
        element.node.findChildByType(AntlersTypes.T_IDENT)?.psi

    private fun paint(holder: AnnotationHolder, leaf: PsiElement, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(leaf)
            .textAttributes(key)
            .create()
    }
}
```

Note: the generated PSI impls extend these `*Mixin` classes, so `is AntlersConditionMixin` etc. match
at runtime. If the BNF binds the mixins to different generated impl names, match whichever type the
impls actually are (check `src/main/gen/.../psi/impl/`).

- [ ] **Step 4: Register the annotator** in `plugin.xml` (below the existing balance annotator line):

```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersSemanticHighlightAnnotator"/>
```

- [ ] **Step 5: Run, confirm the semantic test passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.editor.AntlersSemanticHighlightTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Surface the 3 keys in the ColorSettingsPage** (descriptors + preview)

In `AntlersColorSettingsPage.kt`: add the three descriptors to `DESCRIPTORS`, wrap the demo tokens, and
return the highlight-tag map. Replace `getAdditionalHighlightingTagToDescriptorMap()`, `DESCRIPTORS`,
and `DEMO`:

```kotlin
    override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey> =
        mutableMapOf(
            "tag" to AntlersSyntaxHighlighter.TAG,
            "kw" to AntlersSyntaxHighlighter.KEYWORD,
            "mod" to AntlersSyntaxHighlighter.MODIFIER,
        )
```

```kotlin
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Braces & delimiters", AntlersSyntaxHighlighter.BRACES),
            AttributesDescriptor("Tag name", AntlersSyntaxHighlighter.TAG),
            AttributesDescriptor("Condition keyword", AntlersSyntaxHighlighter.KEYWORD),
            AttributesDescriptor("Modifier", AntlersSyntaxHighlighter.MODIFIER),
            AttributesDescriptor("Identifier", AntlersSyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("String", AntlersSyntaxHighlighter.STRING),
            AttributesDescriptor("Number", AntlersSyntaxHighlighter.NUMBER),
            AttributesDescriptor("Comment", AntlersSyntaxHighlighter.COMMENT),
            AttributesDescriptor("Operator", AntlersSyntaxHighlighter.OPERATOR),
        )

        private val DEMO = """
            {{# Featured posts #}}
            {{ <tag>collection</tag>:blog limit="3" as="posts" }}
              {{ title | <mod>upper</mod> }}
              {{ <kw>if</kw> count > 0 }}{{ price }}{{ /collection }}
        """.trimIndent()
```

- [ ] **Step 7: Update the ColorSettingsPage tests** in `AntlersColorSettingsPageTest`

Replace `testDescriptorsCoverExactlyTheSixKeys` and the `additionalHighlightingTagToDescriptorMap`
assertion in `testBasics`:

```kotlin
    fun testDescriptorsCoverAllKeys() {
        val keys = AntlersColorSettingsPage().attributeDescriptors.map { it.key }.toSet()
        val expected = setOf(
            AntlersSyntaxHighlighter.BRACES, AntlersSyntaxHighlighter.TAG, AntlersSyntaxHighlighter.KEYWORD,
            AntlersSyntaxHighlighter.MODIFIER, AntlersSyntaxHighlighter.IDENTIFIER, AntlersSyntaxHighlighter.STRING,
            AntlersSyntaxHighlighter.NUMBER, AntlersSyntaxHighlighter.COMMENT, AntlersSyntaxHighlighter.OPERATOR,
        )
        assertEquals(expected, keys)
    }
```

In `testBasics`, change `assertNull("no additional highlighting tags", page.additionalHighlightingTagToDescriptorMap)`
to:
```kotlin
        assertTrue("preview maps the 3 semantic tags",
            page.additionalHighlightingTagToDescriptorMap!!.keys == setOf("tag", "kw", "mod"))
```

- [ ] **Step 8: Run the ColorSettingsPage + semantic tests**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.highlighting.AntlersColorSettingsPageTest" --tests "com.github.balotias.intellijantlers.editor.AntlersSemanticHighlightTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersSemanticHighlightAnnotator.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersSemanticHighlightTest.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPageTest.kt
git commit -m "Semantic highlighting for Antlers tags, condition keywords, and modifiers"
```

---

### Task 4: Full-suite verification

- [ ] **Step 1:** `./gradlew --rerun-tasks test` → BUILD SUCCESSFUL.
- [ ] **Step 2:** `echo "failed_files=$(grep -lo 'failures=\"[1-9]\|errors=\"[1-9]' build/test-results/test/*.xml | wc -l | tr -d ' ')"` → `failed_files=0`. Note the total (prior 229 + the new tests, minus the removed live-template test).
- [ ] **Step 3:** `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL.

---

## Self-Review

**Spec coverage:** Fix 1 (context scoping) → Task 1; Fix 2 (pair caret) + Nit A (coll default) → Task 2; Fix 4 (semantic coloring: keys + annotator + ColorSettingsPage) → Task 3; verification → Task 4. Fix 3 (Reformat indentation) intentionally absent (deferred). Nit B (modifier `()`) intentionally unchanged.

**Placeholder scan:** none — all code shown; commands have expected output.

**Type/name consistency:** `AntlersSyntaxHighlighter.TAG/KEYWORD/MODIFIER` defined in Task 3 Step 1, consumed by the annotator (Step 3), the ColorSettingsPage (Step 6), and both test files. `AntlersTagInsertHandler(isPair)` signature unchanged. The annotator matches `AntlersConditionMixin`/`AntlersModifierMixin`/`AntlersNamePathMixin` (the verified mixin types).

**Known SDK-shape risks flagged inline:** generated PSI interface vs. mixin class for the "inside braces" check (Task 1 Step 3) and the annotator `is` checks (Task 3 Step 3); `HighlightInfo.forcedTextAttributesKey` accessor (Task 3 Step 2b). The implementer matches whatever compiles; the behavior contracts are pinned by the tests.
