# Antlers Modifiers-in-Conditions Implementation Plan

> One grammar rule + regenerated `AntlersParser.java`. Execute INLINE. Round-trip the regen toolchain BEFORE editing.

**Spec:** `docs/superpowers/specs/2026-06-03-antlers-modifiers-in-conditions-design.md`
**Branch:** `antlers-modifiers-in-conditions` (from `main`).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty).

**Regen recipe (standalone grammar-kit — the in-project plugin conflicts with IPGP):**
```bash
GK=/Users/balotias/.gradle/caches/modules-2/files-2.1/org.jetbrains/grammar-kit/2022.3.2/6eb13410f38e69b40213d76dd36f1ea80fbbd832/grammar-kit-2022.3.2.jar
IDELIB=/Users/balotias/.gradle/caches/9.5.0/transforms/eec72b1cad4f6953a493ec271def7dbc/transformed/ideaIC-2025.2.6.2/lib
# (verify both exist; if the transform hash changed, re-find: find ~/.gradle/caches -path '*ideaIC-2025.2.6.2/lib/app.jar')
java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main <OUT_DIR> src/main/grammar/Antlers.bnf
```

---

### Task 1: Round-trip — toolchain reproduces the committed parser

- [ ] Snapshot: `cp src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java /tmp/AP.committed.java`
- [ ] Regen from the UNCHANGED grammar to a temp dir:
  `rm -rf /tmp/gkc && mkdir -p /tmp/gkc && java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main /tmp/gkc src/main/grammar/Antlers.bnf 2>&1 | grep -v WARN | tail -2`
- [ ] Diff: `diff src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java /tmp/gkc/com/github/balotias/intellijantlers/parser/AntlersParser.java | grep -cE '^[<>]'`
  Expect **0** (byte-identical). If non-zero and it's not just a header banner → STOP and report (toolchain mismatch). Also confirm the PSI dir is identical:
  `for f in src/main/gen/com/github/balotias/intellijantlers/psi/*.java; do diff "$f" "/tmp/gkc/com/github/balotias/intellijantlers/psi/$(basename "$f")" >/dev/null || echo "DIFF $(basename "$f")"; done` → no output.

---

### Task 2: Edit the grammar + regenerate

- [ ] In `src/main/grammar/Antlers.bnf`, change:
```
condition ::= <<atConditionKeyword>> conditionKeyword exprToken_* {
  mixin="com.github.balotias.intellijantlers.psi.AntlersConditionMixin"
}
```
to:
```
condition ::= <<atConditionKeyword>> conditionKeyword (modifier | exprToken_)* {
  mixin="com.github.balotias.intellijantlers.psi.AntlersConditionMixin"
}
```

- [ ] Regen to a temp dir:
  `rm -rf /tmp/gkc2 && mkdir -p /tmp/gkc2 && java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main /tmp/gkc2 src/main/grammar/Antlers.bnf 2>&1 | grep -v WARN | tail -2`
- [ ] Verify ONLY `AntlersParser.java` changed (no new PSI / no lexer change):
  `for f in src/main/gen/com/github/balotias/intellijantlers/psi/*.java src/main/gen/com/github/balotias/intellijantlers/psi/impl/*.java; do diff "$f" "/tmp/gkc2/${f#src/main/gen/}" >/dev/null || echo "PSI DIFF $(basename "$f")"; done` → no output. (If a PSI file differs → STOP, an assumption was wrong.)
- [ ] Copy the regenerated parser in:
  `cp /tmp/gkc2/com/github/balotias/intellijantlers/parser/AntlersParser.java src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java`
- [ ] `./gradlew compileKotlin` → BUILD SUCCESSFUL. `git status -s` → only `Antlers.bnf` + `AntlersParser.java`.
- [ ] Commit:
```bash
git add src/main/grammar/Antlers.bnf src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java
git commit -m "$(cat <<'EOF'
fix(grammar): allow modifiers inside conditions (if x | contains("<style"))

condition now accepts `(modifier | exprToken_)*`, so a `| modifier` in a
condition parses (and is highlighted) instead of producing a parse error.
Regenerated AntlersParser.java; no token/PSI/lexer change.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Tests + corpus

- [ ] **Focused test** — create
  `src/test/kotlin/com/github/balotias/intellijantlers/AntlersConditionModifierTest.kt`:
```kotlin
package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.psi.AntlersModifier
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersConditionModifierTest : BasePlatformTestCase() {

    private fun errors(text: String): List<String> {
        val file = myFixture.configureByText("p.antlers.html", text)
        return PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).map { it.errorDescription }
    }

    fun testModifierInIfParsesCleanly() {
        assertEquals("no errors: ${errors("{{ if title | upper }}")}", emptyList<String>(), errors("{{ if title | upper }}"))
    }

    fun testContainsWithAngleBracketStringParses() {
        // The reported case — the `<style` string is fine; the `|` was the problem.
        assertEquals(emptyList<String>(), errors("{{ if code | contains(\"<style\") }}{{ /if }}"))
    }

    fun testUnlessAndElseifAndChainedModifiers() {
        assertEquals(emptyList<String>(), errors("{{ unless items | count }}{{ /unless }}"))
        assertEquals(emptyList<String>(), errors("{{ if a | upper | lower }}{{ /if }}"))
        assertEquals(emptyList<String>(), errors("{{ if title | lower == \"x\" }}{{ /if }}"))
    }

    fun testModifierNodeInsideCondition() {
        myFixture.configureByText("p.antlers.html", "{{ if code | contains(\"<style\") }}{{ /if }}")
        val mod = PsiTreeUtil.findChildOfType(myFixture.file, AntlersModifier::class.java)
        assertNotNull("a modifier node exists inside the condition", mod)
        assertEquals("contains", (mod as AntlersModifierMixin).modifierName)
    }
}
```
- [ ] Run `./gradlew --rerun-tasks test --tests "*AntlersConditionModifierTest"` → PASS.
- [ ] **Corpus** — add `src/test/testData/parsing/ConditionModifier.antlers.html`
  (`{{ if code | contains("<style") }}{{ /if }}`) and `fun testConditionModifier() = doTest(true)` to
  `AntlersParsingTest`. Run `--tests "*AntlersParsingTest"`; the harness writes `ConditionModifier.txt`
  (or prints the actual tree) — save it, re-run to confirm green, and inspect that the tree shows the
  `AntlersModifierImpl` **inside** the `AntlersConditionImpl`.
- [ ] **Full gate**: `./gradlew --rerun-tasks test` + grep (empty). If a balance/scope/highlight test
  regressed, evaluate (new parse correct → update; else investigate). Report any test changed.
- [ ] Commit:
```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/AntlersConditionModifierTest.kt \
        src/test/testData/parsing/ConditionModifier.antlers.html src/test/testData/parsing/ConditionModifier.txt \
        src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt
git commit -m "$(cat <<'EOF'
test: cover modifiers in conditions (parse + node + corpus)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Self-Review
- Round-trip gate de-risks the regen; the temp-dir diff proves no new PSI / no lexer change.
- One grammar rule (`(modifier | exprToken_)*`); `modifier` is the existing rule, `T_PIPE` only via it.
- Tests pin the reported `contains("<style")` case + chained/parens/elseif + the modifier node; corpus
  shows the modifier inside the condition; full suite guards downstream (balance/scope/highlight).
