# Antlers Reformat Indentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reformat Code indents Antlers pair tags + conditions one level on top of HTML indentation, via the XML-aware template-formatting framework (spike-proven). Attempt the one residual (Antlers-on-Antlers nesting); document it if intractable.

**Architecture:** `AntlersXmlTemplateFormattingModelBuilder extends AbstractXmlTemplateFormattingModelBuilder` makes the HTML/XML formatter the primary block tree and merges Antlers blocks in; an absolute-mode space indent (`Indent.getSpaceIndent(depth*size, relativeToDirectParent=false)`) keyed to `AntlersNestingTreeBuilder` depth composes additively on top of HTML indent. The existing spacing post-processor stays (complementary).

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`com.intellij.xml.template.formatter.*`), `BasePlatformTestCase`. Tests run via `./gradlew test`; build cache returns cached results → use `--rerun-tasks`, gate on `build/test-results/test/*.xml`.

**Spike-proven foundation:** branch `spike/reformat-indentation` (commit `82553c8`) has a WORKING `AntlersXmlTemplateFormattingModelBuilder.kt` + `AntlersFormatTest.kt` with the additive GOAL cases asserted (`testGoalUlCollection`, `testGoalDivUlCollection` pass). This plan PRODUCTIONIZES it. Pull proven code with `git show spike/reformat-indentation:<path>`.

**Verified facts:**
- Base class `com.intellij.xml.template.formatter.AbstractXmlTemplateFormattingModelBuilder` (NOT `Abstract…TemplateLanguage…`). Block base `com.intellij.xml.template.formatter.TemplateLanguageBlock`.
- `Indent.getSpaceIndent(N, /*relativeToDirectParent=*/ false)` is the additive lever (proven). `true`/`(1+depth)` double-count.
- `getIndent()`/Antlers-block indent default must NOT be null; the prototype's `setIndent` override + `getChildIndent()=NoneIndent` handle this.
- The spike hardcoded `* 4`; production reads `settings.getIndentSize(AntlersFileType.INSTANCE)`.
- `AntlersNestingTreeBuilder.build(file, project): NestingTree(roots, unmatchedClosers)`; `NestingNode(opener, name, closer, children)`; body span = `opener.endOffset until closer.startOffset` (closer non-null).
- `AntlersSpacingPostFormatProcessor` coexists (spike-verified: `AntlersSpacingFormatterTest` green with the model registered).
- Branch for this work: `antlers-editor-ux-fixes` (current).

---

### Task 1: Productionize the XML-template formatting model

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersXmlTemplateFormattingModelBuilder.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersIndentFormatterTest.kt` (create)

- [ ] **Step 1: Copy the proven builder, then productionize indent size**

Pull the proven file: `git show spike/reformat-indentation:src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersXmlTemplateFormattingModelBuilder.kt > src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersXmlTemplateFormattingModelBuilder.kt`

Then make the indent size settings-driven (replace the hardcoded `4`):
- In the builder, replace `private val indentUnit: Int get() = 4` with a settings read. The builder has access to `CodeStyleSettings` in `createTemplateLanguageBlock`/`createXmlTagBlock`/`createXmlBlock`; pass the size through, OR compute it where the `settings` is in scope:
  - In `bump(indent, extraLevels)`, the builder doesn't have `settings` on hand — change `bump` to take a `size: Int` param, and have each caller pass `settings.getIndentSize(com.github.balotias.intellijantlers.AntlersFileType.INSTANCE)` (the `settings` is a parameter of `createXmlTagBlock`/`createXmlBlock`/`createTemplateLanguageBlock`). Update the KDoc accordingly.
- In `AntlersXmlTemplateBlock.setIndent`, replace `Indent.getSpaceIndent(depth * 4, false)` with `Indent.getSpaceIndent(depth * settings.getIndentSize(AntlersFileType.INSTANCE), false)` — the block holds `settings` (constructor param `settings: CodeStyleSettings`); add the `AntlersFileType` import.

Keep everything else from the proven file (the `isTemplateFile`/`isOuterLanguageElement`/`isMarkupLanguageElement` overrides, `spansFor`/`antlersDepthAt` with per-text caching, `createXmlTagBlock`/`createXmlBlock` bumping, `AntlersXmlTemplateBlock` with `setIndent`/`getChildIndent`/`getSpacing`).

- [ ] **Step 2: Register the formatter in plugin.xml** (below the `<postFormatProcessor>` line):

```xml
        <lang.formatter language="Antlers" implementationClass="com.github.balotias.intellijantlers.formatter.AntlersXmlTemplateFormattingModelBuilder"/>
