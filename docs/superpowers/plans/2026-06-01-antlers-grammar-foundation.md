# Antlers Grammar Foundation (Sub-project A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder Antlers lexer/grammar/PSI with a structural parser that recognises the real Statamic 6 Antlers syntax surface (all five delimiter types, tags, parameters, modifiers, conditions, access paths) while staying permissive on deep expressions.

**Architecture:** State-based JFlex lexer feeding a permissive, error-recovering GrammarKit BNF grammar. The parse tree captures structure where the IDE needs it (tag head, parameters, modifier chains, conditions) and treats operator-precedence interiors as a token sequence. The existing HTML template-data split (`T_OUTER_HTML` → HTML parser, `{{ }}` → hidden `ANTLERS_FRAGMENT`) is preserved unchanged.

**Tech Stack:** Kotlin, JetBrains IntelliJ Platform 2025.2, JFlex (via grammarkit `generateLexer`), GrammarKit BNF (`generateParser`), JUnit 4, `BasePlatformTestCase`/`ParsingTestCase`.

**Companion spec:** `docs/superpowers/specs/2026-06-01-antlers-grammar-completion-design.md`

**Note on running tests:** The Claude sandbox cannot fetch the IntelliJ test runtime, so in-sandbox verification uses `./gradlew compileTestKotlin`. The `./gradlew test` commands below run normally in the developer's environment.

---

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `src/main/grammar/AntlersLexer.flex` | Tokenise all delimiter types + expression tokens | Rewrite |
| `src/main/grammar/Antlers.bnf` | Token declarations + structural grammar + PSI mixins | Rewrite |
| `src/main/gen/**` | Generated lexer/parser/PSI | Regenerated |
| `src/main/kotlin/.../psi/AntlersTag.kt` | Mixin: tag name/method/params/closing | Create |
| `src/main/kotlin/.../psi/AntlersVariableEl.kt` | Mixin: variable path + modifiers | Create |
| `src/main/kotlin/.../psi/AntlersPsiImplUtil.kt` | Static accessor implementations for mixins | Create |
| `src/main/kotlin/.../parser/AntlersParserDefinition.kt` | Comment/string token sets, lexer wiring | Modify |
| `src/main/kotlin/.../highlighting/AntlersSyntaxHighlighter.kt` | Map new tokens to colours | Modify |
| `src/main/kotlin/.../template/AntlersFileViewProvider.kt` | Template-data split (keep `T_OUTER_HTML`) | Verify/no-op |
| `src/test/kotlin/.../LexerTest.kt` | Token-sequence assertions | Rewrite |
| `src/test/kotlin/.../AntlersParsingTest.kt` | Golden PSI-tree tests | Create |
| `src/test/testData/parsing/*.antlers.html` + `*.txt` | Parser fixtures + expected trees | Create |
| `src/test/kotlin/.../AntlersCompletionTest.kt` | HTML-split regression (already exists) | Keep/verify |

**Token name stability:** `T_OUTER_HTML` keeps its exact name so `AntlersFileViewProvider.ANTLERS_TEMPLATE_DATA` (third arg) and the HTML/CSS-completion fix from the prior session are not disturbed.

---

## Task 1: Rewrite the lexer

