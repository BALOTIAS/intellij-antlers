# Antlers Enter-Expand Block Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pressing Enter once inside an empty matched block (`{{ collection }}‹caret›{{ /collection }}`) expands it to an indented three-line structure with the closer on its own line.

**Architecture:** One new `EnterHandlerDelegate` (`AntlersEnterHandler`) registered via `<enterHandlerDelegate>`. It text-scans for the empty-one-line-block shape and, when matched, rewrites the empty region with computed indentation (from code-style options) and returns `Stop`. No PSI/formatter/grammar dependency.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`EnterHandlerDelegateAdapter`, `CodeStyle`), `BasePlatformTestCase`, Gradle.

**Reference spec:** `docs/superpowers/specs/2026-06-03-antlers-enter-expand-block-design.md`

**Branch:** `antlers-enter-expand-block` (create from `main` before Task 1).

**Test gate (after every test run):** `./gradlew --rerun-tasks test` then
`grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` — must print nothing. `--rerun-tasks` is required (Gradle caches).

---

### Task 1: Enter handler + registration + tests

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersEnterHandler.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Create: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersEnterHandlerTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersEnterHandlerTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.intellij.psi.codeStyle.CodeStyle
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersEnterHandlerTest : BasePlatformTestCase() {

    /** Type one Enter at the caret and return the resulting document text. */
    private fun enter(textWithCaret: String): String {
        myFixture.configureByText("p.antlers.html", textWithCaret)
        myFixture.type("\n")
        return myFixture.editor.document.text
    }

    /** One indent unit per the file's code style (tab or N spaces). */
    private fun unit(): String {
        val o = CodeStyle.getIndentOptions(myFixture.file)
        return if (o.USE_TAB_CHARACTER) "\t" else " ".repeat(o.INDENT_SIZE)
    }

    fun testExpandsColZeroBlock() {
        myFixture.configureByText("p.antlers.html", "{{ collection }}<caret>{{ /collection }}")
        myFixture.type("\n")
        assertEquals("{{ collection }}\n${unit()}\n{{ /collection }}", myFixture.editor.document.text)
        assertEquals("{{ collection }}\n${unit()}".length, myFixture.caretOffset)
    }

    fun testExpandsIndentedOpener() {
        assertEquals(
            "  {{ if x }}\n  ${unit()}\n  {{ /if }}",
            enter("  {{ if x }}<caret>{{ /if }}"),
        )
    }

    fun testWhitespaceBetweenNormalizes() {
        assertEquals(
            "{{ collection }}\n${unit()}\n{{ /collection }}",
            enter("{{ collection }} <caret> {{ /collection }}"),
        )
    }

    fun testExpandsLogicBlock() {
        assertEquals(
            "{{ unless x }}\n${unit()}\n{{ /unless }}",
            enter("{{ unless x }}<caret>{{ /unless }}"),
        )
    }

    fun testNonEmptyBlockDoesNotFire() {
        // Caret not directly after "}}" → normal Enter (exactly one newline inserted).
        val r = enter("{{ collection }}foo<caret>{{ /collection }}")
        assertEquals("only the default newline was inserted", 1, r.count { it == '\n' })
    }

    fun testAlreadyMultilineDoesNotDoubleExpand() {
        // A newline precedes the caret on the backward scan → handler does not fire.
        val r = enter("{{ collection }}\n<caret>\n{{ /collection }}")
        assertEquals("started with 2 newlines, default Enter adds one", 3, r.count { it == '\n' })
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*AntlersEnterHandlerTest"`
Expected: FAIL — `testExpandsColZeroBlock`, `testExpandsIndentedOpener`, `testWhitespaceBetweenNormalizes`, `testExpandsLogicBlock` fail (no expansion yet). `testNonEmptyBlockDoesNotFire` and `testAlreadyMultilineDoesNotDoubleExpand` should already pass (default Enter).

- [ ] **Step 3: Create the Enter handler**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersEnterHandler.kt`:

```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyle

/**
 * Expands an empty one-line matched block on a single Enter:
 * `{{ collection }}<caret>{{ /collection }}` becomes the opener, an indented caret line, and the
 * closer on its own line at the opener's indent. Fires only when, ignoring spaces/tabs (but not
 * newlines), the caret sits between an opener's `}}` and a closing tag `{{ /… }}`. Indentation is
 * computed from the code-style options, not the formatter.
 */
class AntlersEnterHandler : EnterHandlerDelegateAdapter() {

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffsetRef: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?,
    ): EnterHandlerDelegate.Result {
        if (file.viewProvider.baseLanguage !== AntlersLanguage.INSTANCE) return EnterHandlerDelegate.Result.Continue
        if (editor.caretModel.caretCount != 1) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        val text = document.charsSequence
        val caret = editor.caretModel.offset

        // Backward over spaces/tabs (stop at a newline) → must end on "}}".
        var i = caret
        while (i > 0 && (text[i - 1] == ' ' || text[i - 1] == '\t')) i--
        if (i < 2 || text[i - 1] != '}' || text[i - 2] != '}') return EnterHandlerDelegate.Result.Continue
        val openerEnd = i

        // Forward over spaces/tabs (stop at a newline) → must be a closing tag "{{" … "/".
        var j = caret
        while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
        if (j + 1 >= text.length || text[j] != '{' || text[j + 1] != '{') return EnterHandlerDelegate.Result.Continue
        var k = j + 2
        while (k < text.length && (text[k] == ' ' || text[k] == '\t')) k++
        if (k >= text.length || text[k] != '/') return EnterHandlerDelegate.Result.Continue
        val closerStart = j

        // Leading whitespace of the opener's line.
        var lineStart = openerEnd
        while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
        var e = lineStart
        while (e < openerEnd && (text[e] == ' ' || text[e] == '\t')) e++
        val openerIndent = text.subSequence(lineStart, e).toString()

        val opts = CodeStyle.getIndentOptions(file)
        val unit = if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
        val body = openerIndent + unit

        document.replaceString(openerEnd, closerStart, "\n$body\n$openerIndent")
        editor.caretModel.moveToOffset(openerEnd + 1 + body.length)
        return EnterHandlerDelegate.Result.Stop
    }
}
```

- [ ] **Step 4: Register it in `plugin.xml`**

In `src/main/resources/META-INF/plugin.xml`, inside the `<extensions defaultExtensionNs="com.intellij">` block, immediately after the existing line
`<editorActionHandler action="EditorTab" implementationClass="com.github.balotias.intellijantlers.editor.AntlersParamTabHandler"/>`
add:

```xml
        <enterHandlerDelegate implementation="com.github.balotias.intellijantlers.editor.AntlersEnterHandler"/>
```

(The `AntlersParamTabHandler` registration spans two lines — add the new line after its closing `/>`.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*AntlersEnterHandlerTest"`
Expected: PASS (6 tests).

If `testExpandsColZeroBlock` shows a different indent than `unit()`, print `myFixture.editor.document.text` to inspect — the intended result is opener line, then `openerIndent + one code-style unit` on the caret line, then the closer at `openerIndent`. Do not weaken the assertion to match a wrong actual; the indent must come from `CodeStyle.getIndentOptions`.

- [ ] **Step 6: Full suite gate**

Run: `./gradlew --rerun-tasks test`
Then: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml`
Expected: prints nothing. (A new `enterHandlerDelegate` only acts on the empty-block shape and returns `Continue` otherwise, so existing tests should be unaffected.)

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersEnterHandler.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersEnterHandlerTest.kt
git commit -m "$(cat <<'EOF'
feat: expand an empty matched block on a single Enter

Caret between an opener's }} and a closing tag {{ /… }} (whitespace only
between) -> opener / indented caret line / closer on its own line. Computes
indentation from code-style options; returns Stop so the default Enter does
not also fire.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (completed during planning)

- **Spec coverage:** `EnterHandlerDelegateAdapter.preprocessEnter` + registration ✓; backward `}}` / forward `{{ /` detection with whitespace-only-between and newline stops ✓; opener-indent + code-style unit, closer at opener indent, caret on middle line, return `Stop` ✓; all six spec tests E1–E6 mapped ✓.
- **Trace checks:** col-0 (`openerEnd=16`, `openerIndent=""`, replacement `"\n    \n"`, caret `=16+1+4=21`) ✓; indented `  {{ if x }}` (`openerIndent="  "`, body 6 spaces) ✓; whitespace-between replaces the spaces in `[openerEnd, closerStart)` ✓; `}}foo<caret>` backward hits `o` → Continue ✓; `\n<caret>` backward hits `\n` → Continue ✓.
- **No placeholders:** every code step is complete; run steps have commands + expected results; Step 5 forbids weakening the indent assertion.
- **Type/EP check:** `Ref<Int>` overrides the Java `Ref<Integer>` parameter; `EnterHandlerDelegate.Result.{Continue,Stop}`; EP `enterHandlerDelegate`.
```
