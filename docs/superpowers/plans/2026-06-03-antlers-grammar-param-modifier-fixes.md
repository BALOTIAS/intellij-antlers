# Antlers Grammar Fixes Implementation Plan

> Bound params (`{{ partial :src="x" }}`) + colon-arg modifiers (`{{ x | truncate:10 }}`). Grammar-only `.bnf` edits + regenerated `AntlersParser.java`. **Execute INLINE** (the regen is fragile/iterative). REQUIRED: validate the regen toolchain (round-trip) BEFORE editing the grammar.

**Reference spec:** `docs/superpowers/specs/2026-06-03-antlers-grammar-param-modifier-fixes-design.md`
**Branch:** `antlers-grammar-param-modifier-fixes` (already created).

**Test gate:** `./gradlew --rerun-tasks test` then
`grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` — must print nothing.

**Grammarkit scaffolding (temporary; reverted before the final commit).** Add to `settings.gradle.kts`
`pluginManagement { plugins { … } }`: `id("org.jetbrains.grammarkit") version "2022.3.2.2"`. Add to
`build.gradle.kts`: `id("org.jetbrains.grammarkit")` in the `plugins {}` block, plus:
```kotlin
import org.jetbrains.grammarkit.tasks.GenerateParserTask
tasks.register<GenerateParserTask>("genAntlersParser") {
    sourceFile.set(file("src/main/grammar/Antlers.bnf"))
    targetRootOutputDir.set(file("src/main/gen"))
    pathToParser.set("com/github/balotias/intellijantlers/parser/AntlersParser.java")
    pathToPsiRoot.set("com/github/balotias/intellijantlers/psi")
}
```
Always run the generator with `--no-configuration-cache` so it does not poison the normal build's
config cache. Run ONLY `genAntlersParser` while grammarkit is applied — never `test`/`check`.

---

### Task 1: Round-trip — prove the toolchain reproduces the committed parser

- [ ] **Step 1: Snapshot the committed parser**
```bash
cp src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java /tmp/AntlersParser.committed.java
```

- [ ] **Step 2: Add the temporary grammarkit scaffolding** (settings + build.gradle, as above).

- [ ] **Step 3: Regenerate from the UNCHANGED grammar**
```bash
./gradlew genAntlersParser --no-configuration-cache 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`. If the task fails to resolve/run, capture the error.

- [ ] **Step 4: Diff regenerated vs committed (logic only)**
```bash
diff <(grep -vE '^//|generated|GENERATED|GeneratedParser|^\s*\*' /tmp/AntlersParser.committed.java) \
     <(grep -vE '^//|generated|GENERATED|GeneratedParser|^\s*\*' src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java) \
  | head -60
```
**Decision gate:** if the only differences are header/banner/comment lines (cosmetic), the toolchain is
trustworthy → proceed to Task 2. If parser *method/logic* bodies differ, STOP: restore the committed
file (`cp /tmp/AntlersParser.committed.java <the path>`), revert the scaffolding, and report — we will
not regenerate.