**Files:**
- Modify: `src/main/grammar/AntlersLexer.flex`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt`

- [ ] **Step 1: Write the failing lexer tests**

Replace the contents of `src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt`:

```kotlin
package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class LexerTest {

    private fun lex(input: String): List<Pair<IElementType, String>> {
        val lexer = _AntlersLexer(StringReader(input))
        val out = mutableListOf<Pair<IElementType, String>>()
        var t = lexer.advance()
        while (t != null) {
            out.add(t to lexer.yytext().toString())
            t = lexer.advance()
        }
        return out
    }

    private fun types(input: String) = lex(input).map { it.first }

    @Test
    fun outerHtmlAndTag() {
        assertEquals(
            listOf(
                AntlersTypes.T_OUTER_HTML,   // "Hi "
                AntlersTypes.T_LDOUBLE,      // "{{"
                AntlersTypes.T_WS,
                AntlersTypes.T_IDENT,        // "title"
                AntlersTypes.T_WS,
                AntlersTypes.T_RDOUBLE       // "}}"
            ),
            types("Hi {{ title }}")
        )
    }

    @Test
    fun escapedDelimiterIsOuterHtml() {
        // @{{ must NOT open an expression
        val ts = types("@{{ title }}")
        assert(ts.none { it == AntlersTypes.T_LDOUBLE }) { "escaped @{{ opened an expression: $ts" }
        assertEquals(AntlersTypes.T_OUTER_HTML, ts.first())
    }

    @Test
    fun comment() {
        assertEquals(
            listOf(AntlersTypes.T_COMMENT_OPEN, AntlersTypes.T_COMMENT_TEXT, AntlersTypes.T_COMMENT_CLOSE),
            types("{{# hidden #}}")
        )
    }

    @Test
    fun phpRawAndEcho() {
        assertEquals(
            listOf(AntlersTypes.T_PHP_RAW_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_RAW_CLOSE),
            types("{{? \$x = 1; ?}}")
        )
        assertEquals(
            listOf(AntlersTypes.T_PHP_ECHO_OPEN, AntlersTypes.T_PHP_TEXT, AntlersTypes.T_PHP_ECHO_CLOSE),
            types("{{\$ \$x \$}}")
        )
    }

    @Test
    fun noparseIsRaw() {
        val ts = types("{{ noparse }}{{ title }}{{ /noparse }}")
        assertEquals(AntlersTypes.T_NOPARSE_OPEN, ts.first())
        assertEquals(AntlersTypes.T_NOPARSE_CLOSE, ts.last())
        assert(ts.none { it == AntlersTypes.T_IDENT }) { "noparse body was tokenised: $ts" }
    }

    @Test
    fun modifierPipeAndOperators() {
        val ts = types("{{ a | upper }}{{ if x == 1 }}")
        assert(ts.contains(AntlersTypes.T_PIPE))
        assert(ts.contains(AntlersTypes.T_OP))
    }

    @Test
    fun accessAndParams() {
        val ts = types("{{ collection:blog limit=\"5\" :sort=\"order\" }}")
        assert(ts.contains(AntlersTypes.T_COLON))
        assert(ts.contains(AntlersTypes.T_EQUALS))
        assert(ts.contains(AntlersTypes.T_STRING))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.LexerTest"`
Expected: FAIL (compile error — `T_LDOUBLE`, `T_OP`, etc. don't exist yet, and the old lexer emits old tokens).

- [ ] **Step 3: Rewrite the lexer**

Replace the entire contents of `src/main/grammar/AntlersLexer.flex`:

```flex
package com.github.balotias.intellijantlers.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.github.balotias.intellijantlers.psi.AntlersTypes;
import com.intellij.psi.TokenType;

%%

%class _AntlersLexer
%public
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

%{
  public _AntlersLexer() {
    this((java.io.Reader)null);
  }
%}

%state EXPR
%state COMMENT
%state PHP_RAW
%state PHP_ECHO
%state NOPARSE

WS=\s+
IDENT=[a-zA-Z_][a-zA-Z_0-9\-]*
NUMBER=[0-9]+(\.[0-9]+)?
STRING=\"([^\"\\]|\\.)*\"|'([^'\\]|\\.)*'
NOPARSE_OPEN="{{"[ \t]*"noparse"[ \t]*"}}"
NOPARSE_CLOSE="{{"[ \t]*"/noparse"[ \t]*"}}"
OP="==="|"!=="|"<=>"|"=="|"!="|">="|"<="|">"|"<"|"&&"|"||"|"!"|"???"|"??"|"?="|"?"|"+="|"-="|"*="|"/="|"%="|"**"|"+"|"-"|"*"|"%"

%%

<YYINITIAL> {
  {NOPARSE_OPEN}      { yybegin(NOPARSE); return AntlersTypes.T_NOPARSE_OPEN; }
  "@{{"               { return AntlersTypes.T_OUTER_HTML; }   // escaped delimiter -> literal
  "{{#"               { yybegin(COMMENT);  return AntlersTypes.T_COMMENT_OPEN; }
  "{{?"               { yybegin(PHP_RAW);  return AntlersTypes.T_PHP_RAW_OPEN; }
  "{{$"               { yybegin(PHP_ECHO); return AntlersTypes.T_PHP_ECHO_OPEN; }
  "{{"                { yybegin(EXPR);     return AntlersTypes.T_LDOUBLE; }
  [^{@]+              { return AntlersTypes.T_OUTER_HTML; }
  "@"                 { return AntlersTypes.T_OUTER_HTML; }
  "{"                 { return AntlersTypes.T_OUTER_HTML; }
}

<EXPR> {
  "}}"                { yybegin(YYINITIAL); return AntlersTypes.T_RDOUBLE; }
  {WS}                { return AntlersTypes.T_WS; }
  {STRING}            { return AntlersTypes.T_STRING; }
  {NUMBER}            { return AntlersTypes.T_NUMBER; }
  "=>"                { return AntlersTypes.T_ARROW; }
  {OP}                { return AntlersTypes.T_OP; }
  "|"                 { return AntlersTypes.T_PIPE; }
  "/"                 { return AntlersTypes.T_SLASH; }
  ":"                 { return AntlersTypes.T_COLON; }
  "."                 { return AntlersTypes.T_DOT; }
  ","                 { return AntlersTypes.T_COMMA; }
  ";"                 { return AntlersTypes.T_SEMICOLON; }
  "="                 { return AntlersTypes.T_EQUALS; }
  "("                 { return AntlersTypes.T_LPAREN; }
  ")"                 { return AntlersTypes.T_RPAREN; }
  "["                 { return AntlersTypes.T_LBRACKET; }
  "]"                 { return AntlersTypes.T_RBRACKET; }
  "{"                 { return AntlersTypes.T_LBRACE; }
  "}"                 { return AntlersTypes.T_RBRACE; }
  "@"                 { return AntlersTypes.T_AT; }
  "$"                 { return AntlersTypes.T_DOLLAR; }
  {IDENT}             { return AntlersTypes.T_IDENT; }
  .                   { return TokenType.BAD_CHARACTER; }
}

<COMMENT> {
  "#}}"               { yybegin(YYINITIAL); return AntlersTypes.T_COMMENT_CLOSE; }
  [^#]+               { return AntlersTypes.T_COMMENT_TEXT; }
  "#"                 { return AntlersTypes.T_COMMENT_TEXT; }
}

<PHP_RAW> {
  "?}}"               { yybegin(YYINITIAL); return AntlersTypes.T_PHP_RAW_CLOSE; }
  [^?]+               { return AntlersTypes.T_PHP_TEXT; }
  "?"                 { return AntlersTypes.T_PHP_TEXT; }
}

<PHP_ECHO> {
  "$}}"               { yybegin(YYINITIAL); return AntlersTypes.T_PHP_ECHO_CLOSE; }
  [^$]+               { return AntlersTypes.T_PHP_TEXT; }
  "$"                 { return AntlersTypes.T_PHP_TEXT; }
}

<NOPARSE> {
  {NOPARSE_CLOSE}     { yybegin(YYINITIAL); return AntlersTypes.T_NOPARSE_CLOSE; }
  [^{]+               { return AntlersTypes.T_NOPARSE_TEXT; }
  "{"                 { return AntlersTypes.T_NOPARSE_TEXT; }
}
```

- [ ] **Step 4: Declare the tokens in the BNF so they generate**

The lexer references `AntlersTypes.*` constants generated from the BNF `tokens` block. Update `src/main/grammar/Antlers.bnf` `tokens = [ ... ]` to contain exactly (full BNF is rewritten in Task 2, but the token list must exist for the lexer to compile):

```
  tokens = [
    T_OUTER_HTML="T_OUTER_HTML"
    T_LDOUBLE="{{"
    T_RDOUBLE="}}"
    T_COMMENT_OPEN="{{#"
    T_COMMENT_CLOSE="#}}"
    T_COMMENT_TEXT="T_COMMENT_TEXT"
    T_PHP_RAW_OPEN="{{?"
    T_PHP_RAW_CLOSE="?}}"
    T_PHP_ECHO_OPEN="{{$"
    T_PHP_ECHO_CLOSE="$}}"
    T_PHP_TEXT="T_PHP_TEXT"
    T_NOPARSE_OPEN="T_NOPARSE_OPEN"
    T_NOPARSE_CLOSE="T_NOPARSE_CLOSE"
    T_NOPARSE_TEXT="T_NOPARSE_TEXT"
    T_IDENT="T_IDENT"
    T_STRING="T_STRING"
    T_NUMBER="T_NUMBER"
    T_OP="T_OP"
    T_PIPE="|"
    T_SLASH="/"
    T_COLON=":"
    T_DOT="."
    T_COMMA=","
    T_SEMICOLON=";"
    T_EQUALS="="
    T_ARROW="=>"
    T_LPAREN="("
    T_RPAREN=")"
    T_LBRACKET="["
    T_RBRACKET="]"
    T_LBRACE="{"
    T_RBRACE="}"
    T_AT="@"
    T_DOLLAR="$"
    T_WS="T_WS"
  ]
```

- [ ] **Step 5: Regenerate the lexer**

Run: `./gradlew generateParser generateLexer`
Expected: BUILD SUCCESSFUL; `src/main/gen/.../lexer/_AntlersLexer.java` and `.../psi/AntlersTypes.java` regenerated with the new token constants.

- [ ] **Step 6: Run the lexer tests**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.LexerTest"`
Expected: PASS (all 7 tests). In the sandbox, run `./gradlew compileTestKotlin` instead and confirm BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/grammar/AntlersLexer.flex src/main/grammar/Antlers.bnf src/main/gen src/test/kotlin/com/github/balotias/intellijantlers/LexerTest.kt
git commit -m "Rewrite Antlers lexer: all delimiter types, escape, noparse"
```

---

## Task 2: Rewrite the grammar (structural BNF)

**Files:**
- Modify: `src/main/grammar/Antlers.bnf`
- Regenerated: `src/main/gen/**`

- [ ] **Step 1: Replace the grammar body**

In `src/main/grammar/Antlers.bnf`, keep the header `{ ... }` block (parser class, psi packages, the `tokens` list from Task 1) and add these attributes to the header block, then replace all rules after it:

Header attributes to ensure are present inside the top `{ }`:

```
  psiImplUtilClass="com.github.balotias.intellijantlers.psi.AntlersPsiImplUtil"
  parserUtilClass="com.intellij.lang.parser.GeneratedParserUtilBase"
```

Rules (replace everything from `antlersFile ::=` to end of file):

```
antlersFile ::= node_*

private node_ ::= comment | phpBlock | noparseBlock | statement | outerHtml

outerHtml ::= T_OUTER_HTML

comment ::= T_COMMENT_OPEN T_COMMENT_TEXT? T_COMMENT_CLOSE { pin=1 }

phpBlock ::= (T_PHP_RAW_OPEN T_PHP_TEXT? T_PHP_RAW_CLOSE)
           | (T_PHP_ECHO_OPEN T_PHP_TEXT? T_PHP_ECHO_CLOSE)

noparseBlock ::= T_NOPARSE_OPEN T_NOPARSE_TEXT? T_NOPARSE_CLOSE

statement ::= T_LDOUBLE body? T_RDOUBLE {
  pin=1
  recoverWhile=statement_recover
}
private statement_recover ::= !(T_RDOUBLE | T_LDOUBLE | T_OUTER_HTML | T_COMMENT_OPEN)

private body ::= closingTag | condition | expr_

closingTag ::= T_SLASH namePath? {
  mixin="com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin"
}

condition ::= conditionKeyword exprToken_* {
  mixin="com.github.balotias.intellijantlers.psi.AntlersConditionMixin"
}
private conditionKeyword ::= T_IDENT  // if|elseif|else|unless|endif|endunless, validated in mixin

private expr_ ::= namePath tail_*

tail_ ::= parameter | modifier | exprToken_

namePath ::= pathSegment ((T_COLON | T_DOT) pathSegment | bracketAccess)* {
  mixin="com.github.balotias.intellijantlers.psi.AntlersNamePathMixin"
}
private pathSegment ::= T_DOLLAR? (T_IDENT | T_NUMBER)
bracketAccess ::= T_LBRACKET exprToken_* T_RBRACKET

parameter ::= T_COLON? T_DOLLAR? T_IDENT (T_EQUALS paramValue)? {
  mixin="com.github.balotias.intellijantlers.psi.AntlersParameterMixin"
}
private paramValue ::= T_STRING | (T_LBRACE exprToken_* T_RBRACE) | T_NUMBER | namePath

modifier ::= T_PIPE T_IDENT (T_LPAREN argList? T_RPAREN)? {
  mixin="com.github.balotias.intellijantlers.psi.AntlersModifierMixin"
}
private argList ::= exprToken_ (T_COMMA exprToken_)*

// Permissive operator/expression soup: anything that is not a parameter,
// modifier, or a delimiter that ends the statement.
private exprToken_ ::=
    T_OP | T_STRING | T_NUMBER | T_ARROW | T_DOT | T_COLON | T_COMMA | T_SEMICOLON
  | T_EQUALS | T_DOLLAR | T_AT | T_LPAREN | T_RPAREN | T_LBRACKET | T_RBRACKET
  | T_LBRACE | T_RBRACE | T_IDENT | T_SLASH
```

Notes for the implementer:
- `tail_` ordering puts `parameter` and `modifier` before the generic `exprToken_`, so `limit="5"` parses as a parameter and `| upper` as a modifier; operator expressions fall through to the soup.
- `private` rules don't get their own PSI node; only `outerHtml`, `comment`, `phpBlock`, `noparseBlock`, `statement`, `closingTag`, `condition`, `namePath`, `bracketAccess`, `parameter`, `modifier` become nodes.

- [ ] **Step 2: Create empty mixin placeholders so the BNF can reference them**

The BNF references mixin classes that must exist before `generateParser`'s output compiles. Create them in Task 3; for now create minimal stubs (Task 3 fills the bodies). Create `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt`:

```kotlin
package com.github.balotias.intellijantlers.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

open class AntlersClosingTagMixin(node: ASTNode) : ASTWrapperPsiElement(node)
open class AntlersConditionMixin(node: ASTNode) : ASTWrapperPsiElement(node)
open class AntlersNamePathMixin(node: ASTNode) : ASTWrapperPsiElement(node)
open class AntlersParameterMixin(node: ASTNode) : ASTWrapperPsiElement(node)
open class AntlersModifierMixin(node: ASTNode) : ASTWrapperPsiElement(node)
```

- [ ] **Step 3: Regenerate the parser & PSI**

Run: `./gradlew generateParser generateLexer`
Expected: BUILD SUCCESSFUL; `src/main/gen/.../parser/AntlersParser.java`, `.../psi/AntlersTypes.java`, and `.../psi/*` regenerated. The generated `AntlersTypes.Factory` references the new element types.

- [ ] **Step 4: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/grammar/Antlers.bnf src/main/gen src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt
git commit -m "Rewrite Antlers grammar: structural nodes for tags, params, modifiers, conditions"
```

---

## Task 3: PSI accessors (mixin bodies + util)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersPsiUtil.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/AntlersPsiTest.kt`

- [ ] **Step 1: Write the failing PSI accessor test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/AntlersPsiTest.kt`:

```kotlin
package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPsiTest : BasePlatformTestCase() {

    fun testParameterAccessors() {
        val file = myFixture.configureByText("t.antlers.html", "{{ collection:blog limit=\"5\" :sort=\"order\" }}")
        val params = PsiTreeUtil.findChildrenOfType(file, AntlersParameterMixin::class.java).toList()
        assertEquals(2, params.size)
        assertEquals("limit", params[0].parameterName)
        assertFalse(params[0].isBound)
        assertEquals("sort", params[1].parameterName)
        assertTrue(params[1].isBound)
    }

    fun testModifierAccessor() {
        val file = myFixture.configureByText("t.antlers.html", "{{ title | upper | truncate(20) }}")
        val mods = PsiTreeUtil.findChildrenOfType(file, AntlersModifierMixin::class.java).toList()
        assertEquals(listOf("upper", "truncate"), mods.map { it.modifierName })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.AntlersPsiTest"`
Expected: FAIL (`parameterName`, `isBound`, `modifierName` unresolved).

- [ ] **Step 3: Implement the accessors**

Replace `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt`:

```kotlin
package com.github.balotias.intellijantlers.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

open class AntlersClosingTagMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** The closed tag's name path text, e.g. "collection:blog" (null if absent). */
    val closedName: String?
        get() = PsiTreeUtil.findChildOfType(this, AntlersNamePathMixin::class.java)?.pathText
}

open class AntlersConditionMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** if | elseif | else | unless | endif | endunless */
    val keyword: String
        get() = node.findChildByType(AntlersTypes.T_IDENT)?.text ?: ""
}

open class AntlersNamePathMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** First identifier segment, e.g. "collection" in "collection:blog". */
    val head: String
        get() = node.findChildByType(AntlersTypes.T_IDENT)?.text ?: ""

    /** Method segment after the first colon, e.g. "blog" in "collection:blog" (null if none). */
    val method: String?
        get() {
            val idents = node.getChildren(null)
                .filter { it.elementType == AntlersTypes.T_IDENT }
            return if (idents.size >= 2) idents[1].text else null
        }

    /** Whole path text, e.g. "collection:blog". */
    val pathText: String get() = text
}

