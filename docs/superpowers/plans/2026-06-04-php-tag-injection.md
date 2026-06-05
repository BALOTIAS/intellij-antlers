# Native `<?php ?>` + Hardened PHP Injection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recognize literal `<?php … ?>` and `<?= … ?>` tags in Antlers files and inject real PHP into them, reusing and hardening the existing `{{? ?}}` / `{{$ $}}` injection.

**Architecture:** Add a `PHP_TAG` lexer state + three tokens; add `phpTagBlock` / `phpEchoTagBlock` grammar rules that share the existing `phpBlockBody` injection host; generalize the injector's prefix; color the new delimiters. The generated lexer/parser in `src/main/gen` are regenerated **standalone** (codegen is decoupled from the build).

**Tech Stack:** Kotlin, IntelliJ Platform SDK, JFlex (`AntlersLexer.flex`), Grammar-Kit (`Antlers.bnf`). Build/test: `./gradlew --rerun-tasks test` (`--rerun-tasks` REQUIRED — Gradle caches). Gate: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` must print nothing.

**Codegen recipe (used in Tasks 1 & 2):**
```bash
# PARSER (Antlers.bnf -> AntlersParser.java + PSI). Regenerate into a temp dir, diff, copy changed files.
GK=~/.gradle/caches/modules-2/files-2.1/org.jetbrains/grammar-kit/2022.3.2/6eb13410f38e69b40213d76dd36f1ea80fbbd832/grammar-kit-2022.3.2.jar
IDELIB=$(find ~/.gradle/caches -type d -path '*ideaIC-2025.2.6.2/lib' | head -1)
java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main "$OUT" src/main/grammar/Antlers.bnf

