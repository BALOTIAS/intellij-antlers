# Real PSI in String Interpolation (Antlers Self-Injection) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `{ … }` interpolation inside Antlers strings real PSI (completion, go-to-def, hover, modifier docs/Ctrl+P) by injecting Antlers — wrapped `{{ … }}` — into each span.

**Architecture:** Make the existing `AntlersStringLeaf` (custom `T_STRING` PSI leaf) a `PsiLanguageInjectionHost`; a `MultiHostInjector` injects `AntlersLanguage` into each `{ … }` content span with `"{{ "`/`" }}"` prefix/suffix; a manipulator round-trips edits; the existing interpolation annotator is slimmed to brace-only (injection colors the contents). No lexer/grammar/parser change.

**Tech Stack:** Kotlin, IntelliJ `MultiHostInjector` / `PsiLanguageInjectionHost` / `AbstractElementManipulator`, `BasePlatformTestCase`, `InjectedLanguageManager`. Build/test: `./gradlew --rerun-tasks test` (`--rerun-tasks` REQUIRED — Gradle caches). Gate: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

---

## File Structure

- `psi/AntlersLeafElements.kt` (modify) — `AntlersStringLeaf` implements `PsiLanguageInjectionHost`.
- `editor/AntlersStringLeafManipulator.kt` (create) — write-back for the leaf.
- `injection/AntlersStringInterpolationInjector.kt` (create) — the injector.
- `META-INF/plugin.xml` (modify) — register the manipulator + injector.
- `editor/AntlersStringInterpolationAnnotator.kt` (modify) — slim to brace-only.
- Tests: a host/manipulator test, an injection test, an updated highlight test.

