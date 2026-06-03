# Antlers Test-Coverage Hardening Plan

> **For agentic workers:** Use superpowers:subagent-driven-development. Test-only additions; each task creates/extends test files and gates on the full suite. **No production code changes** — if a test reveals a real BUG (not just a coverage gap), STOP and report rather than asserting the buggy behavior.

**Goal:** Close the coverage gaps from the 2026-06-03 audit: the Critical dynamic-catalog-scan hole and three Important gaps (parser corpus, commenter/brace behavior, inspection false-positives).

**Test gate (after every run):** `./gradlew --rerun-tasks test` then
`grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` — must print nothing.

**Branch:** `antlers-test-coverage` (off `main`).

**Key facts:**
- `TagScanner.scan(project)` scans PHP files whose path contains `/Tags/`, matching
  `class X extends (\Statamic\Tags\)?Tags`, returns `camelToSnake(X)`. `ModifierScanner` is the same for
  `/Modifiers/` + `extends (\Statamic\Modifiers\)?Modifier`. `camelToSnake`, `scanDir`, `findInDir` are
  `internal` (callable from in-module tests). `find()` returns `NavTarget(file, classNameOffset)`.
- `AntlersCatalogService.tags()/modifiers()` merge bundled (version-filtered) + scanned customs (custom =
  scanned names not already bundled, as bare `TagDef`/`ModifierDef` with description "Custom tag"/"Custom
  modifier").
- Parser tests use `ParsingTestCase("parsing", "antlers.html", AntlersParserDefinition())` with
  `src/test/testData/parsing/<Name>.antlers.html` + a generated `<Name>.txt` PSI dump; `doTest(true)`
  compares the tree (does NOT fail on `PsiErrorElement`s, so error-recovery cases are fine).
- `AntlersCommenter` is block-only `{{# … #}}` (no line comment). The existing `AntlersEditorBasicsTest`
  notes platform comment-routing is unreliable in a template-language context.
- `AntlersUnknownModifierInspection` (shortName `AntlersUnknownModifier`) registers a WEAK_WARNING on an
  `AntlersModifierMixin` whose `modifierName` is not in `catalog.modifiers()`. No quickfix.

---

### Task 1 — [Critical] Dynamic catalog scan

**Files:** create `src/test/kotlin/.../catalog/scan/TagScannerTest.kt`,
`src/test/kotlin/.../catalog/scan/ModifierScannerTest.kt`,
`src/test/kotlin/.../catalog/CatalogDynamicScanTest.kt`.

Cover (`BasePlatformTestCase`, files via `myFixture.addFileToProject`):
- `TagScannerTest`: `scan()` finds `class FooBar extends Tags` under a `Tags/` dir → `["foo_bar"]`; the
  FQN form `extends \Statamic\Tags\Tags`; a class NOT under `/Tags/` is ignored; `camelToSnake("FooBar")
  == "foo_bar"` (and an all-caps/edge case); `find(project, "foo_bar")` returns a non-null `NavTarget`
  at the class-name offset.
- `ModifierScannerTest`: same shape for `/Modifiers/` + `extends Modifier`.
- `CatalogDynamicScanTest` (end-to-end): add `app/Tags/MyThing.php` (`class MyThing extends Tags`) →
  `AntlersCatalogService.getInstance(project).tags()` contains a `my_thing` tag AND completion at
  `{{ my_<caret> }}` offers `my_thing`. Add `app/Modifiers/ShoutLoud.php` → `modifiers()` contains
  `shout_loud` AND completion after `{{ x | sho<caret> }}` offers it.

Run the three classes, then the full gate. Commit.

---

### Task 2 — [Important] Parser corpus expansion

**Files:** add input/expected pairs under `src/test/testData/parsing/` and `test…()` methods to
`src/test/kotlin/.../AntlersParsingTest.kt`.

Add corpus cases (one `.antlers.html` + generated `.txt` each): `UnclosedTag` (error recovery —
`{{ collection`), `NestedTags`, `ModifierChain` (`{{ x | upper | truncate:10 }}`), `BoundParam`
(`{{ partial :src="x" }}`), `BracketAccess` (`{{ data[key] }}`), `PhpBlock` (`{{$ … $}}` / `{{? … ?}}`
per the lexer), `NoparseBlock`, `MultiLineTag`.

**Generating the `.txt`:** create the `.antlers.html`, add `fun testX() = doTest(true)`, run it once —
`ParsingTestCase` writes the missing expected file (or the failure prints the actual tree). Save the
produced tree as `<Name>.txt`, re-run to confirm green. Verify each input lexes/parses as intended
before pinning (don't pin a tree that's accidentally wrong — e.g. confirm `PhpBlock` actually uses the
real Antlers PHP delimiters by checking `AntlersLexer.flex`). Run the full gate. Commit.

---

### Task 3 — [Important] Commenter + brace-matcher behavior

**Files:** create `src/test/kotlin/.../editor/AntlersBraceMatcherBehaviorTest.kt`; extend or replace the
commenter smoke test.

- **Brace match (reliable):** `AntlersBraceMatcherBehaviorTest` — caret at the opening `{{` of
  `{{ collection }}`, `performEditorAction(IdeActions.ACTION_EDITOR_MATCH_BRACE)`, assert the caret moved
  to the matching `}}` (and back). Also a comment-open/close pair if supported.
- **Commenter:** ATTEMPT a behavioral round-trip — caret inside `{{ x }}`,
  `performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)`, assert the line becomes `{{# … #}}` and a
  second action un-comments. **If the platform routes to the HTML data-language commenter** (producing
  `<!-- -->`), do NOT fake it: keep a meaningful assertion that the Antlers comment delimiters are what
  our commenter contributes (and document the routing limitation in a comment). Report which path held.

Run the full gate. Commit.

---

### Task 4 — [Important] Inspection false-positives

**Files:** extend `src/test/kotlin/.../inspection/AntlersUnknownModifierInspectionTest.kt`.

Using `myFixture.enableInspections(AntlersUnknownModifierInspection())` +
`myFixture.doHighlighting()` (or `<warning>` markup with `testHighlighting`):
- **True positive:** `{{ x | bogusmod }}` → a WEAK_WARNING "Unknown modifier 'bogusmod'".
- **No false positive on a known modifier:** `{{ x | upper }}` → no warning.
- **No false positive on a scanned custom modifier:** add `app/Modifiers/MyFmt.php`
  (`class MyFmt extends Modifier`) → `{{ x | my_fmt }}` → no warning.
- **No false positive on a parameterized/known modifier with args:** `{{ x | truncate:10 }}` → no
  warning on `truncate`.

(There is no quickfix, so no quickfix test.) Run the full gate. Commit.

---

## Self-Review
- Covers the audit's Critical (dynamic scan: scanners + end-to-end completion) + the three Important
  gaps (corpus, commenter/brace behavior, inspection false-positives).
- Honors the two infeasibility caveats explicitly (commenter routing; PSI-dump generation) with
  "discover-and-pin / report, don't fake" guidance.
- Test-only; any production bug surfaced → STOP and report.