open class AntlersParameterMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** Parameter name without the leading ':' or '$'. */
    val parameterName: String
        get() = node.findChildByType(AntlersTypes.T_IDENT)?.text ?: ""

    /** True for bound parameters written as ':name' or ':$name'. */
    val isBound: Boolean
        get() = node.findChildByType(AntlersTypes.T_COLON) != null

    /** The value PSI (string/number/namePath/braced expr), or null for a bare flag. */
    val valueElement: PsiElement?
        get() = node.findChildByType(AntlersTypes.T_EQUALS)?.psi?.nextSibling
            ?.let { skipWhitespace(it) }

    private fun skipWhitespace(start: PsiElement): PsiElement? {
        var e: PsiElement? = start
        while (e != null && e.node.elementType == AntlersTypes.T_WS) e = e.nextSibling
        return e
    }
}

open class AntlersModifierMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** Modifier name after the pipe, e.g. "upper". */
    val modifierName: String
        get() = node.findChildByType(AntlersTypes.T_IDENT)?.text ?: ""
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.AntlersPsiTest"`
Expected: PASS (2 tests). Sandbox: `./gradlew compileTestKotlin` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt src/test/kotlin/com/github/balotias/intellijantlers/AntlersPsiTest.kt
git commit -m "Add Antlers PSI accessors for params, modifiers, name paths, conditions"
```

---

## Task 4: Update ParserDefinition (comment & string token sets)

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/parser/AntlersParserDefinition.kt`