Reference (read, don't change): `injection/AntlersPhpInjector.kt` and `editor/AntlersPhpBlockBodyManipulator.kt` (the patterns), `editor/AntlersInterpolationScanner.kt` (`scan(text): List<Span>`, `Span(openBrace, contentStart, contentEnd, closeBrace)`).

---

### Task 1: `AntlersStringLeaf` as injection host + manipulator

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersLeafElements.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringLeafManipulator.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersStringLeafHostTest.kt`

- [ ] **Step 1: Write the failing test**

Create `AntlersStringLeafHostTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersStringLeafHostTest : BasePlatformTestCase() {

    private fun stringLeaf(text: String): AntlersStringLeaf {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.collectElements(antlers) { it is AntlersStringLeaf }.first() as AntlersStringLeaf
    }

    fun testStringLeafIsValidHost() {
        assertTrue((stringLeaf("{{ x = \"title\" }}") as PsiLanguageInjectionHost).isValidHost)
    }

    fun testUpdateTextReplacesWholeLiteral() {
        val leaf = stringLeaf("{{ x = \"title\" }}")
        val updated = WriteCommandAction.runWriteCommandAction<PsiLanguageInjectionHost>(project) {
            leaf.updateText("\"name\"")
        }
        assertEquals("\"name\"", updated.text)
    }

    fun testUpdateTextBailsWhenEditBreaksTheString() {
        val leaf = stringLeaf("{{ x = \"title\" }}")
        // An unescaped inner quote would end the string early → not a single T_STRING → bail, text intact.
        val updated = WriteCommandAction.runWriteCommandAction<PsiLanguageInjectionHost>(project) {
            leaf.updateText("\"na\"me\"")
        }
        assertEquals("\"title\"", updated.text)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersStringLeafHostTest"`
Expected: FAIL — `AntlersStringLeaf` is not a `PsiLanguageInjectionHost` (compile error on the cast / `updateText`).

- [ ] **Step 3: Make `AntlersStringLeaf` an injection host**

In `psi/AntlersLeafElements.kt`, add imports:
```kotlin
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
```
Change the `AntlersStringLeaf` class declaration and body:
```kotlin
class AntlersStringLeaf(type: IElementType, text: CharSequence) : LeafPsiElement(type, text) {
    override fun getReferences(): Array<PsiReference> {
        return AntlersPartialReferenceHelper.refsForString(this)
    }

    override fun getReference(): PsiReference? = references.firstOrNull()
}
```
to:
```kotlin
class AntlersStringLeaf(type: IElementType, text: CharSequence) :
    LeafPsiElement(type, text), PsiLanguageInjectionHost {

    override fun getReferences(): Array<PsiReference> {
        return AntlersPartialReferenceHelper.refsForString(this)
    }

    override fun getReference(): PsiReference? = references.firstOrNull()

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost =
        ElementManipulators.handleContentChange(this, text) as PsiLanguageInjectionHost

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}
```

- [ ] **Step 4: Create the manipulator**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringLeafManipulator.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lexer.FlexAdapter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator

/**
 * Write-back for edits made inside an injected interpolation fragment. Splices the new content into the
 * string leaf and rebuilds it, bailing (round-trip guard) if the result no longer lexes to a single
 * T_STRING covering the whole text — e.g. an unescaped quote that ends the string early — so a bad edit
 * leaves the source intact instead of corrupting it.
 */
class AntlersStringLeafManipulator : AbstractElementManipulator<AntlersStringLeaf>() {
    override fun handleContentChange(
        element: AntlersStringLeaf,
        range: TextRange,
        newContent: String
    ): AntlersStringLeaf {
        val old = element.text
        val newText = old.substring(0, range.startOffset) + newContent + old.substring(range.endOffset)
        if (!isSingleString(newText)) return element
        return element.replaceWithText(newText) as AntlersStringLeaf
    }

    private fun isSingleString(text: String): Boolean {
        val lexer = FlexAdapter(_AntlersLexer(null))
        lexer.start(text, 0, text.length, _AntlersLexer.EXPR)
        return lexer.tokenType == AntlersTypes.T_STRING && lexer.tokenEnd == text.length
    }
}
```
NOTE: if `element.replaceWithText(newText)` does not return an `AntlersStringLeaf` directly in this platform build, use `element.replaceWithText(newText).psi as AntlersStringLeaf` (the partial-rename code in `AntlersPartialReference` uses the `.psi` form). Keep the round-trip guard intact either way.

- [ ] **Step 5: Register the manipulator**

In `src/main/resources/META-INF/plugin.xml`, after the existing `AntlersPhpBlockBody` `<lang.elementManipulator>` block, add:
```xml
        <lang.elementManipulator forClass="com.github.balotias.intellijantlers.psi.AntlersStringLeaf"
            implementationClass="com.github.balotias.intellijantlers.editor.AntlersStringLeafManipulator"/>
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersStringLeafHostTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersLeafElements.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringLeafManipulator.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersStringLeafHostTest.kt
git commit -m "feat(injection): make AntlersStringLeaf a PsiLanguageInjectionHost + manipulator"
```

---

### Task 2: The interpolation injector (the self-injection gate)

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersStringInterpolationInjector.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersInterpolationInjectionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `AntlersInterpolationInjectionTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersInterpolationInjectionTest : BasePlatformTestCase() {

    /** The injected PSI element at the offset of [marker] inside [text], or null if none. */
    private fun injectedAt(text: String, marker: String): com.intellij.psi.PsiElement? {
        myFixture.configureByText("p.antlers.html", text)
        val offset = text.indexOf(marker)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return InjectedLanguageManager.getInstance(project).findInjectedElementAt(antlers, offset)
    }

    fun testInterpolationIsInjected() {
        assertNotNull("expression inside {…} should be injected",
            injectedAt("{{ \"{title | upper}\" }}", "title"))
    }

    fun testPlainStringNotInjected() {
        assertNull("a string without interpolation injects nothing",
            injectedAt("{{ \"hello world\" }}", "hello"))
    }

    fun testInjectedFragmentIsAntlers() {
        val el = injectedAt("{{ \"{title | upper}\" }}", "upper")!!
        assertEquals(AntlersLanguage.INSTANCE, el.containingFile.language)
    }

    fun testTwoInterpolationsInjectedIndependently() {
        assertNotNull(injectedAt("{{ \"a {one} b {two} c\" }}", "one"))
        assertNotNull(injectedAt("{{ \"a {one} b {two} c\" }}", "two"))
        assertNull("plain text between interpolations is not injected",
            injectedAt("{{ \"a {one} b {two} c\" }}", " b "))
    }

    fun testEmptyInterpolationNotInjected() {
        assertNull(injectedAt("{{ \"x {} y\" }}", "} y"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInterpolationInjectionTest"`
Expected: FAIL — nothing is injected yet (no injector).

- [ ] **Step 3: Create the injector**

Create `src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersStringInterpolationInjector.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.editor.AntlersInterpolationScanner
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Injects the Antlers language into each `{ … }` interpolation span of a string literal, wrapped as
 * `{{ … }}`, so the interpolation parses as a real Antlers expression — enabling completion, go-to-def,
 * hover, and modifier docs/Ctrl+P inside interpolation. Each span is its own injected fragment. Field
 * scope inside the fragment is global/page (not the enclosing loop) — a documented limitation.
 */
class AntlersStringInterpolationInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(AntlersStringLeaf::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is AntlersStringLeaf) return
        val host = context as PsiLanguageInjectionHost
        if (!host.isValidHost) return
        for (span in AntlersInterpolationScanner.scan(context.text)) {
            if (span.contentStart >= span.contentEnd) continue
            registrar.startInjecting(AntlersLanguage.INSTANCE)
                .addPlace("{{ ", " }}", host, TextRange(span.contentStart, span.contentEnd))
                .doneInjecting()
        }
    }
}
```

- [ ] **Step 4: Register the injector**

In `src/main/resources/META-INF/plugin.xml`, after the existing `<multiHostInjector implementation="...AntlersYamlInjector"/>` (and the `AntlersPhpInjector` line next to it), add:
```xml
        <multiHostInjector implementation="com.github.balotias.intellijantlers.injection.AntlersStringInterpolationInjector"/>
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInterpolationInjectionTest"`
Expected: PASS (5 tests).

**If `testInterpolationIsInjected` FAILS because the platform refuses to inject a language into itself** (self-injection rejected) or because the Antlers template-language file-view-provider chokes on the tiny injected fragment: STOP and report `BLOCKED` with the exact symptom. Do NOT work around it silently — the fallback (a dedicated bare-expression language) is a design change that must be escalated. (Self-injection is expected to work; this guard is to catch it early if not.)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersStringInterpolationInjector.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersInterpolationInjectionTest.kt
git commit -m "feat(injection): inject Antlers into string interpolation spans"
```

---

### Task 3: Slim the interpolation annotator (injection colors the contents)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationAnnotator.kt`
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt`

- [ ] **Step 1: Slim the annotator to brace-only**

Replace the entire contents of `AntlersStringInterpolationAnnotator.kt` with:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Colors the `{` and `}` delimiters of Antlers interpolation inside a string literal. The interpolation
 * *contents* are now a real injected Antlers fragment (see AntlersStringInterpolationInjector) and are
 * highlighted natively by the injected language, so this only paints the delimiters — with an explicit
 * foreground (HighlighterColors.TEXT) so they don't bleed the green T_STRING base in schemes where the
 * brace key inherits its foreground (observed in PhpStorm). The surrounding string text keeps STRING.
 */
class AntlersStringInterpolationAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != AntlersTypes.T_STRING) return
        val text = element.text
        val base = element.textRange.startOffset
        for (span in AntlersInterpolationScanner.scan(text)) {
            paint(holder, base + span.openBrace, base + span.openBrace + 1)
            paint(holder, base + span.closeBrace, base + span.closeBrace + 1)
        }
    }

    private fun paint(holder: AnnotationHolder, start: Int, end: Int) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(start, end))
            .textAttributes(HighlighterColors.TEXT)
            .create()
    }
}
```

- [ ] **Step 2: Prune the highlight test to the still-valid assertions**

In `AntlersStringInterpolationHighlightTest.kt`, the inner-token-coloring assertions are now obsolete (the injected fragment colors the contents, not forced annotations the test can read). KEEP only the brace-coloring and the "not colored" assertions; DELETE the inner-token ones. Replace the whole class body's test methods with exactly these (keep the existing `keyOver` helper, imports, and class header):
```kotlin
    fun testInterpolationBraceUsesPlainText() =
        assertEquals(HighlighterColors.TEXT,
            keyOver("{{ \"object-position: {logo:focus_css}\" }}", "{"))

    fun testSurroundingStringTextNotColored() =
        assertNull(keyOver("{{ \"object-position: {logo:focus_css}\" }}", "object"))

    fun testPlainStringNotColored() =
        assertNull(keyOver("{{ \"hello world\" }}", "hello"))

    fun testEscapedBraceNotColored() =
        assertNull(keyOver("{{ \"a \\{not} b\" }}", "not"))
```
(Delete: `testInterpolatedVariableColored`, `testInterpolatedFieldColored`, `testInterpolationColonUsesPlainText`, `testInterpolationParenUsesPlainText`, `testInterpolatedModifierColored`, `testInterpolatedPipeUsesPipeColor`, `testArrayBracketUsesPlainText`, `testArrayModifierColored`, `testTwoInterpolationsColored` — these asserted inner-token forced keys the slim annotator no longer emits.) If the `HighlighterColors` import was removed with the deleted lines, keep it.

- [ ] **Step 3: Run the highlight test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersStringInterpolationHighlightTest"`
Expected: PASS (4 tests). The braces are still TEXT-colored; surrounding/escaped/plain text is still uncolored.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationAnnotator.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersStringInterpolationHighlightTest.kt
git commit -m "refactor(highlighting): slim interpolation annotator to braces (injection colors contents)"
```

---

### Task 4: End-to-end value (modifier docs inside interpolation) + regression

**Files:**
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersInterpolationFeatureTest.kt`

- [ ] **Step 1: Write the feature test**

Create `AntlersInterpolationFeatureTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersInterpolationFeatureTest : BasePlatformTestCase() {

    /** The injected element at the offset of [marker] inside [text]. */
    private fun injectedAt(text: String, marker: String): com.intellij.psi.PsiElement {
        myFixture.configureByText("p.antlers.html", text)
        val offset = text.indexOf(marker)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return InjectedLanguageManager.getInstance(project).findInjectedElementAt(antlers, offset)!!
    }

    fun testModifierDocInsideInterpolation() {
        // `replace` inside the interpolation resolves as a real modifier → quick-doc shows its signature.
        val el = injectedAt("{{ \"{view:href | replace('a','b')}\" }}", "replace")
        val provider = AntlersDocumentationProvider()
        // walk to the identifier leaf the doc provider keys on
        val ident = PsiTreeUtil.getDeepestFirst(el).let { if (it.text == "replace") it else el }
        val doc = provider.generateDoc(ident, ident)
        assertNotNull("modifier quick-doc fires inside interpolation", doc)
        assertTrue("doc shows the replace signature, got: $doc",
            doc!!.contains("replace(search, replacement)"))
    }

    fun testInjectedModifierIsModifierPsi() {
        val el = injectedAt("{{ \"{title | upper}\" }}", "upper")
        // The injected fragment really parsed `upper` as a modifier name (AntlersModifierMixin ancestor).
        val mod = PsiTreeUtil.getParentOfType(
            el, com.github.balotias.intellijantlers.psi.AntlersModifierMixin::class.java, false)
        assertNotNull("`upper` is a real modifier in the injected fragment", mod)
    }
}
```

- [ ] **Step 2: Run the feature test**

Run: `./gradlew --rerun-tasks test --tests "*AntlersInterpolationFeatureTest"`
Expected: PASS (2 tests). If `generateDoc` needs the exact identifier element, adjust the `ident` selection to the `replace` leaf within `el` (e.g. `PsiTreeUtil.collectElements(el.containingFile) { it.text == "replace" }.firstOrNull() ?: el`); keep the two assertions (doc non-null + contains the signature). If `findInjectedElementAt` returns the host-tree element rather than the injected one for the doc case, get the injected file via `InjectedLanguageManager.getInjectedPsiFiles(antlers)` and locate `replace` inside it.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersInterpolationFeatureTest.kt
git commit -m "test(injection): modifier docs + real modifier PSI inside interpolation"
```

- [ ] **Step 4: Regression — partial references on strings still resolve**

Run the existing reference/partial suites (the `AntlersStringLeaf.getReferences()` path must keep working now that the class also implements the host interface):
```bash
./gradlew --rerun-tasks test --tests "*AntlersPartial*" --tests "*ReferenceTest" --tests "*RefactoringTest"
```
Expected: all PASS. If any fail, the host interface broke `getReferences()` — investigate (it should be untouched) and report.

---

### Task 5: Full-suite gate

**Files:** none (verification only).

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml; echo "exit:$?"`
Expected: prints nothing, `exit:1`.

- [ ] **Step 3: Count check**

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END{print s}'`
Expected: previous total + ~10 new (3 host + 5 injection + 2 feature) − 9 removed highlight tests + 4 kept = net change; zero failures/errors. The important checks are BUILD SUCCESSFUL and the clean gate.

If green, proceed to whole-feature review and `superpowers:finishing-a-development-branch`.

---

## Notes for the implementer

- **Task 2 is the make-or-break gate:** if the platform won't self-inject Antlers into Antlers, stop and report `BLOCKED` rather than hacking around it.
- The injector wraps each span as `{{ <content> }}` (prefix/suffix are synthetic — not in the host), so the existing parser/completion/docs handle it as a real tag expression.
- **Accepted limitation:** field completion inside the injected fragment uses global/page scope, not the enclosing loop scope. Do not try to add loop-scope plumbing — it's the deferred follow-up.
- The slim annotator only colors the `{`/`}`; the injected fragment colors the contents.
- Run the **full suite with `--rerun-tasks`** and gate on the result XML.
