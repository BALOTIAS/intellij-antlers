# Antlers Front Matter — Real YAML Injection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Inject the real `YAMLLanguage` into a view's `---…---` front matter so comments are colored and the IDE surfaces YAML errors/warnings/completion inside the block.

**Architecture:** Carve the leading front matter out of the lexer into dedicated tokens (so the template-data split excludes it from HTML), make the body a `PsiLanguageInjectionHost` via the grammar, and inject YAML with a `MultiHostInjector`. The text-based `view:` completion/nav/docs are unchanged; the #5 B-visual annotator is retired.

**Tech Stack:** Kotlin, IntelliJ Platform 2025.2, JFlex (lexer regen), Grammar-Kit (parser regen), the bundled YAML plugin (`org.jetbrains.plugins.yaml`), language injection (`MultiHostInjector`, `PsiLanguageInjectionHost`), JUnit / `BasePlatformTestCase` / `ParsingTestCase`.

**Spec:** `docs/superpowers/specs/2026-06-04-antlers-yaml-frontmatter-injection-design.md`
**Branch:** `antlers-yaml-frontmatter-injection` (already created from `main`; spec already committed).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty). `--rerun-tasks` is REQUIRED (Gradle caches).

**Codegen recipes (both verified byte-identical round-trips on 2026-06-04):**
```bash
GK=/Users/balotias/.gradle/caches/modules-2/files-2.1/org.jetbrains/grammar-kit/2022.3.2/6eb13410f38e69b40213d76dd36f1ea80fbbd832/grammar-kit-2022.3.2.jar
IDELIB=/Users/balotias/.gradle/caches/9.5.0/transforms/eec72b1cad4f6953a493ec271def7dbc/transformed/ideaIC-2025.2.6.2/lib
JFLEX=/Users/balotias/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps.jflex/jflex/1.9.2/e866267b1b1e983b3c316c350e9fa9a9656606d7/jflex-1.9.2.jar
# parser:  java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main <out> src/main/grammar/Antlers.bnf
# lexer:   java -cp "$JFLEX" jflex.Main -d <out> --nobak src/main/grammar/AntlersLexer.flex
# (if a path 404s, re-find: find ~/.gradle/caches -type d -path '*ideaIC-2025.2.6.2/lib')
```
**Always round-trip first** (regen the UNCHANGED source to a temp dir → byte-identical committed output) before editing; then regen the edited source to a temp dir and copy only changed files into `src/main/gen/`.

## File Structure

- **Modify** `build.gradle.kts` — add `bundledPlugin("org.jetbrains.plugins.yaml")`.
- **Modify** `src/main/resources/META-INF/plugin.xml` — `<depends>` YAML; register manipulator + injector; drop the B-visual annotator.
- **Modify** `src/main/grammar/Antlers.bnf` — front-matter tokens + rules + host. **Regen** `src/main/gen/**`.
- **Modify** `src/main/grammar/AntlersLexer.flex` — START/CONTENT/FRONTMATTER states. **Regen** `_AntlersLexer.java`.
- **Modify** `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt` — `AntlersFrontMatterBodyMixin`.
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterBodyManipulator.kt`.
- **Create** `src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlInjector.kt`.
- **Modify** `highlighting/AntlersSyntaxHighlighter.kt` + `highlighting/AntlersColorSettingsPage.kt`; **delete** `editor/AntlersFrontMatterHighlightAnnotator.kt` + its test.
- Tests: `AntlersYamlDepTest`, `LexerTest` (add), `AntlersParsingTest` (add) + corpus, `AntlersYamlInjectionTest`, color-settings + highlighter unit checks.

---

### Task 1: Add the YAML plugin dependency

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlDepTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlDepTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.YAMLLanguage

class AntlersYamlDepTest : BasePlatformTestCase() {
    fun testYamlLanguageAvailable() {
        assertNotNull("the bundled YAML plugin is on the test classpath", YAMLLanguage.INSTANCE)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersYamlDepTest"`
