# Antlers Template IDE Hints (scope 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recognize the 7 Antlers Toolbox hint directives in `{{# … #}}` comments — highlight + complete them — and let a leading `@collection`/`@entry`/`@blueprint <handle>` drive blueprint field scoping.

**Architecture:** A pure parser feeds a highlight annotator and a per-file cached scoping resolver; a small completion branch offers the directive names; `AntlersFieldContext` consults the hint namespaces between loop-scope (E1) and page-mapping (E2). No lexer/parser/grammar change.

**Tech Stack:** Kotlin, IntelliJ Platform (`Annotator`, `CompletionProvider`, `CachedValuesManager`, `TextAttributesKey`), JUnit / `BasePlatformTestCase`.

**Spec:** `docs/superpowers/specs/2026-06-04-antlers-template-hints-design.md`
**Branch:** `antlers-template-hints` (already created from `main`; spec already committed).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty). `--rerun-tasks` REQUIRED.

## File Structure

- **Create** `scope/AntlersHintParser.kt` — pure directive parser.
- **Create** `editor/AntlersHintAnnotator.kt` — highlight; + `HINT` key in `highlighting/AntlersSyntaxHighlighter.kt`; + `highlighting/AntlersColorSettingsPage.kt`; + `plugin.xml`.
- **Modify** `completion/AntlersCompletionProvider.kt` — directive completion in comments.
- **Create** `blueprint/AntlersViewHints.kt` — cached leading-comment namespaces; **Modify** `scope/AntlersFieldContext.kt` — consult it.
- Tests: `AntlersHintParserTest`, `AntlersHintHighlightTest`, `AntlersHintCompletionTest`, `AntlersViewHintsTest` + `AntlersColorSettingsPageTest` update.

---

### Task 1: The hint parser

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersHintParser.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersHintParserTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersHintParserTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.scope

import org.junit.Assert.assertEquals
import org.junit.Test

class AntlersHintParserTest {
    private fun names(body: String) = AntlersHintParser.parse(body).map { it.name }

    @Test fun recognizesAllSevenDirectives() {
        val body = "\n@name N\n@desc D\n@param p A param\n@entry e\n@collection blog\n@blueprint bp\n@set a.b.c\n"
        assertEquals(
            listOf("@name", "@desc", "@param", "@entry", "@collection", "@blueprint", "@set"),
            names(body)
        )
    }

    @Test fun ignoresNonDirectiveAndUnknown() {
        assertEquals(emptyList<String>(), names("just prose\n@unknown x\n  @ also not\n"))
    }

    @Test fun capturesValueAndNameSpan() {
        val body = "  @collection blog\n"
        val d = AntlersHintParser.parse(body).single()
        assertEquals("blog", d.value)
        assertEquals("@collection", body.substring(d.nameStart, d.nameEnd))
    }