- [ ] **Step 5: Restore the committed parser before grammar edits** (so Task 2's diff is clean)
```bash
cp /tmp/AntlersParser.committed.java src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java
```

---

### Task 2: Edit the grammar + regenerate

- [ ] **Step 1: Edit `src/main/grammar/Antlers.bnf` — namePath not-predicate.** Replace:
```
namePath ::= pathSegment ((T_COLON | T_DOT) pathSegment | bracketAccess)* {
  mixin="com.github.balotias.intellijantlers.psi.AntlersNamePathMixin"
}
```
with:
```
namePath ::= pathSegment ((T_COLON pathSegment !T_EQUALS) | T_DOT pathSegment | bracketAccess)* {
  mixin="com.github.balotias.intellijantlers.psi.AntlersNamePathMixin"
}
```

- [ ] **Step 2: Edit `Antlers.bnf` — colon-arg modifier.** Replace:
```
modifier ::= T_PIPE T_IDENT (T_LPAREN argList? T_RPAREN)? {
  mixin="com.github.balotias.intellijantlers.psi.AntlersModifierMixin"
}
```
with:
```
modifier ::= T_PIPE T_IDENT ((T_LPAREN argList? T_RPAREN) | (T_COLON modifierArg_)+)? {
  mixin="com.github.balotias.intellijantlers.psi.AntlersModifierMixin"
}
private modifierArg_ ::= T_STRING | T_NUMBER | T_DOLLAR? T_IDENT
```

- [ ] **Step 3: Regenerate**
```bash
./gradlew genAntlersParser --no-configuration-cache 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify ONLY `AntlersParser.java` changed under `gen/`** (the private-rule assumption)
```bash
git status --porcelain src/main/gen
```
Expected: only `AntlersParser.java` modified. If a PSI interface/impl or the lexer also changed, STOP
and report (an assumption was wrong — the spec said no new PSI nodes).

- [ ] **Step 5: Remove the grammarkit scaffolding** (settings + build.gradle back to original) and clear
the config cache so the normal build is restored:
```bash
rm -rf .gradle/configuration-cache 2>/dev/null; true
```
Then confirm a clean compile: `./gradlew compileKotlin 2>&1 | tail -5` → `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the grammar + parser** (scaffolding already removed)
```bash
git add src/main/grammar/Antlers.bnf src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java
git commit -m "$(cat <<'EOF'
fix(grammar): parse bound params after a tag name and colon-arg modifiers

namePath no longer swallows `:name=` (a `!T_EQUALS` not-predicate), so
`{{ partial :src="x" }}` parses `:src="x"` as a parameter; modifier now accepts
`:arg` form so `{{ x | truncate:10 }}` keeps `:10` inside the modifier.
Regenerated AntlersParser.java; no token/PSI/lexer changes.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Retrain corpus + focused PSI tests

- [ ] **Step 1: Regenerate the two affected corpus expected trees.** Delete the stale expected dumps so
`ParsingTestCase` re-creates them (or run and capture the new actual):
```bash
rm src/test/testData/parsing/BoundParam.txt src/test/testData/parsing/ModifierChain.txt
./gradlew --rerun-tasks test --tests "*AntlersParsingTest" 2>&1 | tail -15
```
Run again to confirm the recreated `.txt` files now pass. **Inspect both regenerated trees**:
`BoundParam.txt` must now contain an `AntlersParameter` node covering `:src="hero"`; `ModifierChain.txt`
must show `:10` inside the `AntlersModifier`. If they still show the degraded (loose-token) shape, the
grammar edit did not take effect — STOP and investigate.

- [ ] **Step 2: Add focused PSI assertions.** Create
`src/test/kotlin/com/github/balotias/intellijantlers/AntlersGrammarParamModifierTest.kt`:
```kotlin
package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.psi.AntlersModifier
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameter
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersGrammarParamModifierTest : BasePlatformTestCase() {

    private fun configure(text: String) = myFixture.configureByText("p.antlers.html", text)

    private fun errors() =
        PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java)

    fun testBoundParamParsesAsParameter() {
        configure("{{ partial :src=\"hero\" }}")
        val param = PsiTreeUtil.findChildOfType(myFixture.file, AntlersParameter::class.java)
        assertNotNull("`:src=\"hero\"` should parse as a parameter, not be swallowed into the name-path", param)
        assertTrue("the parameter text covers the bound name", param!!.text.contains(":src"))
        assertTrue("no parse errors: ${errors().map { it.errorDescription }}", errors().isEmpty())
    }

    fun testColonArgModifierKeepsArgInsideModifier() {
        configure("{{ title | truncate:10 }}")
        val mod = PsiTreeUtil.findChildOfType(myFixture.file, AntlersModifier::class.java)
        assertNotNull(mod)
        assertEquals("truncate", (mod as AntlersModifierMixin).modifierName)
        assertTrue("the modifier node spans the `:10` colon-arg", mod.text.replace(" ", "").contains("truncate:10"))
        assertTrue("no parse errors: ${errors().map { it.errorDescription }}", errors().isEmpty())
    }

    fun testShorthandHandleStillOneNamePath() {
        // Regression: `collection:blog` (no `=`) is still a single name-path, not a param.
        configure("{{ collection:blog }}{{ /collection:blog }}")
        val np = PsiTreeUtil.findChildOfType(myFixture.file, AntlersNamePathMixin::class.java)
        assertNotNull(np)
        assertTrue("collection:blog stays one name-path", np!!.text.contains("collection:blog"))
        assertTrue(errors().isEmpty())
    }
}
```
(If `AntlersModifierMixin.modifierName` or the `AntlersParameter`/`AntlersModifier` PSI names differ from
these imports, adjust to the real API — read `psi/AntlersMixins.kt` and `gen/.../psi/`.)

- [ ] **Step 3: Spot-check the param-value classifier is unaffected.** Confirm an existing test like
`AntlersParamValueCompletionTest` (or add one assertion) still classifies a value position inside a
bound param. Run `./gradlew --rerun-tasks test --tests "*AntlersGrammarParamModifierTest" --tests "*AntlersParsingTest" --tests "*AntlersCompletionContextTest" --tests "*AntlersShorthandInsertTest"`.
Expected: all pass.

- [ ] **Step 4: Full-suite gate**
```bash
./gradlew --rerun-tasks test
grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml
```
Expected: prints nothing. If a previously-passing test now fails because it depended on the *degraded*
parse, evaluate: is the new parse correct (update that test) or did we break something (revert)? Report
any test changed.

- [ ] **Step 5: Commit**
```bash
git add src/test/testData/parsing/BoundParam.txt src/test/testData/parsing/ModifierChain.txt \
        src/test/kotlin/com/github/balotias/intellijantlers/AntlersGrammarParamModifierTest.kt
git commit -m "$(cat <<'EOF'
test: retrain corpus to corrected trees; assert bound-param/colon-arg parsing

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review
- Round-trip gate (Task 1) de-risks the regen before any change; the scaffolding is temporary and removed
  before commits; `git status gen/` (Task 2 Step 4) verifies the no-new-PSI assumption.
- Both `.bnf` edits match the spec exactly; `modifierArg_` is private (no new PSI).
- Corpus before/after is the proof; the new test pins the corrected PSI; a shorthand regression guard is
  included; the full suite (333 tests) is the safety net.
- INLINE execution chosen because grammar-edit → regenerate → corpus-retrain is iterative and coupled.