- [ ] **Step 1: Update the token sets to the new names**

In `AntlersParserDefinition.kt`, replace the `getCommentTokens`, `getWhitespaceTokens`, and `getStringLiteralElements` methods:

```kotlin
    override fun getCommentTokens(): TokenSet = TokenSet.create(
        AntlersTypes.T_COMMENT_OPEN,
        AntlersTypes.T_COMMENT_TEXT,
        AntlersTypes.T_COMMENT_CLOSE
    )

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(
        com.intellij.psi.TokenType.WHITE_SPACE,
        AntlersTypes.T_WS
    )

    override fun getStringLiteralElements(): TokenSet = TokenSet.create(AntlersTypes.T_STRING)
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (old token names `T_OPEN_BRACE`/`T_IDENTIFIER` no longer referenced anywhere).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/parser/AntlersParserDefinition.kt
git commit -m "Update AntlersParserDefinition token sets for new grammar"
```

---

## Task 5: Update the syntax highlighter

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt`

- [ ] **Step 1: Map the new tokens to colours**

Replace the `companion object` keys and `getTokenHighlights` in `AntlersSyntaxHighlighter.kt`:

```kotlin
    companion object {
        val BRACES = TextAttributesKey.createTextAttributesKey("ANTLERS_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val IDENTIFIER = TextAttributesKey.createTextAttributesKey("ANTLERS_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val STRING = TextAttributesKey.createTextAttributesKey("ANTLERS_STRING", DefaultLanguageHighlighterColors.STRING)
        val NUMBER = TextAttributesKey.createTextAttributesKey("ANTLERS_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val COMMENT = TextAttributesKey.createTextAttributesKey("ANTLERS_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        val OPERATOR = TextAttributesKey.createTextAttributesKey("ANTLERS_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val KEYWORD = TextAttributesKey.createTextAttributesKey("ANTLERS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)

        private val BRACES_KEYS = arrayOf(BRACES)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val STRING_KEYS = arrayOf(STRING)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val OPERATOR_KEYS = arrayOf(OPERATOR)
        private val EMPTY_KEYS = arrayOf<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = FlexAdapter(_AntlersLexer(null))

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            AntlersTypes.T_LDOUBLE, AntlersTypes.T_RDOUBLE,
            AntlersTypes.T_PHP_RAW_OPEN, AntlersTypes.T_PHP_RAW_CLOSE,
            AntlersTypes.T_PHP_ECHO_OPEN, AntlersTypes.T_PHP_ECHO_CLOSE,
            AntlersTypes.T_NOPARSE_OPEN, AntlersTypes.T_NOPARSE_CLOSE -> BRACES_KEYS

            AntlersTypes.T_IDENT, AntlersTypes.T_DOLLAR -> IDENTIFIER_KEYS
            AntlersTypes.T_STRING -> STRING_KEYS
            AntlersTypes.T_NUMBER -> NUMBER_KEYS

            AntlersTypes.T_COMMENT_OPEN, AntlersTypes.T_COMMENT_CLOSE, AntlersTypes.T_COMMENT_TEXT -> COMMENT_KEYS

            AntlersTypes.T_OP, AntlersTypes.T_PIPE, AntlersTypes.T_EQUALS, AntlersTypes.T_ARROW,
            AntlersTypes.T_COLON, AntlersTypes.T_SLASH, AntlersTypes.T_DOT -> OPERATOR_KEYS

            else -> EMPTY_KEYS
        }
    }
```

(The `import` for `_AntlersLexer`, `AntlersTypes`, `FlexAdapter`, `Lexer`, `DefaultLanguageHighlighterColors`, `TextAttributesKey`, `SyntaxHighlighterBase`, `IElementType` are already present at the top of the file; keep them.)

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersSyntaxHighlighter.kt
git commit -m "Update Antlers syntax highlighter for new token set"
```

---

## Task 6: Regression — HTML/CSS completion still works

**Files:**
- Verify: `src/main/kotlin/com/github/balotias/intellijantlers/template/AntlersFileViewProvider.kt` (expect no change)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/AntlersCompletionTest.kt` (existing)

- [ ] **Step 1: Confirm the template-data type still references `T_OUTER_HTML`**

Read `AntlersFileViewProvider.kt`. `ANTLERS_TEMPLATE_DATA`'s third argument must still be `AntlersTypes.T_OUTER_HTML` and the fourth `ANTLERS_FRAGMENT`. The lexer still emits `T_OUTER_HTML` for outer content, so no change is expected. If `T_OUTER_HTML` was accidentally renamed, restore the name in the BNF `tokens` list.

- [ ] **Step 2: Run the HTML regression test**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.AntlersCompletionTest.testHtmlTagCompletionWorks"`
Expected: PASS — `<d<caret>` still offers `div`. (Sandbox: `./gradlew compileTestKotlin`.)

This test will be expanded in Plan 2; here it only guards that the lexer rewrite didn't break the HTML split.

- [ ] **Step 3: Commit (only if a fix was needed)**

```bash
git add -A
git commit -m "Keep T_OUTER_HTML contract intact after lexer rewrite"
```

---

## Task 7: Golden parser tests

**Files:**
- Create: `src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt`
- Create: `src/test/testData/parsing/tag.antlers.html`, `tag.txt`, `modifiers.antlers.html`, `modifiers.txt`, `condition.antlers.html`, `condition.txt`, `mixed.antlers.html`, `mixed.txt`

- [ ] **Step 1: Create the parsing test harness**

Create `src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt`:

```kotlin
package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.parser.AntlersParserDefinition
import com.intellij.testFramework.ParsingTestCase

class AntlersParsingTest : ParsingTestCase("parsing", "antlers.html", AntlersParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData"
    override fun skipSpaces(): Boolean = false
    override fun includeRanges(): Boolean = true

    fun testTag() = doTest(true)
    fun testModifiers() = doTest(true)
    fun testCondition() = doTest(true)
    fun testMixed() = doTest(true)
}
```

- [ ] **Step 2: Create the input fixtures**

Create `src/test/testData/parsing/tag.antlers.html`:

```
{{ collection:blog limit="5" :sort="order" as="posts" }}{{ /collection:blog }}
```

Create `src/test/testData/parsing/modifiers.antlers.html`:

```
{{ title | upper | truncate(20) }}
```

Create `src/test/testData/parsing/condition.antlers.html`:

```
{{ if status == 'published' && featured }}yes{{ /if }}
```

Create `src/test/testData/parsing/mixed.antlers.html`:

```
<div>{{# note #}}{{ noparse }}{{ x }}{{ /noparse }}{{ user:name }}</div>
```

- [ ] **Step 3: Generate the expected trees**

Leave the four `*.txt` files **empty** initially, then run the tests once. `ParsingTestCase` writes the actual tree to the console on first failure; copy each printed tree into the matching `.txt` file. (Alternatively set the env var to auto-create — but manual copy is reliable.)

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.AntlersParsingTest"`
Expected (first run): FAIL, printing the actual PSI trees.

- [ ] **Step 4: Review each printed tree for correctness, then save it**

For each test, confirm the tree matches the structural design before pasting it into the `.txt` file:
- `tag.txt`: a `statement` containing a `namePath` (`collection:blog`), two `parameter` nodes (`limit`, bound `:sort`, plus `as`), then a separate `statement` with a `closingTag`.
- `modifiers.txt`: a `statement` with `namePath` (`title`) and two `modifier` nodes (`upper`, `truncate` with an argList).
- `condition.txt`: a `statement` whose body is a `condition` (keyword `if`) followed by expr tokens; outer text `yes`; closing `statement`.
- `mixed.txt`: `outerHtml`, `comment`, `noparseBlock` (raw `{{ x }}` not tokenised), `statement` (`user:name`), `outerHtml`.

If a tree is wrong (e.g. a parameter parsed as expr soup), fix the BNF in Task 2, regenerate, and re-run before saving the golden file.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.AntlersParsingTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add src/test/kotlin/com/github/balotias/intellijantlers/AntlersParsingTest.kt src/test/testData/parsing
git commit -m "Add golden parser tests for Antlers structural grammar"
```

---

## Task 8: Full build & final verification

- [ ] **Step 1: Full compile**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full test suite (developer environment)**

Run: `./gradlew test`
Expected: PASS — `LexerTest`, `AntlersPsiTest`, `AntlersParsingTest`, and the existing `AntlersCompletionTest` HTML regression all green.

- [ ] **Step 3: Manual smoke test (optional, developer environment)**

Run: `./gradlew runIde`, open a `.antlers.html` file with tags, comments, conditions, and `noparse`. Confirm highlighting distinguishes braces / identifiers / strings / operators / comments, and that plain HTML around `{{ }}` still highlights and completes.

- [ ] **Step 4: Commit any final touch-ups**

```bash
git add -A
git commit -m "Antlers grammar foundation complete (sub-project A)"
```

---

## Self-Review (against the spec)

**Spec coverage (§3 Sub-project A):**
- §3.1 lexer states (EXPR/COMMENT/PHP/NOPARSE + `@{{` escape) → Task 1 ✓
- §3.1 expression tokens + keywords-as-IDENT → Task 1 (`T_OP` soup, `T_IDENT` keywords) ✓
- §3.1 `T_OUTER_HTML` non-collision constraint → Task 1 + Task 6 regression ✓
- §3.2 structural BNF (statement/closingTag/condition/namePath/parameter/modifier) → Task 2 ✓
- §3.3 PSI accessors (tag name/method, parameter bound/value, modifier name, condition keyword) → Task 3 ✓
- §6 testing (lexer asserts, ParsingTestCase golden, HTML regression) → Tasks 1, 6, 7 ✓
- §2.5 preserve template-data split → Task 6 ✓

**Deferred to Plan 2 (completion):** catalog, the four completion providers, `AntlersCompletionContributor` rewrite, completion tests. Not in this plan by design.

**Placeholder scan:** No TBD/TODO; every code step shows complete file/section content. The one manual step (Task 7 Step 3, pasting generated golden trees) is inherent to `ParsingTestCase` and includes the verification criteria to check before saving.

**Type consistency:** Accessor names used in tests match the mixins — `parameterName`/`isBound`/`valueElement` (`AntlersParameterMixin`), `modifierName` (`AntlersModifierMixin`), `head`/`method`/`pathText` (`AntlersNamePathMixin`), `keyword` (`AntlersConditionMixin`), `closedName` (`AntlersClosingTagMixin`). Token names match between the `.flex` returns and the BNF `tokens` block.