    @Test fun directiveSetIsTheSevenNames() {
        assertEquals(
            setOf("name", "desc", "param", "entry", "collection", "blueprint", "set"),
            AntlersHintParser.DIRECTIVE_NAMES
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersHintParserTest"`
Expected: FAIL — compile error (`AntlersHintParser` unresolved).

- [ ] **Step 3: Write the parser**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersHintParser.kt`:
```kotlin
package com.github.balotias.intellijantlers.scope

/**
 * Parses Antlers Toolbox "Template IDE Hint" directives out of a `{{# … #}}` comment body. Pure /
 * IntelliJ-free; tolerant. A directive is a line whose first non-whitespace token is one of the seven
 * `@<directive>` keywords, followed by its (trimmed) value.
 */
object AntlersHintParser {

    /** The seven directive names, without the leading `@`. */
    val DIRECTIVE_NAMES = setOf("name", "desc", "param", "entry", "collection", "blueprint", "set")

    /** `nameStart`/`nameEnd` = half-open `@directive` offsets in the parsed body; `value` is trimmed. */
    data class Directive(val name: String, val nameStart: Int, val nameEnd: Int, val value: String)

    private val LINE = Regex("""^(\s*)(@[a-zA-Z]+)\b(.*)$""")

    fun parse(body: String): List<Directive> {
        val out = ArrayList<Directive>()
        var pos = 0
        for (line in body.split("\n")) {
            val m = LINE.find(line)
            if (m != null && m.groupValues[2].removePrefix("@") in DIRECTIVE_NAMES) {
                val name = m.groupValues[2]
                val nameStart = pos + m.groupValues[1].length
                out.add(Directive(name, nameStart, nameStart + name.length, m.groupValues[3].trim()))
            }
            pos += line.length + 1
        }
        return out
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersHintParserTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersHintParser.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersHintParserTest.kt
git commit -m "$(cat <<'EOF'
feat: parser for Antlers template-IDE-hint directives in comments

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Highlight the directives

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersHintAnnotator.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersHintHighlightTest.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPageTest.kt` (update)

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersHintHighlightTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersHintHighlightTest : BasePlatformTestCase() {
    private fun keyOver(text: String, token: String): TextAttributesKey? {
        myFixture.configureByText("p.antlers.html", text)
        return myFixture.doHighlighting()
            .firstOrNull { it.text == token && it.forcedTextAttributesKey != null }
            ?.forcedTextAttributesKey
    }

    fun testDirectiveTagIsHintColored() =
        assertEquals(AntlersSyntaxHighlighter.HINT, keyOver("{{#\n@collection blog\n#}}", "@collection"))

    fun testPlainCommentProseNotColored() =
        assertNull(keyOver("{{# just a note #}}", "just"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersHintHighlightTest"`
Expected: FAIL — `HINT` unresolved (compile error).

- [ ] **Step 3: Add the HINT color key**

In `AntlersSyntaxHighlighter.kt`, after the `val PIPE = …` line in the companion, add:
```kotlin
        val HINT = TextAttributesKey.createTextAttributesKey("ANTLERS_HINT", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG)
```

- [ ] **Step 4: Write the annotator**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersHintAnnotator.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersHintParser
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Colors Antlers Toolbox "Template IDE Hint" directives (`@name`, `@collection`, …) inside `{{# … #}}`
 * comments, like a Javadoc/KDoc tag. Overlay on the comment-text token (same mechanism as 4b).
 */
class AntlersHintAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != AntlersTypes.T_COMMENT_TEXT) return
        val base = element.textRange.startOffset
        for (d in AntlersHintParser.parse(element.text)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(base + d.nameStart, base + d.nameEnd))
                .textAttributes(AntlersSyntaxHighlighter.HINT)
                .create()
        }
    }
}
```
Register in `plugin.xml` (in the `com.intellij` extensions block, next to the other annotators):
```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersHintAnnotator"/>
```

- [ ] **Step 5: Run the highlight test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersHintHighlightTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Expose the HINT color in the settings page + update its test**

In `AntlersColorSettingsPage.kt`:
- add `"hint" to AntlersSyntaxHighlighter.HINT,` to the map (after the `"pipe"` entry);
- add `AttributesDescriptor("Template hint", AntlersSyntaxHighlighter.HINT),` to `DESCRIPTORS` (after the
  `"Modifier pipe"` entry);
- in `DEMO`, after the `{{# Featured posts #}}` line, add a hint demo line:
```kotlin
            {{# <hint>@name</hint> Button · <hint>@collection</hint> blog #}}
```

In `AntlersColorSettingsPageTest.kt`, add `AntlersSyntaxHighlighter.HINT` to the expected descriptor set
(after `PIPE`) and `"hint"` to the expected tag-map key set.

- [ ] **Step 7: Run the settings-page test**

Run: `./gradlew --rerun-tasks test --tests "*AntlersColorSettingsPageTest" --tests "*AntlersHintHighlightTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersHintAnnotator.kt \
        src/main/resources/META-INF/plugin.xml \
        src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersHintHighlightTest.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPageTest.kt
git commit -m "$(cat <<'EOF'
feat(highlighting): color template-IDE-hint directives in {{# #}} comments

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Complete the directive names after `@`

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersHintCompletionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersHintCompletionTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersHintCompletionTest : BasePlatformTestCase() {
    private fun complete(text: String): List<String> {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testDirectivesOfferedAfterAtInComment() {
        val items = complete("{{# @<caret> #}}")
        assertTrue("offers hint directives, got $items", items.containsAll(listOf("name", "collection", "blueprint")))
    }

    fun testFiltersByTypedPrefix() {
        // 'col' → only 'collection' matches; completeBasic auto-inserts the single match.
        myFixture.configureByText("p.antlers.html", "{{# @col<caret> #}}")
        myFixture.completeBasic()
        assertTrue("collection inserted, got: ${myFixture.editor.document.text}",
            myFixture.editor.document.text.contains("@collection"))
    }

    fun testNormalTagUnaffected() {
        val items = complete("{{ <caret> }}")
        assertFalse("a plain tag position does not offer hint directives", items.contains("blueprint"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersHintCompletionTest"`
Expected: FAIL — `testDirectivesOfferedAfterAtInComment` / `testFiltersByTypedPrefix` fail (no comment-directive completion yet).

- [ ] **Step 3: Add the comment-directive branch**

In `AntlersCompletionProvider.kt`, add these imports if not present:
```kotlin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersHintParser
```
At the **start** of `addCompletions` (before the existing `val info = AntlersCompletionContext.classify(...)`
line), add:
```kotlin
        val hintPrefix = commentDirectivePrefix(parameters.position, parameters.offset)
        if (hintPrefix != null) {
            val r = result.withPrefixMatcher(hintPrefix)
            for (name in AntlersHintParser.DIRECTIVE_NAMES) {
                r.addElement(LookupElementBuilder.create(name).withIcon(AntlersIcons.FILE).withTypeText("Hint"))
            }
            return
        }
```
Add this private helper to the class (e.g. just below `addCompletions`):
```kotlin
    /** When the caret sits in a `{{# … #}}` comment right after `@<word>`, the post-`@` prefix; else null. */
    private fun commentDirectivePrefix(position: com.intellij.psi.PsiElement, caretOffset: Int): String? {
        if (position.node.elementType != AntlersTypes.T_COMMENT_TEXT) return null
        val start = position.textRange.startOffset
        val end = (caretOffset - start).coerceIn(0, position.text.length)
        val before = position.text.substring(0, end)
        val at = before.lastIndexOf('@')
        if (at < 0) return null
        val after = before.substring(at + 1)
        return if (after.all { it.isLetter() }) after else null
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersHintCompletionTest"`
Expected: PASS (3 tests). If `testNormalTagUnaffected` fails, the helper is matching outside comments —
confirm the `T_COMMENT_TEXT` gate.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersHintCompletionTest.kt
git commit -m "$(cat <<'EOF'
feat(completion): offer hint directives after @ in {{# #}} comments

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Blueprint scoping from the leading comment

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersViewHints.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersFieldContext.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersViewHintsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersViewHintsTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersViewHintsTest : BasePlatformTestCase() {

    fun testLeadingCollectionHintBecomesNamespace() {
        val file = myFixture.configureByText("p.antlers.html", "{{# @collection blog #}}\n{{ x }}")
        val ns = AntlersViewHints.declaredNamespaces(file)
        assertEquals(listOf(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog")), ns)
    }

    fun testNoHintNoNamespaces() {
        val file = myFixture.configureByText("p.antlers.html", "{{# just a note #}}\n{{ x }}")
        assertTrue(AntlersViewHints.declaredNamespaces(file).isEmpty())
    }

    fun testCollectionHintRestrictsFieldsToThatBlueprint() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: X\n"
        )
        val file = myFixture.configureByText("p.antlers.html", "{{# @collection blog #}}\n{{ x }}")
        val antlers = file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val el = antlers.findElementAt(antlers.text.lastIndexOf("x"))!!
        val handles = AntlersFieldContext.fieldsInScope(el, project)?.map { it.handle }
        assertEquals(listOf("hero_title"), handles)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersViewHintsTest"`
Expected: FAIL — `AntlersViewHints` unresolved (compile error).

- [ ] **Step 3: Write `AntlersViewHints`**

Create `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersViewHints.kt`:
```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.psi.AntlersComment
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersHintParser
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * The blueprint namespaces a view declares via a leading `{{# @collection|@entry|@blueprint <handle> #}}`
 * hint comment (Antlers Toolbox). Cached per-file. `@blueprint` is read as a collection handle in v1.
 */
object AntlersViewHints {

    private val SCOPING = setOf("@collection", "@entry", "@blueprint")

    fun declaredNamespaces(file: PsiFile): List<BlueprintNamespace> =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(compute(file), PsiModificationTracker.MODIFICATION_COUNT)
        }

    private fun compute(file: PsiFile): List<BlueprintNamespace> {
        val comment = PsiTreeUtil.findChildOfType(file, AntlersComment::class.java) ?: return emptyList()
        val body = comment.node.findChildByType(AntlersTypes.T_COMMENT_TEXT)?.text ?: return emptyList()
        return AntlersHintParser.parse(body)
            .filter { it.name in SCOPING && it.value.isNotBlank() }
            .map { BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, it.value.substringBefore(' ').trim()) }
            .distinct()
    }
}
```

- [ ] **Step 4: Wire it into `AntlersFieldContext.namespacesFor`**

In `AntlersFieldContext.kt`, change `namespacesFor` from:
```kotlin
    fun namespacesFor(element: PsiElement): List<BlueprintNamespace>? {
        val scopes = AntlersScopeResolver.scopesAt(element)
        if (scopes.isNotEmpty()) return scopes.map { it.namespace }
        val page = PageBlueprintResolver.namespacesFor(element)
        if (page.isNotEmpty()) return page
        return null
    }
```
to:
```kotlin
    fun namespacesFor(element: PsiElement): List<BlueprintNamespace>? {
        val scopes = AntlersScopeResolver.scopesAt(element)
        if (scopes.isNotEmpty()) return scopes.map { it.namespace }
        val hints = element.containingFile?.let { com.github.balotias.intellijantlers.blueprint.AntlersViewHints.declaredNamespaces(it) }
        if (!hints.isNullOrEmpty()) return hints
        val page = PageBlueprintResolver.namespacesFor(element)
        if (page.isNotEmpty()) return page
        return null
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersViewHintsTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersViewHints.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersFieldContext.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/blueprint/AntlersViewHintsTest.kt
git commit -m "$(cat <<'EOF'
feat(scope): @collection/@entry/@blueprint hint drives field scoping

A leading {{# @collection <handle> #}} declares the blueprint, consulted between
loop scope (E1) and the template->blueprint page mapping (E2). #140.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Full-suite regression gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml && echo FAIL || echo "GATE CLEAN"`
Expected: `GATE CLEAN`.

If a scope/completion/field test regressed: the only behavioral change is the new hint-namespace step in
`namespacesFor` (it returns early ONLY when a leading hint comment declares a scoping directive — files
without one are unchanged). Investigate; do not edit other tests to go green.

## Self-Review

- **Spec coverage:** A1 parser (the 7 directives, spans) → Task 1; A2 highlight + `HINT` key + settings
  page (§A2) → Task 2; A3 directive completion after `@` in a comment (§A3) → Task 3; B `AntlersViewHints`
  + `AntlersFieldContext` precedence (E1 → hint → E2 → global) (§B) → Task 4; testing rows (parser,
  highlight, completion, declaredNamespaces + fieldsInScope, settings page, gate) → Tasks 1-5. `@param`/
  `@set` deferred and `@blueprint`-as-collection are honored (parser recognizes all 7 for highlight/
  complete; only collection/entry/blueprint feed scoping, as collection handles). No gaps.
- **Placeholder scan:** none — full code in every step.
- **Type consistency:** `AntlersHintParser.parse(String): List<Directive>` / `Directive(name, nameStart,
  nameEnd, value)` / `DIRECTIVE_NAMES` defined in Task 1 and consumed identically in Tasks 2/3/4;
  `AntlersViewHints.declaredNamespaces(PsiFile): List<BlueprintNamespace>` (Task 4) returns
  `BlueprintNamespace(Kind.COLLECTION, handle)` consumed by `namespacesFor`; `AntlersSyntaxHighlighter.HINT`
  (Task 2) used in the annotator, settings page, and tests; the `commentDirectivePrefix` gate matches the
  `T_COMMENT_TEXT` leaf the annotator also uses.