Expected: FAIL — compile error (`org.jetbrains.yaml.YAMLLanguage` unresolved; the YAML plugin isn't a dependency yet).

- [ ] **Step 3: Add the dependency**

In `build.gradle.kts`, the `intellijPlatform { … }` dependencies block is:
```kotlin
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
```
Change it to:
```kotlin
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        bundledPlugin("org.jetbrains.plugins.yaml")
        testFramework(TestFrameworkType.Platform)
    }
```

In `src/main/resources/META-INF/plugin.xml`, after the line `<depends>com.intellij.modules.platform</depends>` add:
```xml
    <depends>org.jetbrains.plugins.yaml</depends>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersYamlDepTest"`
Expected: PASS (1 test). If the build can't resolve `org.jetbrains.plugins.yaml`, confirm the bundled-plugin id via `ls ~/.gradle/caches/*/transforms/*/transformed/ideaIC-2025.2.6.2/plugins/yaml` (it exists); do not switch to a Maven coordinate.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlDepTest.kt
git commit -m "$(cat <<'EOF'
build: depend on the bundled YAML plugin (for front-matter injection)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Grammar tokens + front-matter rules + injection host

**Files:**
- Modify: `src/main/grammar/Antlers.bnf`
- Regen: `src/main/gen/**` (parser + new PSI)
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterBodyManipulator.kt`
- Modify: `src/main/resources/META-INF/plugin.xml` (register the manipulator)

> This task is scaffolding: it adds the tokens (so `AntlersTypes.T_FRONTMATTER_*` exists for the lexer in
> Task 3), the `frontMatter`/`frontMatterBody` PSI + host, and the manipulator. There is no behavioral test
> yet (the lexer doesn't emit the tokens until Task 3); verification is **compile success + a sane gen
> diff**. Behavior is covered by Task 3 (corpus) and Task 4 (injection).

- [ ] **Step 1: Round-trip the parser toolchain (gate)**

```bash
rm -rf /tmp/gk0 && mkdir -p /tmp/gk0
java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main /tmp/gk0 src/main/grammar/Antlers.bnf 2>&1 | grep -vi warn | tail -2
diff src/main/gen/com/github/balotias/intellijantlers/parser/AntlersParser.java /tmp/gk0/com/github/balotias/intellijantlers/parser/AntlersParser.java | grep -cE '^[<>]'
```
Expected: `0` (byte-identical). If non-zero and not just a header banner → STOP, the toolchain differs.

- [ ] **Step 2: Edit the grammar**

In `src/main/grammar/Antlers.bnf`, add two tokens to the `tokens = [ … ]` block (next to `T_OUTER_HTML`):
```
    T_FRONTMATTER_FENCE="T_FRONTMATTER_FENCE"
    T_FRONTMATTER_TEXT="T_FRONTMATTER_TEXT"
```
Change the file rule:
```
antlersFile ::= node_*
```
to:
```
antlersFile ::= frontMatter? node_*

frontMatter ::= T_FRONTMATTER_FENCE frontMatterBody? T_FRONTMATTER_FENCE?
frontMatterBody ::= T_FRONTMATTER_TEXT+ {
  mixin="com.github.balotias.intellijantlers.psi.AntlersFrontMatterBodyMixin"
  implements="com.intellij.psi.PsiLanguageInjectionHost"
}
```

- [ ] **Step 3: Regenerate the parser + PSI**

```bash
rm -rf /tmp/gk1 && mkdir -p /tmp/gk1
java -cp "$GK:$IDELIB/*" org.intellij.grammar.Main /tmp/gk1 src/main/grammar/Antlers.bnf 2>&1 | grep -vi warn | tail -2
# Copy every changed/new file under gen/:
rsync -c -r /tmp/gk1/ src/main/gen/   # -c = checksum compare; only differing files are written
git status -s src/main/gen
```
Expected new/changed files: `AntlersTypes.java` (the two tokens), `AntlersParser.java`, new
`psi/AntlersFrontMatter.java`, `psi/AntlersFrontMatterBody.java`, `psi/impl/AntlersFrontMatterImpl.java`,
`psi/impl/AntlersFrontMatterBodyImpl.java`, and `AntlersVisitor.java`. (If `rsync` is unavailable:
`cp -r /tmp/gk1/com src/main/gen/`.)

- [ ] **Step 4: Add the injection-host mixin**

In `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt`, add (and ensure the imports
`com.intellij.lang.ASTNode`, `com.intellij.extapi.psi.ASTWrapperPsiElement` are present — they already are):
```kotlin
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Front-matter body (`T_FRONTMATTER_TEXT+`) as a YAML injection host. The body text is the verbatim YAML
 * between the `---` fences, so the escaper is trivial and the injected range is the whole element.
 */
open class AntlersFrontMatterBodyMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {
    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost =
        ElementManipulators.handleContentChange(this, text) as PsiLanguageInjectionHost

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}
```

- [ ] **Step 5: Add the element manipulator (write-back)**

Create `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterBodyManipulator.kt`:
```kotlin
package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersFileType
import com.github.balotias.intellijantlers.psi.AntlersFrontMatterBody
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil

/**
 * Write-back for edits made inside the injected YAML fragment: rebuild a `frontMatterBody` by parsing a
 * synthetic front matter with the new content and splicing it in. (Direct editing in the host document
 * does not use this; it is for injected-fragment / quick-fix edits.)
 */
class AntlersFrontMatterBodyManipulator : AbstractElementManipulator<AntlersFrontMatterBody>() {
    override fun handleContentChange(
        element: AntlersFrontMatterBody,
        range: TextRange,
        newContent: String
    ): AntlersFrontMatterBody {
        val old = element.text
        val updated = old.substring(0, range.startOffset) + newContent + old.substring(range.endOffset)
        val body = if (updated.endsWith("\n")) updated else "$updated\n"
        val dummy = PsiFileFactory.getInstance(element.project)
            .createFileFromText("_fm.antlers.html", AntlersFileType.INSTANCE, "---\n$body---\n")
        val newBody = PsiTreeUtil.findChildOfType(dummy, AntlersFrontMatterBody::class.java) ?: return element
        return element.replace(newBody) as AntlersFrontMatterBody
    }
}
```

Register it in `plugin.xml` (in the `<extensions defaultExtensionNs="com.intellij">` block):
```xml
        <lang.elementManipulator forClass="com.github.balotias.intellijantlers.psi.AntlersFrontMatterBody"
            implementationClass="com.github.balotias.intellijantlers.editor.AntlersFrontMatterBodyManipulator"/>
```

- [ ] **Step 6: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL. (If `AntlersFrontMatterBodyImpl` fails to compile complaining about
`isValidHost`/`updateText`/`createLiteralTextEscaper`, the mixin in Step 4 is missing/mismatched — fix it,
do not edit the generated impl.)

- [ ] **Step 7: Commit**

```bash
git add src/main/grammar/Antlers.bnf src/main/gen \
        src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterBodyManipulator.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "$(cat <<'EOF'
feat(grammar): front-matter tokens + frontMatterBody injection host

Adds T_FRONTMATTER_FENCE/T_FRONTMATTER_TEXT and frontMatter/frontMatterBody
rules; frontMatterBody is a PsiLanguageInjectionHost with an element
manipulator for write-back. Lexer emits the tokens in the next task.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Lexer carve-out

**Files:**
- Modify: `src/main/grammar/AntlersLexer.flex`
- Regen: `src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt` (add)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt` (add) + `src/test/testData/parsing/FrontMatter.antlers.html`

- [ ] **Step 1: Round-trip the lexer toolchain (gate)**

```bash
rm -rf /tmp/jf0 && mkdir -p /tmp/jf0
java -cp "$JFLEX" jflex.Main -d /tmp/jf0 --nobak src/main/grammar/AntlersLexer.flex 2>&1 | tail -2
diff src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java /tmp/jf0/_AntlersLexer.java | grep -cE '^[<>]'
```
Expected: `0` (byte-identical). If non-zero → STOP.

- [ ] **Step 2: Write the failing lexer tests**

Append to `src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt` (inside the class; the existing
`types(...)` helper is reused):
```kotlin
    @Test fun frontMatterAtStartIsCarvedOut() {
        assertEquals(
            listOf(
                AntlersTypes.T_FRONTMATTER_FENCE,   // "---\n"
                AntlersTypes.T_FRONTMATTER_TEXT,    // "name: ''\n"
                AntlersTypes.T_FRONTMATTER_TEXT,    // "filled: false\n"
                AntlersTypes.T_FRONTMATTER_FENCE,   // "---\n"
                AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS, AntlersTypes.T_IDENT,
                AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE
            ),
            types("---\nname: ''\nfilled: false\n---\n{{ title }}")
        )
    }

    @Test fun noLeadingFenceLexesAsBefore() {
        assertEquals(
            listOf(AntlersTypes.T_OUTER_HTML, AntlersTypes.T_LDOUBLE, AntlersTypes.T_WS,
                AntlersTypes.T_IDENT, AntlersTypes.T_WS, AntlersTypes.T_RDOUBLE),
            types("<div>{{ title }}")
        )
    }

    @Test fun dashesNotAtFileStartAreOuterHtml() {
        // `---` mid-file is plain content, not front matter.
        assertEquals(listOf(AntlersTypes.T_OUTER_HTML), types("x\n---\ny"))
    }
```

- [ ] **Step 3: Run lexer tests to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "*LexerTest"`
Expected: FAIL — `frontMatterAtStartIsCarvedOut` fails (today the whole `---…---` is `T_OUTER_HTML`). The
other two may already pass.

- [ ] **Step 4: Edit the lexer**

In `src/main/grammar/AntlersLexer.flex`:

(a) After the `%state NOPARSE` line, add:
```
%state CONTENT
%state FRONTMATTER
```

(b) In the macro section (after `WS=\s+`), add:
```
NL=\r\n|\n|\r
```

(c) Replace the entire `<YYINITIAL> { … }` block with a START dispatcher **plus** the relocated content
state. That is, change:
```
<YYINITIAL> {
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
to:
```
<YYINITIAL> {
  "---" [ \t]* {NL}   { yybegin(FRONTMATTER); return AntlersTypes.T_FRONTMATTER_FENCE; }
  [^]                 { yybegin(CONTENT); yypushback(1); }
}

<FRONTMATTER> {
  "---" [ \t]* {NL}   { yybegin(CONTENT); return AntlersTypes.T_FRONTMATTER_FENCE; }
  "---" [ \t]*        { yybegin(CONTENT); return AntlersTypes.T_FRONTMATTER_FENCE; }
  [^\r\n]* {NL}       { return AntlersTypes.T_FRONTMATTER_TEXT; }
  [^\r\n]+            { return AntlersTypes.T_FRONTMATTER_TEXT; }
}

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

(d) Change every `yybegin(YYINITIAL)` in the OTHER states to `yybegin(CONTENT)`. There are five:
- `<EXPR>`: `"}}" { yybegin(YYINITIAL); return AntlersTypes.T_RDOUBLE; }` → `yybegin(CONTENT)`
- `<COMMENT>`: `"#}}" { yybegin(YYINITIAL); … }` → `yybegin(CONTENT)`
- `<PHP_RAW>`: `"?}}" { yybegin(YYINITIAL); … }` → `yybegin(CONTENT)`
- `<PHP_ECHO>`: `"$}}" { yybegin(YYINITIAL); … }` → `yybegin(CONTENT)`
- `<NOPARSE>`: `{NOPARSE_CLOSE} { yybegin(YYINITIAL); … }` → `yybegin(CONTENT)`

- [ ] **Step 5: Regenerate the lexer**

```bash
rm -rf /tmp/jf1 && mkdir -p /tmp/jf1
java -cp "$JFLEX" jflex.Main -d /tmp/jf1 --nobak src/main/grammar/AntlersLexer.flex 2>&1 | tail -3
cp /tmp/jf1/_AntlersLexer.java src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java
./gradlew compileKotlin
```
Expected: JFlex prints state/DFA counts with **no errors**; BUILD SUCCESSFUL. If JFlex reports
"Unexpected character" or rule conflicts, fix the `.flex` and re-run (do not hand-edit the generated Java).

- [ ] **Step 6: Run lexer tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "*LexerTest"`
Expected: PASS (all — the 3 new + every existing case; non-front-matter files lex identically because they
enter `CONTENT` immediately via the START dispatcher).

- [ ] **Step 7: Add the parsing corpus case**

Create `src/test/testData/parsing/FrontMatter.antlers.html`:
```
---
name: ''
filled: false
---
{{ title }}
```
Add to `src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt`, after the last
`fun test…() = doTest(true)`:
```kotlin
    fun testFrontMatter() = doTest(true)
```
Run: `./gradlew --rerun-tasks test --tests "*AntlersParsingTest"` — the harness generates
`src/test/testData/parsing/FrontMatter.txt` on first run; re-run to confirm green, and inspect that the
tree shows `AntlersFrontMatterImpl` containing `AntlersFrontMatterBodyImpl` with the two
`T_FRONTMATTER_TEXT` leaves, and **no `PsiErrorElement`**.

- [ ] **Step 8: Commit**

```bash
git add src/main/grammar/AntlersLexer.flex \
        src/main/gen/com/github/balotias/intellijantlers/lexer/_AntlersLexer.java \
        src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt \
        src/test/testData/parsing/FrontMatter.antlers.html src/test/testData/parsing/FrontMatter.txt
git commit -m "$(cat <<'EOF'
feat(lexer): carve leading ---…--- front matter into its own tokens

YYINITIAL is now a one-shot START dispatcher; content rules moved to a
CONTENT state; a FRONTMATTER state emits per-line fence/text tokens. Front
matter is recognized only at offset 0, so it leaves the HTML data stream.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: YAML injector

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlInjector.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlInjectionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlInjectionTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.YAMLLanguage

class AntlersYamlInjectionTest : BasePlatformTestCase() {

    private fun antlersFileFor(text: String) = run {
        myFixture.configureByText("p.antlers.html", text)
        myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
    }

    fun testYamlInjectedIntoFrontMatter() {
        val text = "---\nname: ''\nfilled: false\n---\n{{ title }}"
        val antlers = antlersFileFor(text)
        val ilm = InjectedLanguageManager.getInstance(project)
        val injected = ilm.findInjectedElementAt(antlers, text.indexOf("name"))
        assertNotNull("YAML is injected at the front-matter key", injected)
        assertEquals(YAMLLanguage.INSTANCE, injected!!.containingFile.language)
    }

    fun testNoInjectionOutsideFrontMatter() {
        val text = "---\nname: ''\n---\n{{ title }}"
        val antlers = antlersFileFor(text)
        val ilm = InjectedLanguageManager.getInstance(project)
        assertNull("the body is not YAML", ilm.findInjectedElementAt(antlers, text.indexOf("title")))
    }

    fun testNoInjectionWhenNoFrontMatter() {
        val text = "{{ title }}\nname: ''"
        val antlers = antlersFileFor(text)
        val ilm = InjectedLanguageManager.getInstance(project)
        assertNull("no front matter → no YAML", ilm.findInjectedElementAt(antlers, text.indexOf("name")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersYamlInjectionTest"`
Expected: FAIL — `testYamlInjectedIntoFrontMatter` fails (no injector → `findInjectedElementAt` is null).

- [ ] **Step 3: Write the injector**

Create `src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlInjector.kt`:
```kotlin
package com.github.balotias.intellijantlers.injection

import com.github.balotias.intellijantlers.psi.AntlersFrontMatterBody
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.yaml.YAMLLanguage

/** Injects the real YAML language into a view's `---…---` front-matter body. */
class AntlersYamlInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(AntlersFrontMatterBody::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is AntlersFrontMatterBody) return
        val host = context as PsiLanguageInjectionHost
        if (!host.isValidHost) return
        registrar.startInjecting(YAMLLanguage.INSTANCE)
            .addPlace(null, null, host, TextRange(0, context.textLength))
            .doneInjecting()
    }
}
```

Register in `plugin.xml` (in the `com.intellij` extensions block):
```xml
        <multiHostInjector implementation="com.github.balotias.intellijantlers.injection.AntlersYamlInjector"/>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "*AntlersYamlInjectionTest"`
Expected: PASS (3 tests). If `findInjectedElementAt` is still null, verify the host range is non-empty and
that `frontMatterBody` actually parsed (re-check the Task 3 corpus tree); do NOT weaken the assertion.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlInjector.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/injection/AntlersYamlInjectionTest.kt
git commit -m "$(cat <<'EOF'
feat(injection): inject the YAML language into view front matter

Comments/scalars are now colored by the real YAML highlighter and YAML
errors/warnings/completion appear inside the ---…--- block.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Fence highlight + retire the B-visual annotator

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt`
- Delete: `src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightAnnotator.kt`
- Delete: `src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml` (drop the annotator registration)
- Modify: `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPageTest.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersFrontMatterFenceHighlightTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersFrontMatterFenceHighlightTest.kt`:
```kotlin
package com.github.balotias.intellijantlers.highlighting

import com.github.balotias.intellijantlers.psi.AntlersTypes
import org.junit.Assert.assertTrue
import org.junit.Test

class AntlersFrontMatterFenceHighlightTest {
    @Test fun fenceTokenUsesFenceColor() {
        val keys = AntlersSyntaxHighlighter().getTokenHighlights(AntlersTypes.T_FRONTMATTER_FENCE)
        assertTrue("--- fence is FRONTMATTER_FENCE-colored", keys.contains(AntlersSyntaxHighlighter.FRONTMATTER_FENCE))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "*AntlersFrontMatterFenceHighlightTest"`
Expected: FAIL — `getTokenHighlights(T_FRONTMATTER_FENCE)` returns the empty array today.

- [ ] **Step 3: Color the fence token + drop the dead keys**

In `AntlersSyntaxHighlighter.kt`:
- In the companion, **remove** the `FRONTMATTER_KEY` and `FRONTMATTER_VALUE` vals (added in #5); **keep**
  `FRONTMATTER_FENCE`. Add a keys array beside the others:
```kotlin
        private val FRONTMATTER_FENCE_KEYS = arrayOf(FRONTMATTER_FENCE)
```
- In `getTokenHighlights`, add a branch (next to the other token→key branches):
```kotlin
            AntlersTypes.T_FRONTMATTER_FENCE -> FRONTMATTER_FENCE_KEYS
```

- [ ] **Step 4: Retire the B-visual annotator**

```bash
git rm src/main/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightAnnotator.kt \
       src/test/kotlin/com/github/balotias/intellijantlers/editor/AntlersFrontMatterHighlightTest.kt
```
In `plugin.xml`, delete the line:
```xml
        <annotator language="Antlers" implementationClass="com.github.balotias.intellijantlers.editor.AntlersFrontMatterHighlightAnnotator"/>
```

- [ ] **Step 5: Update the color-settings page + its test**

In `AntlersColorSettingsPage.kt`:
- Remove the `"fmkey"`/`"fmval"` entries from `getAdditionalHighlightingTagToDescriptorMap()` (keep
  `"fmfence"`).
- Remove the `"Front matter//Key"` and `"Front matter//Value"` `AttributesDescriptor`s (keep
  `"Front matter//Fence"`).
- In the `DEMO` string, change the front-matter block so only the fence is tagged (the key/value tags no
  longer exist):
```kotlin
            <fmfence>---</fmfence>
            title: "My Page"
            <fmfence>---</fmfence>
```

In `AntlersColorSettingsPageTest.kt`, update whatever assertions enumerate the exact descriptor/tag sets
so they expect `FRONTMATTER_FENCE` only (no `FRONTMATTER_KEY`/`FRONTMATTER_VALUE`, no `fmkey`/`fmval`).
Run the test to see the exact expected-set diff and align it.

- [ ] **Step 6: Run the affected tests**

Run: `./gradlew --rerun-tasks test --tests "*AntlersFrontMatterFenceHighlightTest" --tests "*AntlersColorSettingsPage*"`
Expected: PASS. (The deleted `AntlersFrontMatterHighlightTest` no longer exists.)

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(highlighting): color --- fence token; retire #5 B-visual annotator

The injected YAML now colors the body (comments included), so the front-matter
overlay annotator and its KEY/VALUE color keys are removed; only the
FRONTMATTER_FENCE token color remains.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Full-suite regression gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on the result XML**

Run: `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml && echo FAIL || echo "GATE CLEAN"`
Expected: `GATE CLEAN`.

- [ ] **Step 3: Confirm the #5 view: features still pass (unchanged, text-based)**

Run: `./gradlew --rerun-tasks test --tests "*ViewFrontMatter*" --tests "*AntlersViewCompletionTest" --tests "*AntlersViewVariableReferenceTest" --tests "*AntlersViewVarDocTest"`
Expected: PASS. (The scanner reads `file.text`, which is unchanged; only the block's tokenization differs.)

If any folding / balance / structure / completion test regressed: the `frontMatter` node is not an
`AntlersStatement`, so those walks must ignore it — investigate before proceeding; do not edit other tests
to go green.

## Self-Review

- **Spec coverage:** YAML dependency (§Components 4) → Task 1; lexer carve-out incl. START/CONTENT/
  FRONTMATTER + unclosed-to-EOF (§Components 1) → Task 3; grammar tokens + `frontMatter`/`frontMatterBody`
  host + manipulator (§Components 2) → Task 2; `MultiHostInjector` (§Components 3) → Task 4; fence color
  (§Components 5) + retire B-visual + drop KEY/VALUE keys (§Components 6) → Task 5; the round-trip gates
  (§De-risking) are Task 2 Step 1 + Task 3 Step 1 (both pre-verified byte-identical during planning); all
  testing rows (lexer / corpus / injection / regression) → Tasks 3/4/6. `view:` features unchanged
  (§Components 7) → Task 6 Step 3. No gaps.
- **Placeholder scan:** none — every code/edit step shows complete content; regen steps give exact commands.
- **Type consistency:** `AntlersFrontMatterBody` (generated, Task 2) is used identically in the mixin,
  manipulator, injector (Task 4), and tests; `AntlersFrontMatterBodyMixin` implements the three
  `PsiLanguageInjectionHost` methods the generated impl needs; `T_FRONTMATTER_FENCE`/`T_FRONTMATTER_TEXT`
  are added to `tokens=[…]` (Task 2) before the lexer (Task 3) and highlighter (Task 5) reference them;
  `YAMLLanguage` resolves via the Task-1 dependency; the injector's `addPlace(null,null,host,TextRange(0,
  len))` matches the host element used in the injection test.
