# Antlers `.antlers.php` Support + PHP Injection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `.antlers.php` a first-class Antlers view (Component A) and inject the real PHP language into `{{$ … $}}` / `{{? … ?}}` blocks (Component B, #166).

**Architecture:** A registers the second extension + extends partial/view resolution. B restructures the `phpBlock` grammar so the body is a `PsiLanguageInjectionHost` and adds a `MultiHostInjector` that reaches PHP dependency-free via `Language.findLanguageByID("PHP")` (no-ops where the PHP plugin is absent).

**Tech Stack:** Kotlin, IntelliJ Platform 2025.2 (file types, language injection, Grammar-Kit parser regen), JUnit / `BasePlatformTestCase` / `ParsingTestCase`.

**Spec:** `docs/superpowers/specs/2026-06-04-antlers-php-support-design.md`
**Branch:** `antlers-php-support` (already created from `main`; spec already committed).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty). `--rerun-tasks` REQUIRED.

**Parser-regen recipe (verified byte-identical round-trip):**
```bash
GK=/Users/balotias/.gradle/caches/modules-2/files-2.1/org.jetbrains/grammar-kit/2022.3.2/6eb13410f38e69b40213d76dd36f1ea80fbbd832/grammar-kit-2022.3.2.jar
IDELIB=/Users/balotias/.gradle/caches/9.5.0/transforms/eec72b1cad4f6953a493ec271def7dbc/transformed/ideaIC-2025.2.6.2/lib
# java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main <out-dir> src/main/grammar/Antlers.bnf
```
Round-trip the UNCHANGED `.bnf` to byte-identical first, then edit/regen/copy.

## File Structure

- **A:** `plugin.xml` (pattern) + `references/StatamicProject.kt`, `references/AntlersPartialReference.kt`, `references/AntlersPartialReferenceSearcher.kt`, `blueprint/PageBlueprintResolver.kt` (extension lists).
- **B:** `grammar/Antlers.bnf` + regen `gen/**`; `psi/AntlersMixins.kt` (host mixin); `editor/AntlersPhpBlockBodyManipulator.kt`; `injection/AntlersPhpInjector.kt` + a pure helper; `plugin.xml` (manipulator + injector registration); `testData/parsing/*`.

---

### Task 1: Component A — `.antlers.php` is first-class