```

- [ ] **Step 3: Write the reformat tests** (the additive GOAL cases that the spike proved, plus edges)

```kotlin
package com.github.balotias.intellijantlers.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersIndentFormatterTest : BasePlatformTestCase() {

    private fun reformatted(input: String): String {
        val file = myFixture.configureByText("t.antlers.html", input)
        CodeStyle.getSettings(file).indentOptions?.INDENT_SIZE = 4   // deterministic
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        return file.text
    }

    fun testHtmlOnlyStillIndents() {
        assertEquals("<div>\n    <p>x</p>\n</div>", reformatted("<div>\n<p>x</p>\n</div>"))
    }

    fun testPairTagWrapsHtml() {
        val out = reformatted("{{ collection:blog }}\n<article>{{ title }}</article>\n{{ /collection }}")
        assertEquals("{{ collection:blog }}\n    <article>{{ title }}</article>\n{{ /collection }}", out)
    }

    fun testHtmlWrapsPairTagAdditive() {
        val out = reformatted("<ul>\n{{ collection:blog }}\n<li>{{ title }}</li>\n{{ /collection }}\n</ul>")
        assertEquals(
            "<ul>\n    {{ collection:blog }}\n        <li>{{ title }}</li>\n    {{ /collection }}\n</ul>",
            out
        )
    }

    fun testTwoHtmlLevelsAdditive() {
        val out = reformatted("<div>\n<ul>\n{{ collection:blog }}\n<li>x</li>\n{{ /collection }}\n</ul>\n</div>")
        assertEquals(
            "<div>\n    <ul>\n        {{ collection:blog }}\n            <li>x</li>\n        {{ /collection }}\n    </ul>\n</div>",
            out
        )
    }

    fun testUnclosedPairNoIndent() {
        // No closer → no body span → `<p>` not indented under the opener.
        val out = reformatted("{{ collection:blog }}\n<p>x</p>")
        assertEquals("{{ collection:blog }}\n<p>x</p>", out)
    }

    fun testSpacingProcessorStillRuns() {
        assertEquals("{{ title }}", reformatted("{{title}}"))
    }
}
```

Note: the exact expected strings must match what the proven model produces — the GOAL cases
(`testHtmlWrapsPairTagAdditive`, `testTwoHtmlLevelsAdditive`) are the spike's asserted outputs (verify
against `git show spike/reformat-indentation:src/test/.../AntlersFormatTest.kt` `testGoalUlCollection`
/`testGoalDivUlCollection`). If the HTML formatter's exact whitespace differs (e.g. trailing newline),
adjust the expected strings to the ACTUAL reformat output — the contract is the additive indent
STRUCTURE (1/2/3 levels), not incidental whitespace. If `CodeStyle.getSettings(file).indentOptions`
is null in the test, set the size via `CodeStyle.getSettings(file).getIndentOptions(file.fileType).INDENT_SIZE = 4` or the language-specific options; match whatever the SDK exposes.

- [ ] **Step 4: Run, confirm green**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.AntlersIndentFormatterTest"`
Expected: PASS. If a GOAL additive case fails, dump the block tree (the spike's technique:
`builder.createModel(FormattingContext.create(file, range, settings, FormattingMode.REFORMAT)).rootBlock`,
walk `subBlocks`, print `textRange`+`indent`) to see where the indent diverges — do NOT guess.

- [ ] **Step 5: Confirm the spacing processor still passes + no broader regression**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.AntlersSpacingFormatterTest"`
Expected: PASS (the model defines no Antlers `SpacingBuilder`, so the post-processor is unaffected).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersXmlTemplateFormattingModelBuilder.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersIndentFormatterTest.kt
git commit -m "Add Antlers reformat indentation (XML-template formatting model, additive HTML+Antlers)"
```

---

### Task 2: Attempt the Antlers-on-Antlers residual fix

**Goal:** make a condition/pair *directly* inside another (no HTML between) indent its own line + its
content correctly — e.g.
```
{{ collection:blog }}
    {{ if featured }}
        <article>x</article>
    {{ /if }}
{{ /collection }}
```
The spike's residual: the inner `{{ if }}`/`{{ /if }}` lines stay at the parent level and the
`<article>` double-counts. This task is **exploratory** (formatting internals); the fallback is a
documented limitation + a marker test. Do NOT thrash — try the hypotheses below, measure each via the
block-tree dump, and if none works after genuine investigation, take the fallback.

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersXmlTemplateFormattingModelBuilder.kt`
- Test: append to `AntlersIndentFormatterTest.kt`

- [ ] **Step 1: Write the (initially failing) target test**