# LEXER (AntlersLexer.flex -> _AntlersLexer.java). Forked JetBrains jflex; IntelliJ skeleton built in.
JFLEX=~/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps.jflex/jflex/1.9.2/e866267b1b1e983b3c316c350e9fa9a9656606d7/jflex-1.9.2.jar
java -cp "$JFLEX" jflex.Main -d "$OUT" --nobak src/main/grammar/AntlersLexer.flex
```
**ALWAYS round-trip first:** regenerate the UNCHANGED source into a temp dir and confirm it is byte-identical to the committed `src/main/gen` before editing. The parser must be regenerated BEFORE the lexer (the lexer references the new `AntlersTypes.*` constants the parser generates).

---

## File Structure

- `src/main/grammar/Antlers.bnf` (modify) — 3 new tokens; `phpBlock` extended; `phpTagBlock`/`phpEchoTagBlock` rules.
- `src/main/gen/**` (regenerated) — `AntlersTypes`, `AntlersParser`, new `AntlersPhpTagBlock`/`AntlersPhpEchoTagBlock` PSI, `AntlersPhpBlockImpl`, `AntlersVisitor`, `_AntlersLexer`.
- `src/main/grammar/AntlersLexer.flex` (modify) — `PHP_TAG` state, `<?php`/`<?=` rules, reworked CONTENT catch-all.
- `src/main/kotlin/.../injection/AntlersPhpInjector.kt` (modify) — generalize `prefixFor`.
- `src/main/kotlin/.../highlighting/AntlersSyntaxHighlighter.kt` (modify) — color new delimiters.
- Tests: `LexerTest`, `AntlersParsingTest` + fixtures, `AntlersPhpInjectionTest`, a highlighter test.

---

### Task 1: Grammar tokens + block rules (regenerate parser)

**Files:**
- Modify: `src/main/grammar/Antlers.bnf`
- Regenerate: `src/main/gen/**`

- [ ] **Step 1: Round-trip the parser unchanged (safety check)**

```bash
OUT=$(mktemp -d)
GK=~/.gradle/caches/modules-2/files-2.1/org.jetbrains/grammar-kit/2022.3.2/6eb13410f38e69b40213d76dd36f1ea80fbbd832/grammar-kit-2022.3.2.jar
IDELIB=$(find ~/.gradle/caches -type d -path '*ideaIC-2025.2.6.2/lib' | head -1)
java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main "$OUT" src/main/grammar/Antlers.bnf
diff -rq "$OUT" src/main/gen
```
Expected: `diff` prints nothing (byte-identical). If it differs, STOP — the toolchain/paths are wrong; report `NEEDS_CONTEXT`.

- [ ] **Step 2: Add the three tokens**

In `Antlers.bnf`, in the `tokens = [ … ]` block, immediately after the line `T_PHP_TEXT="T_PHP_TEXT"`, add:
```
    T_PHP_TAG_OPEN="<?php"
    T_PHP_ECHO_TAG_OPEN="<?="
    T_PHP_TAG_CLOSE="?>"
```

- [ ] **Step 3: Extend the phpBlock rules**

In `Antlers.bnf`, replace:
```
phpBlock ::= phpRawBlock | phpEchoBlock
phpRawBlock  ::= T_PHP_RAW_OPEN  phpBlockBody? T_PHP_RAW_CLOSE
phpEchoBlock ::= T_PHP_ECHO_OPEN phpBlockBody? T_PHP_ECHO_CLOSE
```
with:
```
phpBlock ::= phpRawBlock | phpEchoBlock | phpTagBlock | phpEchoTagBlock
phpRawBlock     ::= T_PHP_RAW_OPEN       phpBlockBody? T_PHP_RAW_CLOSE
phpEchoBlock    ::= T_PHP_ECHO_OPEN      phpBlockBody? T_PHP_ECHO_CLOSE
phpTagBlock     ::= T_PHP_TAG_OPEN       phpBlockBody? T_PHP_TAG_CLOSE?
phpEchoTagBlock ::= T_PHP_ECHO_TAG_OPEN  phpBlockBody? T_PHP_TAG_CLOSE?
```
(Leave `phpBlockBody ::= T_PHP_TEXT+ { … }` exactly as is.)

- [ ] **Step 4: Regenerate the parser and copy changed files**

```bash
OUT=$(mktemp -d)
GK=~/.gradle/caches/modules-2/files-2.1/org.jetbrains/grammar-kit/2022.3.2/6eb13410f38e69b40213d76dd36f1ea80fbbd832/grammar-kit-2022.3.2.jar
IDELIB=$(find ~/.gradle/caches -type d -path '*ideaIC-2025.2.6.2/lib' | head -1)
java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main "$OUT" src/main/grammar/Antlers.bnf
diff -rq "$OUT" src/main/gen      # review which files changed / are new
cp -R "$OUT"/. src/main/gen/      # copy regenerated tree over src/main/gen
```
Expected new/changed files include: `AntlersTypes.java` (new `T_PHP_TAG_OPEN`/`T_PHP_ECHO_TAG_OPEN`/`T_PHP_TAG_CLOSE` + element types `PHP_TAG_BLOCK`/`PHP_ECHO_TAG_BLOCK`), `AntlersParser.java`, new `psi/AntlersPhpTagBlock.java` + `psi/AntlersPhpEchoTagBlock.java` + their `impl/…Impl.java`, an updated `impl/AntlersPhpBlockImpl.java` (new `getPhpTagBlock()`/`getPhpEchoTagBlock()` accessors), and `AntlersVisitor.java`.

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew --rerun-tasks compileKotlin`
Expected: BUILD SUCCESSFUL. Confirm the new constants exist:
```bash
grep -c "T_PHP_TAG_OPEN\|T_PHP_ECHO_TAG_OPEN\|T_PHP_TAG_CLOSE" src/main/gen/com/github/balotias/intellijantlers/psi/AntlersTypes.java   # >= 6
ls src/main/gen/com/github/balotias/intellijantlers/psi/AntlersPhpTagBlock.java src/main/gen/com/github/balotias/intellijantlers/psi/AntlersPhpEchoTagBlock.java
```

- [ ] **Step 6: Commit**

```bash
git add src/main/grammar/Antlers.bnf src/main/gen
git commit -m "feat(grammar): phpTagBlock/phpEchoTagBlock + <?php/<?=/?> tokens"
```

---

### Task 2: Lexer `PHP_TAG` state + CONTENT catch-all (regenerate lexer, TDD)

**Files:**
- Modify: `src/main/grammar/AntlersLexer.flex`
- Regenerate: `src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt`

- [ ] **Step 1: Write the failing lexer tests**

In `LexerTest.kt`, add these methods (the `types(input)` helper already exists):
```kotlin
    @Test fun phpTagBlockTokens() {
        assertEquals(
            listOf(AntlersTypes.T_PHP_TAG_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_TAG_CLOSE),
            types("<?php echo 1; ?>")
        )
    }

    @Test fun phpEchoTagTokens() {
        assertEquals(
            listOf(AntlersTypes.T_PHP_ECHO_TAG_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_TAG_CLOSE),
            types("<?= \$x ?>")
        )
    }

    @Test fun unclosedPhpTagHasNoClose() {
        val ts = types("<?php \$x = 1;")
        assertEquals(AntlersTypes.T_PHP_TAG_OPEN, ts.first())
        assert(ts.none { it == AntlersTypes.T_PHP_TAG_CLOSE }) { "unclosed tag should have no close: $ts" }
    }

    @Test fun bareProcessingInstructionStaysHtml() {
        val ts = types("<?xml version=\"1.0\"?>")
        assert(ts.all { it == AntlersTypes.T_OUTER_HTML }) { "<?xml must stay outer HTML: $ts" }
    }

    @Test fun normalHtmlAroundTagStillOpens() {
        val ts = types("<div>{{ x }}</div>")
        assert(ts.contains(AntlersTypes.T_LDOUBLE)) { "the {{ tag must still open inside HTML: $ts" }
        assert(ts.none { it == AntlersTypes.T_PHP_TAG_OPEN }) { "no PHP tag in plain HTML: $ts" }
    }
```

- [ ] **Step 2: Run → confirm FAIL**

Run: `./gradlew --rerun-tasks test --tests "*LexerTest"`
Expected: the new tests fail (`<?php` currently lexes as `T_OUTER_HTML`). (They compile because Task 1 added the token constants.)

- [ ] **Step 3: Add the `PHP_TAG` state declaration**

In `AntlersLexer.flex`, after the line `%state PHP_ECHO`, add:
```
%state PHP_TAG
```

- [ ] **Step 4: Add the `<?php`/`<?=` rules and rework the CONTENT catch-all**

In `AntlersLexer.flex`, replace the entire `<CONTENT> { … }` block:
```
<CONTENT> {
  {NOPARSE_OPEN}      { yybegin(NOPARSE); return AntlersTypes.T_NOPARSE_OPEN; }
  "@{{"               { return AntlersTypes.T_OUTER_HTML; }
  "{{#"               { yybegin(COMMENT);  return AntlersTypes.T_COMMENT_OPEN; }
  "{{?"               { yybegin(PHP_RAW);  return AntlersTypes.T_PHP_RAW_OPEN; }
  "{{$"               { yybegin(PHP_ECHO); return AntlersTypes.T_PHP_ECHO_OPEN; }
  "{{"                { yybegin(EXPR);     return AntlersTypes.T_LDOUBLE; }
  [^{@]+              { return AntlersTypes.T_OUTER_HTML; }
  "@"                 { return AntlersTypes.T_OUTER_HTML; }
  "{"                 { return AntlersTypes.T_OUTER_HTML; }
}
```
with:
```
<CONTENT> {
  {NOPARSE_OPEN}            { yybegin(NOPARSE); return AntlersTypes.T_NOPARSE_OPEN; }
  "@{{"                     { return AntlersTypes.T_OUTER_HTML; }
  "{{#"                     { yybegin(COMMENT);  return AntlersTypes.T_COMMENT_OPEN; }
  "{{?"                     { yybegin(PHP_RAW);  return AntlersTypes.T_PHP_RAW_OPEN; }
  "{{$"                     { yybegin(PHP_ECHO); return AntlersTypes.T_PHP_ECHO_OPEN; }
  "{{"                      { yybegin(EXPR);     return AntlersTypes.T_LDOUBLE; }
  "<?php"                   { yybegin(PHP_TAG);  return AntlersTypes.T_PHP_TAG_OPEN; }
  "<?="                     { yybegin(PHP_TAG);  return AntlersTypes.T_PHP_ECHO_TAG_OPEN; }
  ( [^{@<] | "<" [^?{@<] )+ { return AntlersTypes.T_OUTER_HTML; }
  "<"                       { return AntlersTypes.T_OUTER_HTML; }
  "@"                       { return AntlersTypes.T_OUTER_HTML; }
  "{"                       { return AntlersTypes.T_OUTER_HTML; }
}
```

- [ ] **Step 5: Add the `PHP_TAG` state body**

In `AntlersLexer.flex`, immediately after the `<PHP_ECHO> { … }` block, add:
```
<PHP_TAG> {
  "?>"                { yybegin(CONTENT); return AntlersTypes.T_PHP_TAG_CLOSE; }
  [^?]+               { return AntlersTypes.T_PHP_TEXT; }
  "?"                 { return AntlersTypes.T_PHP_TEXT; }
}
```

- [ ] **Step 6: Regenerate the lexer**

```bash
OUT=$(mktemp -d)
JFLEX=~/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps.jflex/jflex/1.9.2/e866267b1b1e983b3c316c350e9fa9a9656606d7/jflex-1.9.2.jar
java -cp "$JFLEX" jflex.Main -d "$OUT" --nobak src/main/grammar/AntlersLexer.flex
cp "$OUT/_AntlersLexer.java" src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java
```

- [ ] **Step 7: Run the lexer tests → PASS**

Run: `./gradlew --rerun-tasks test --tests "*LexerTest"`
Expected: all pass.

- [ ] **Step 8: Run the FULL suite — confirm no existing fixture regressed**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL, gate grep prints nothing. The CONTENT catch-all keeps ordinary HTML as one token, so existing `ParsingTest` goldens should be unchanged. **If** any `AntlersParsingTest` golden fails because token boundaries shifted (only possible if a fixture contains a literal `<?`, `<<`, etc.), inspect the diff, confirm it is the intended tokenization, and refresh that `.txt` by deleting it and re-running so `ParsingTestCase` regenerates it — then re-run to green. Report any fixture you refreshed.

- [ ] **Step 9: Commit**

```bash
git add src/main/grammar/AntlersLexer.flex src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java \
        src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt
git commit -m "feat(lexer): recognize <?php …?> and <?= …?> tags"
```

---

### Task 3: Parser fixture for the new blocks

**Files:**
- Create: `src/test/testData/parsing/PhpTag.antlers.html`, `src/test/testData/parsing/PhpEchoTag.antlers.html`, and their generated `.txt`
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt`

- [ ] **Step 1: Create the fixture inputs**

`src/test/testData/parsing/PhpTag.antlers.html` (exactly, no trailing newline):
```
<div>{{ title }}</div><?php $x = 1; echo $x; ?>
```
`src/test/testData/parsing/PhpEchoTag.antlers.html` (exactly, no trailing newline):
```
<?= $name ?>
```

- [ ] **Step 2: Add the test methods**

In `AntlersParsingTest.kt`, after `fun testPhpEchoBlock() = doTest(true)`, add:
```kotlin
    fun testPhpTag() = doTest(true)
    fun testPhpEchoTag() = doTest(true)
```

- [ ] **Step 3: Run to generate the golden `.txt`**

Run: `./gradlew --rerun-tasks test --tests "*AntlersParsingTest"`
Expected: the two new tests fail on the first run, generating `PhpTag.txt` / `PhpEchoTag.txt` (ParsingTestCase writes the missing golden).

- [ ] **Step 4: Verify the generated goldens**

```bash
grep -n "PhpTagBlock\|PHP_TAG_BLOCK\|PhpBlockBody\|<?php\|?>" src/test/testData/parsing/PhpTag.txt
grep -n "PhpEchoTagBlock\|PHP_ECHO_TAG_BLOCK\|<?=" src/test/testData/parsing/PhpEchoTag.txt
```
Confirm `PhpTag.txt` contains an `AntlersPhpTagBlockImpl(PHP_TAG_BLOCK)` wrapping a `PsiElement(AntlersTokenType.<?php)`, an `AntlersPhpBlockBodyImpl(PHP_BLOCK_BODY)`, and a `PsiElement(AntlersTokenType.?>)`; that the leading `<div>…</div>` is one (or few) `AntlersTokenType.T_OUTER_HTML` leaves with the `{{ title }}` tag parsed normally; and `PhpEchoTag.txt` contains `AntlersPhpEchoTagBlockImpl(PHP_ECHO_TAG_BLOCK)` with `AntlersTokenType.<?=`. If anything is wrong, fix the grammar/lexer (not the golden).

- [ ] **Step 5: Re-run → PASS**

Run: `./gradlew --rerun-tasks test --tests "*AntlersParsingTest"`
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/test/testData/parsing/PhpTag.antlers.html src/test/testData/parsing/PhpTag.txt \
        src/test/testData/parsing/PhpEchoTag.antlers.html src/test/testData/parsing/PhpEchoTag.txt \
        src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt
git commit -m "test(parser): fixtures for <?php ?> and <?= ?> blocks"
```

---

### Task 4: Generalize the injector prefix (TDD)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjector.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjectionTest.kt`

- [ ] **Step 1: Write the failing tests**

In `AntlersPhpInjectionTest.kt`, add:
```kotlin
    fun testTagBlockUsesPhpOpenPrefix() {
        assertEquals("<?php ", AntlersPhpInjector.prefixFor(phpBody("<?php \$x = 1; ?>")))
    }

    fun testEchoTagBlockUsesShortEchoPrefix() {
        assertEquals("<?= ", AntlersPhpInjector.prefixFor(phpBody("<?= \$name ?>")))
    }
```

- [ ] **Step 2: Run → confirm FAIL**

Run: `./gradlew --rerun-tasks test --tests "*AntlersPhpInjectionTest"`
Expected: `testEchoTagBlockUsesShortEchoPrefix` fails (returns `"<?php "` because `prefixFor` only recognizes `AntlersPhpEchoBlock`). `testTagBlockUsesPhpOpenPrefix` already passes (non-echo → default `<?php `).

- [ ] **Step 3: Generalize `prefixFor`**

In `AntlersPhpInjector.kt`, add the import:
```kotlin
import com.github.balotias.intellijantlers.psi.AntlersPhpEchoTagBlock
```
and change `prefixFor`:
```kotlin
        fun prefixFor(body: AntlersPhpBlockBody): String =
            if (body.parent is AntlersPhpEchoBlock) "<?= " else "<?php "
```
to:
```kotlin
        fun prefixFor(body: AntlersPhpBlockBody): String =
            if (body.parent is AntlersPhpEchoBlock || body.parent is AntlersPhpEchoTagBlock) "<?= " else "<?php "
```

- [ ] **Step 4: Run → PASS**

Run: `./gradlew --rerun-tasks test --tests "*AntlersPhpInjectionTest"`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjector.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjectionTest.kt
git commit -m "feat(injection): inject PHP into <?php ?> and <?= ?> tag bodies"
```

---

### Task 5: Highlight the new delimiters (TDD)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighterTest.kt`

- [ ] **Step 1: Write the failing test**

In `AntlersSyntaxHighlighterTest.kt`, add:
```kotlin
    @Test fun phpTagDelimitersAreBraces() {
        for (t in listOf(AntlersTypes.T_PHP_TAG_OPEN, AntlersTypes.T_PHP_ECHO_TAG_OPEN, AntlersTypes.T_PHP_TAG_CLOSE)) {
            assertTrue("$t should be brace-colored",
                hl.getTokenHighlights(t).toList().contains(AntlersSyntaxHighlighter.BRACES))
        }
    }
```

- [ ] **Step 2: Run → confirm FAIL**

Run: `./gradlew --rerun-tasks test --tests "*AntlersSyntaxHighlighterTest"`
Expected: FAIL (new tokens currently map to `EMPTY_KEYS`).

- [ ] **Step 3: Add the tokens to the BRACES branch**

In `AntlersSyntaxHighlighter.kt`, the branch:
```kotlin
            AntlersTypes.T_LDOUBLE, AntlersTypes.T_RDOUBLE,
            AntlersTypes.T_PHP_RAW_OPEN, AntlersTypes.T_PHP_RAW_CLOSE,
            AntlersTypes.T_PHP_ECHO_OPEN, AntlersTypes.T_PHP_ECHO_CLOSE,
            AntlersTypes.T_NOPARSE_OPEN, AntlersTypes.T_NOPARSE_CLOSE -> BRACES_KEYS
```
becomes:
```kotlin
            AntlersTypes.T_LDOUBLE, AntlersTypes.T_RDOUBLE,
            AntlersTypes.T_PHP_RAW_OPEN, AntlersTypes.T_PHP_RAW_CLOSE,
            AntlersTypes.T_PHP_ECHO_OPEN, AntlersTypes.T_PHP_ECHO_CLOSE,
            AntlersTypes.T_PHP_TAG_OPEN, AntlersTypes.T_PHP_ECHO_TAG_OPEN, AntlersTypes.T_PHP_TAG_CLOSE,
            AntlersTypes.T_NOPARSE_OPEN, AntlersTypes.T_NOPARSE_CLOSE -> BRACES_KEYS
```

- [ ] **Step 4: Run → PASS**

Run: `./gradlew --rerun-tasks test --tests "*AntlersSyntaxHighlighterTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighterTest.kt
git commit -m "feat(highlighting): color <?php/<?=/?> delimiters as braces"
```

---

### Task 6: Hardening tests (edge cases + write-back round-trip)

**Files:**
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjectionTest.kt`

These assert existing behavior holds for the new blocks; no production code is expected to change. If one fails, treat it as a real defect and fix the grammar/lexer/injector.

- [ ] **Step 1: Add the hardening tests**

In `AntlersPhpInjectionTest.kt`, add:
```kotlin
    fun testEmptyTagHasNoBody() {
        // `<?php?>` (no inner text) -> no T_PHP_TEXT -> no host -> nothing to inject.
        myFixture.configureByText("p.antlers.html", "<?php?>")
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        assertNull(PsiTreeUtil.findChildOfType(antlers, AntlersPhpBlockBody::class.java))
    }

    fun testUnclosedTagStillHasBody() {
        assertEquals(" \$x = 1;", phpBody("<?php \$x = 1;").text)
    }

    fun testMultiStatementTagBodyIsOneHost() {
        assertEquals(" \$a = 1; \$b = 2; ", phpBody("<?php \$a = 1; \$b = 2; ?>").text)
    }

    fun testWriteBackReplacesTagBody() {
        assertEquals(" \$y = 2; ", updateBody("<?php \$x = 1; ?>", " \$y = 2; "))
    }
```

- [ ] **Step 2: Run → PASS**

Run: `./gradlew --rerun-tasks test --tests "*AntlersPhpInjectionTest"`
Expected: all pass. If `testWriteBackReplacesTagBody` fails because the manipulator's round-trip guard rejects the content, read `AntlersPhpBlockBodyManipulator` and report — the synthetic-block guard must accept a normal `<?php ?>` body the same way it accepts a `{{? ?}}` body.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersPhpInjectionTest.kt
git commit -m "test(injection): harden <?php ?> edge cases and write-back"
```

---

### Task 7: Full-suite gate

**Files:** none (verification only).

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml; echo "exit:$?"`
Expected: prints nothing, `exit:1`.

- [ ] **Step 3: Count check**

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END{print s}'`
Expected: ≈ previous total + ~13 new tests, zero failures/errors.

If green, proceed to whole-feature review and `superpowers:finishing-a-development-branch`.

---

## Notes for the implementer

- **Codegen order matters:** parser (Task 1) before lexer (Task 2) — the lexer references new `AntlersTypes` constants. Always round-trip the unchanged source first.
- **Copy ALL changed gen files** after a parser regen (a rule edit can change `AntlersPhpBlockImpl`, `AntlersVisitor`, element-type holder, etc.), using the temp-dir `diff` to see what changed.
- **Do not hand-edit `src/main/gen`** except by copying regenerated output.
- The CONTENT catch-all `( [^{@<] | "<" [^?{@<] )+` keeps normal HTML as one token; only a literal `<?` boundary (or the rare `<<`/`<{`/`<@`) splits the run. Refresh any affected `.txt` golden by deleting + regenerating, never by hand.
- Run the **full suite with `--rerun-tasks`** and gate on the result XML.