**Files:**
- Modify: `src/main/resources/META-INF/plugin.xml`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/StatamicProject.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReference.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceSearcher.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/PageBlueprintResolver.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/AntlersPhpFileTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/AntlersPhpFileTest.kt`:
```kotlin
package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.references.StatamicProject
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPhpFileTest : BasePlatformTestCase() {

    fun testAntlersPhpFileIsAntlers() {
        val file = myFixture.configureByText("p.antlers.php", "{{ title }}")
        assertEquals(AntlersLanguage.INSTANCE, file.viewProvider.baseLanguage)
    }

    fun testPartialResolvesToAntlersPhp() {
        myFixture.addFileToProject("resources/views/partials/card.antlers.php", "<div>card</div>")
        myFixture.configureByText("resources/views/page.antlers.html", "{{ partial:car<caret>d }}")
        val target = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertNotNull("partial:card resolves", target)
        assertTrue(
            "resolves to the .antlers.php partial, got ${(target as? com.intellij.psi.PsiFile)?.name}",
            (target as com.intellij.psi.PsiFile).name == "card.antlers.php"
        )
    }

    fun testListPartialsIncludesAntlersPhp() {
        myFixture.addFileToProject("resources/views/partials/widget.antlers.php", "x")
        val anchor = myFixture.configureByText("resources/views/page.antlers.html", "{{ title }}")
        val partials = StatamicProject.listPartials(anchor)
        assertTrue("listPartials includes the .antlers.php partial, got $partials", partials.contains("widget"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersPhpFileTest"`
Expected: FAIL — `.antlers.php` isn't registered, so `baseLanguage` isn't Antlers and the partial/listing don't see `.antlers.php`.

- [ ] **Step 3: Register the `.antlers.php` pattern**

In `src/main/resources/META-INF/plugin.xml`, change the fileType line's `patterns` attribute from
`patterns="*.antlers.html"` to:
```
patterns="*.antlers.html;*.antlers.php"
```

- [ ] **Step 4: Extend partial resolution (`StatamicProject.kt`)**

Change `val exts = listOf("antlers.html", "html")` to:
```kotlin
        val exts = listOf("antlers.html", "antlers.php", "html")
```
In `collectPartials`, change the branch
`} else if (child.name.endsWith(".antlers.html") || child.name.endsWith(".html")) {` to:
```kotlin
            } else if (child.name.endsWith(".antlers.html") || child.name.endsWith(".antlers.php") || child.name.endsWith(".html")) {
```
and the next line `val rel = relativePath(root, child)?.removeSuffix(".antlers.html")?.removeSuffix(".html") ?: continue` to:
```kotlin
                val rel = relativePath(root, child)?.removeSuffix(".antlers.html")?.removeSuffix(".antlers.php")?.removeSuffix(".html") ?: continue
```

- [ ] **Step 5: Extend the partial reference + searcher**

In `AntlersPartialReference.kt`, change `stripPartialExtensions`:
```kotlin
    private fun stripPartialExtensions(name: String): String =
        name.removeSuffix(".antlers.html").removeSuffix(".antlers.php").removeSuffix(".html")
```
and the `relativePathMinusExt` return line to:
```kotlin
        return rel.removeSuffix(".antlers.html").removeSuffix(".antlers.php").removeSuffix(".html")
```
In `AntlersPartialReferenceSearcher.kt`, change the guard to also accept `.antlers.php`:
```kotlin
        if (!vf.name.endsWith(".antlers.html") && !vf.name.endsWith(".antlers.php") && !vf.name.endsWith(".html")) return
```

- [ ] **Step 6: Extend the page-blueprint view-path strip (`PageBlueprintResolver.kt`)**

Change the `viewPathOf` return line:
```kotlin
        return rel.removeSuffix(".antlers.html").removeSuffix(".antlers.php")
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersPhpFileTest"`
Expected: PASS (3 tests). If `testAntlersPhpFileIsAntlers` fails, the `patterns` attribute didn't take —
confirm the `;`-separated value in plugin.xml.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/META-INF/plugin.xml \
        src/main/kotlin/com/github/balotias/intellijantlers/references/StatamicProject.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReference.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceSearcher.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/blueprint/PageBlueprintResolver.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/AntlersPhpFileTest.kt
git commit -m "$(cat <<'EOF'
feat: recognize *.antlers.php and resolve partials/views across both extensions

Registers the second Statamic view extension and teaches partial/view/blueprint
resolution about `.antlers.php`. Addresses Konafets antlers-idea #20.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Component B — PHP block grammar + injection host

**Files:**
- Modify: `src/main/grammar/Antlers.bnf` + regen `src/main/gen/**`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersPhpBlockBodyManipulator.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/testData/parsing/PhpBlock.txt` (regenerated) + `PhpEchoBlock.antlers.html`/`.txt` (new) + `AntlersParsingTest.kt`

> Scaffolding task: the behavioral coverage is the parsing corpus (Step 8). Verification = round-trip
> gate + `compileKotlin` + a clean corpus tree with no `PsiErrorElement`.

- [ ] **Step 1: Round-trip the parser toolchain (gate)**

```bash
GK=/Users/balotias/.gradle/caches/modules-2/files-2.1/org.jetbrains/grammar-kit/2022.3.2/6eb13410f38e69b40213d76dd36f1ea80fbbd832/grammar-kit-2022.3.2.jar
IDELIB=/Users/balotias/.gradle/caches/9.5.0/transforms/eec72b1cad4f6953a493ec271def7dbc/transformed/ideaIC-2025.2.6.2/lib
rm -rf /tmp/gk0 && mkdir -p /tmp/gk0
java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main /tmp/gk0 src/main/grammar/Antlers.bnf 2>&1 | grep -vi warn | tail -2
diff src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java /tmp/gk0/com/github/balotias/intellijantlers/parser/AntlersParser.java | grep -cE '^[<>]'
```
Expected: `0`. If non-zero → STOP, report BLOCKED.

- [ ] **Step 2: Edit the grammar**

In `src/main/grammar/Antlers.bnf`, replace:
```
phpBlock ::= T_PHP_RAW_OPEN T_PHP_TEXT? T_PHP_RAW_CLOSE
           | T_PHP_ECHO_OPEN T_PHP_TEXT? T_PHP_ECHO_CLOSE
```
with:
```
phpBlock ::= phpRawBlock | phpEchoBlock
phpRawBlock  ::= T_PHP_RAW_OPEN  phpBlockBody? T_PHP_RAW_CLOSE
phpEchoBlock ::= T_PHP_ECHO_OPEN phpBlockBody? T_PHP_ECHO_CLOSE
phpBlockBody ::= T_PHP_TEXT+ {
  mixin="com.github.balotias.intellijantlers.psi.AntlersPhpBlockBodyMixin"
  implements="com.intellij.psi.PsiLanguageInjectionHost"
}
```

- [ ] **Step 3: Regenerate the parser + PSI**

```bash
rm -rf /tmp/gk1 && mkdir -p /tmp/gk1
java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main /tmp/gk1 src/main/grammar/Antlers.bnf 2>&1 | grep -vi warn | tail -2
cp -r /tmp/gk1/com src/main/gen/
git status -s src/main/gen
```
Expected new/changed: `AntlersParser.java`, `AntlersVisitor.java`, new `psi/AntlersPhpRawBlock.java`,
`psi/AntlersPhpEchoBlock.java`, `psi/AntlersPhpBlockBody.java` + their `impl/*Impl.java`, and an updated
`psi/AntlersPhpBlock.java` (now a marker interface). (`AntlersTypes.java` unchanged — no new tokens.)

- [ ] **Step 4: Add the host mixin**

In `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt` (the
`AntlersFrontMatterBodyMixin` already imports `ElementManipulators`/`LiteralTextEscaper`/
`PsiLanguageInjectionHost`), add:
```kotlin
/** PHP block body (`T_PHP_TEXT+`) as a PHP injection host — same shape as the front-matter body. */
open class AntlersPhpBlockBodyMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {
    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost =
        ElementManipulators.handleContentChange(this, text) as PsiLanguageInjectionHost

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}
```

- [ ] **Step 5: Add the element manipulator**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersPhpBlockBodyManipulator.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersFileType
import com.github.balotias.intellijantlers.psi.AntlersPhpBlockBody
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil

/**
 * Write-back for edits made inside the injected PHP fragment: rebuild a `phpBlockBody` by parsing a
 * synthetic `{{? … ?}}` with the new content and splicing it in. A `?}}` in the new content would
 * re-close the synthetic block early, so we round-trip-check and bail rather than truncate.
 */
class AntlersPhpBlockBodyManipulator : AbstractElementManipulator<AntlersPhpBlockBody>() {
    override fun handleContentChange(
        element: AntlersPhpBlockBody,
        range: TextRange,
        newContent: String
    ): AntlersPhpBlockBody {
        val old = element.text
        val body = old.substring(0, range.startOffset) + newContent + old.substring(range.endOffset)
        val dummy = PsiFileFactory.getInstance(element.project)
            .createFileFromText("_php.antlers.html", AntlersFileType.INSTANCE, "{{? $body ?}}")
        val newBody = PsiTreeUtil.findChildOfType(dummy, AntlersPhpBlockBody::class.java) ?: return element
        if (newBody.text != body) return element
        return element.replace(newBody) as AntlersPhpBlockBody
    }
}
```
Register in `plugin.xml` (in the `com.intellij` extensions block):
```xml
        <lang.elementManipulator forClass="com.github.balotias.intellijantlers.psi.AntlersPhpBlockBody"
            implementationClass="com.github.balotias.intellijantlers.editor.AntlersPhpBlockBodyManipulator"/>
```

- [ ] **Step 6: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL. (If `AntlersPhpBlockBodyImpl` won't compile re: the host methods, the mixin in
Step 4 is missing/mismatched — fix the mixin, not the generated impl.)

- [ ] **Step 7: Regenerate the changed corpus golden + add an echo case**

The `PhpBlock.txt` golden tree changes shape (now `phpRawBlock` → `phpBlockBody`). Delete it and regenerate:
```bash
rm src/test/testData/parsing/PhpBlock.txt
```
Create `src/test/testData/parsing/PhpEchoBlock.antlers.html` with EXACTLY this one line (a `?` in the body
exercises `T_PHP_TEXT+`):
```
{{$ $featured ?: $latest $}}
```
Add to `src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt`, after `fun testPhpBlock() = doTest(true)`:
```kotlin
    fun testPhpEchoBlock() = doTest(true)
```

- [ ] **Step 8: Run the parsing tests (regenerate goldens, then verify)**

Run: `./gradlew --rerun-tasks test --tests "*AntlersParsingTest"`
The harness writes the missing `PhpBlock.txt` and `PhpEchoBlock.txt` on this first run (tests "fail" by
generating). Re-run the same command; expected PASS. Then OPEN both `.txt` files and verify:
- `PhpBlock.txt`: `AntlersPhpBlockImpl(PHP_BLOCK) > AntlersPhpRawBlockImpl(PHP_RAW_BLOCK) > {{?, AntlersPhpBlockBodyImpl(PHP_BLOCK_BODY) > T_PHP_TEXT, ?}}`, no `PsiErrorElement`.
- `PhpEchoBlock.txt`: an `AntlersPhpEchoBlockImpl` containing an `AntlersPhpBlockBodyImpl` with **multiple**
  `T_PHP_TEXT` leaves (the `?` split), no `PsiErrorElement`. If you see `PsiErrorElement`, STOP and report.

- [ ] **Step 9: Commit**

```bash
git add src/main/grammar/Antlers.bnf src/main/gen \
        src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersPhpBlockBodyManipulator.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt \
        src/test/testData/parsing/PhpBlock.txt src/test/testData/parsing/PhpEchoBlock.antlers.html \
        src/test/testData/parsing/PhpEchoBlock.txt
git commit -m "$(cat <<'EOF'
feat(grammar): php block body is a PsiLanguageInjectionHost (+ T_PHP_TEXT+)

Splits phpBlock into phpRawBlock/phpEchoBlock with a phpBlockBody host that
accepts multiple T_PHP_TEXT tokens (fixes a `?`/`$`-in-body parse gap). PHP
injection is added in the next task.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Component B — the PHP injector

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjector.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjectionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjectionTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.psi.AntlersPhpBlockBody
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPhpInjectionTest : BasePlatformTestCase() {

    private fun phpBody(text: String): AntlersPhpBlockBody {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.findChildOfType(antlers, AntlersPhpBlockBody::class.java)!!
    }

    fun testEchoBlockUsesShortEchoPrefix() {
        assertEquals("<?= ", AntlersPhpInjector.prefixFor(phpBody("{{\$ \$x \$}}")))
    }

    fun testRawBlockUsesPhpOpenPrefix() {
        assertEquals("<?php ", AntlersPhpInjector.prefixFor(phpBody("{{? \$x = 1; ?}}")))
    }

    fun testNoInjectionWithoutPhpPlugin() {
        // CI (IDEA Community) has no PHP plugin → the injector no-ops gracefully (no crash, no injection).
        myFixture.configureByText("p.antlers.html", "{{? \$x = 1; ?}}")
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val ilm = InjectedLanguageManager.getInstance(project)
        assertNull(ilm.findInjectedElementAt(antlers, antlers.text.indexOf("\$x")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersPhpInjectionTest"`
Expected: FAIL — compile error (`AntlersPhpInjector` unresolved).

- [ ] **Step 3: Write the injector + pure helper**

Create `src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjector.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.psi.AntlersPhpBlockBody
import com.github.balotias.intellijantlers.psi.AntlersPhpEchoBlock
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Injects the real PHP language into `{{$ … $}}` / `{{? … ?}}` block bodies, when the PHP plugin is
 * present (PhpStorm / IDEA Ultimate). PHP is resolved by id so there is no compile-time PHP dependency;
 * absent → graceful no-op.
 */
class AntlersPhpInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(AntlersPhpBlockBody::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is AntlersPhpBlockBody) return
        val php = Language.findLanguageByID("PHP") ?: return
        val host = context as PsiLanguageInjectionHost
        registrar.startInjecting(php)
            .addPlace(prefixFor(context), "", host, TextRange(0, context.textLength))
            .doneInjecting()
    }

    companion object {
        /** `<?= ` for an echo block (a PHP expression) else `<?php ` (raw block statements). */
        fun prefixFor(body: AntlersPhpBlockBody): String =
            if (body.parent is AntlersPhpEchoBlock) "<?= " else "<?php "
    }
}
```
Register in `plugin.xml` (in the `com.intellij` extensions block):
```xml
        <multiHostInjector implementation="com.github.balotias.intellijantlers.injection.AntlersPhpInjector"/>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersPhpInjectionTest"`
Expected: PASS (3 tests). `testNoInjectionWithoutPhpPlugin` passes because CI has no PHP plugin
(`findLanguageByID("PHP")` is null). If it instead finds PHP (only possible if the test IDE bundles it),
that test is environment-specific — report it rather than deleting it.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjector.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjectionTest.kt
git commit -m "$(cat <<'EOF'
feat(injection): inject PHP into {{$ }} / {{? }} blocks (PhpStorm)

A MultiHostInjector resolves PHP via Language.findLanguageByID("PHP") (no hard
dependency; no-op where absent) and injects with `<?= ` (echo) / `<?php ` (raw)
prefixes. Addresses Konafets antlers-idea #166.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Full-suite regression gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml && echo FAIL || echo "GATE CLEAN"`
Expected: `GATE CLEAN`.

If a parsing test other than the PhpBlock ones regressed: the grammar restructure only touched `phpBlock`;
investigate. If a partial/reference/blueprint test regressed: the extension-list additions must be additive
(`.antlers.html` still resolves first) — do not edit other tests to go green.

## Self-Review

- **Spec coverage:** A1 pattern → Task 1 Step 3; A2 all five sites → Task 1 Steps 4-6 (StatamicProject
  exts + collectPartials, AntlersPartialReference two strips, the searcher gate, PageBlueprintResolver) with
  tests for file-recognition + partial-resolves + listPartials (the PageBlueprintResolver `.antlers.php`
  strip is a one-line parallel of the tested `.antlers.html` path, covered by the full gate); B1 grammar
  host + mixin + manipulator + `T_PHP_TEXT+` → Task 2; B2 injector + pure `prefixFor` helper + graceful
  no-op → Task 3; corpus (raw + echo-with-`?`) → Task 2 Steps 7-8. The PhpStorm-only real-PHP highlighting
  is documented as manual (spec "Out of scope"/Testing). No gaps.
- **Placeholder scan:** none — full code + exact regen commands and anchor text in every step.
- **Type consistency:** `AntlersPhpBlockBody` (generated, Task 2) is used in the mixin, manipulator,
  injector, and tests; `AntlersPhpEchoBlock` (generated) gates `prefixFor`; `prefixFor` returns the same
  `"<?= "`/`"<?php "` the injector uses and the tests assert; the extension strings (`.antlers.php`,
  `antlers.php`) are consistent across all five Component-A sites.