```kotlin
    fun testAntlersOnAntlersNesting() {
        val out = reformatted(
            "{{ collection:blog }}\n{{ if featured }}\n<article>x</article>\n{{ /if }}\n{{ /collection }}"
        )
        assertEquals(
            "{{ collection:blog }}\n    {{ if featured }}\n        <article>x</article>\n    {{ /if }}\n{{ /collection }}",
            out
        )
    }
```

- [ ] **Step 2: Run, confirm it fails; dump the block tree to see the actual indents**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.formatter.AntlersIndentFormatterTest"`
Capture the actual output of `testAntlersOnAntlersNesting`. Add a temporary block-tree dump (as in
Task 1 Step 4) for THIS input to see the indent each block reports for the `{{ if }}` and `<article>`
lines.

- [ ] **Step 3: Try these hypotheses one at a time (measure each, keep only what works)**

1. The `{{ if }}` Antlers tag block's own line is at parent level because the XML merge gives the
   Antlers *statement* block a `NONE`/parent indent and `setIndent` only fires once. Try: in
   `AntlersXmlTemplateBlock.getIndent()` (override it, not just `setIndent`), return
   `Indent.getSpaceIndent(antlersDepthAt(node.start) * size, false)` so the tag's OWN line indents.
2. If the `<article>` double-counts (e.g. lands at depth 3 not 2), the markup bump in
   `createXmlBlock`/`createXmlTagBlock` is adding depth that the XML parent already contributes for
   the no-HTML-between case. Try: compute the markup bump as the Antlers depth MINUS the depth already
   contributed by enclosing markup, or switch the markup bump to depth at the markup node while the
   Antlers-tag-block indent is handled separately — measure which yields exactly 2 levels.
3. If both the tag line and content can't be satisfied simultaneously with absolute indents, try a
   `buildChildren()`-level synthetic grouping ONLY for the no-intervening-HTML case (the XML base may
   expose a child-building hook) — but timebox this; it's the hardest path.

Make ONE change, re-run the FULL `AntlersIndentFormatterTest`, confirm Task-1 cases STILL pass (no
regression) AND check `testAntlersOnAntlersNesting`. Revert changes that break Task-1 cases.

- [ ] **Step 4: Resolve — fixed OR documented limitation**

- If `testAntlersOnAntlersNesting` passes AND all Task-1 cases still pass → keep the fix, commit.
- If after genuine investigation it's intractable without regressing the proven cases → take the
  fallback: change `testAntlersOnAntlersNesting` into a `// KNOWN LIMITATION` test that asserts the
  ACTUAL (imperfect) current output, with a comment explaining the IndentInheritingBlock/XML-merge
  cause and that conditions normally wrap markup (which works). Add a one-line note to the builder's
  KDoc. Report this outcome clearly.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/formatter/AntlersXmlTemplateFormattingModelBuilder.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/formatter/AntlersIndentFormatterTest.kt
git commit -m "Antlers-on-Antlers reformat nesting: <fixed | documented limitation>"
```

(Use the accurate commit subject for the outcome.)

---

### Task 3: Full-suite verification

- [ ] **Step 1:** `./gradlew --rerun-tasks test` → BUILD SUCCESSFUL.
- [ ] **Step 2:** `echo "failed_files=$(grep -lo 'failures=\"[1-9]\|errors=\"[1-9]' build/test-results/test/*.xml | wc -l | tr -d ' ')"` → `failed_files=0`. Note the total (prior 236 + the new formatter tests).
- [ ] **Step 3:** `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Confirm `<lang.formatter language="Antlers">` is registered exactly once and the `<postFormatProcessor>` (spacing) is still present.

---

## Self-Review

**Spec coverage:** XML-template formatting model + additive indent → Task 1; the residual fix attempt + documented-limitation fallback → Task 2; verification → Task 3. Spacing-processor coexistence preserved (Task 1 Step 5). Out-of-scope items (else/elseif wrapping, settings panel) not implemented.

**Placeholder scan:** none — Task 1 promotes proven code with a precise productionization edit; tests have concrete expected strings (with a documented "match actual whitespace" escape for the HTML formatter's incidental output). Task 2 is explicitly exploratory with a defined fallback (not a placeholder — it's a bounded investigation with a concrete resolution gate).

**Type/name consistency:** `AntlersXmlTemplateFormattingModelBuilder` + `AntlersXmlTemplateBlock` names match the spike; `antlersDepthAt`/`spansFor`/`bump` carried over; `settings.getIndentSize(AntlersFileType.INSTANCE)` replaces the hardcoded 4 consistently in `bump` and `setIndent`.

**Known SDK-shape risks flagged inline:** `CodeStyle.getSettings(file).indentOptions` null-handling (Task 1 Step 3); the exact reformat whitespace vs the asserted strings (match actual). The block-tree dump is the debugging oracle for any divergence.
