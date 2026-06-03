# Antlers Quick Wins Implementation Plan

> Three tiny independent changes (#5 system vars, #10 plugin name, #7 param color). Execute INLINE.

**Spec:** `docs/superpowers/specs/2026-06-03-antlers-quick-wins-design.md`
**Branch:** `antlers-quick-wins` (from `main`).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty).

---

### Task A: Add missing global system variables (#5)

**Files:** `src/main/kotlin/.../blueprint/SystemVariables.kt`; test in
`src/test/kotlin/.../AntlersVariableCompletionTest.kt` (read it first; reuse its completion helper).

- [ ] Add these entries to `SystemVariables.ALL` (after the existing list, before the closing `)`):
```kotlin
        SystemVariable("template_content", "The rendered template content (output inside a layout)."),
        SystemVariable("current_template", "The template actually used to render the page."),
        SystemVariable("current_layout", "The layout actually used to render the page."),
        SystemVariable("current_user", "The authenticated user (null if logged out)."),
        SystemVariable("logged_in", "Whether the visitor is authenticated."),
        SystemVariable("homepage", "The site's homepage URL."),
        SystemVariable("is_homepage", "Whether the current URL is the homepage."),
        SystemVariable("last_segment", "The final segment of the current URL."),
        SystemVariable("segment_1", "The first URL segment (segment_2, segment_3, … follow)."),
        SystemVariable("csrf_field", "A hidden input field containing the CSRF token."),
        SystemVariable("config", "Access Statamic/Laravel configuration values."),
        SystemVariable("get", "Query-string variables."),
        SystemVariable("post", "Submitted POST data."),
        SystemVariable("old", "Old (previous-request) input, for re-populating forms after validation."),
        SystemVariable("response_code", "The HTTP response code (200 or 404)."),
        SystemVariable("live_preview", "Whether the page is rendering in Live Preview."),
        SystemVariable("edit_url", "Control-Panel edit URL for the current content."),
        SystemVariable("sites", "All configured sites."),
        SystemVariable("is_entry", "Whether the current content is an entry."),
```
- [ ] Add a test (extend `AntlersVariableCompletionTest` using its existing completion helper): completing
  `{{ <caret> }}` (top level, no scope) offers `template_content` and `current_user`. Run, then full gate.
- [ ] Commit: `feat: add missing global system variables (template_content, current_user, …)`.

---

### Task B: Plugin display name → "Antlers" (#10)

**Files:** `src/main/resources/META-INF/plugin.xml`.

- [ ] Change `<name>intellij-antlers</name>` to `<name>Antlers</name>`.
- [ ] Verify `./gradlew patchPluginXml` succeeds (validates the descriptor). No behavioral test.
- [ ] Commit: `chore: set plugin display name to "Antlers"`.

---

### Task C: Distinct parameter-name color (#7)

**Files:** `AntlersSyntaxHighlighter.kt`, `AntlersSemanticHighlightAnnotator.kt`,
`AntlersColorSettingsPage.kt`; test `AntlersSemanticHighlightTest.kt`.

- [ ] **Test first** — add to `AntlersSemanticHighlightTest` (uses the existing `keyOver` helper):
```kotlin
    fun testParameterNameColored() =
        assertEquals(AntlersSyntaxHighlighter.PARAMETER, keyOver("{{ collection from=\"x\" }}", "from"))

    fun testBoundParameterNameColored() =
        assertEquals(AntlersSyntaxHighlighter.PARAMETER, keyOver("{{ partial :src=\"x\" }}", "src"))
```
  Run → FAIL (PARAMETER doesn't exist / not painted).
- [ ] In `AntlersSyntaxHighlighter.kt`, add after the `MODIFIER` key:
```kotlin
        val PARAMETER = TextAttributesKey.createTextAttributesKey("ANTLERS_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER)
```
  (If `DefaultLanguageHighlighterColors.PARAMETER` does not resolve in this SDK, use `INSTANCE_FIELD`.)
- [ ] In `AntlersSemanticHighlightAnnotator.kt`, add the import
  `import com.github.balotias.intellijantlers.psi.AntlersParameterMixin` and a `when` case (place it
  before the `AntlersNamePathMixin` case):
```kotlin
            is AntlersParameterMixin ->
                firstIdent(element)?.let { paint(holder, it, AntlersSyntaxHighlighter.PARAMETER) }
```
- [ ] In `AntlersColorSettingsPage.kt`: add `"param" to AntlersSyntaxHighlighter.PARAMETER` to the
  additional-highlight map; add `AttributesDescriptor("Parameter name", AntlersSyntaxHighlighter.PARAMETER)`
  to `DESCRIPTORS`; and mark a param in the `DEMO` text, e.g. change `limit="3"` to
  `<param>limit</param>="3"`.
- [ ] Run `./gradlew --rerun-tasks test --tests "*AntlersSemanticHighlightTest"` → PASS. Then full gate.
- [ ] Commit: `feat: color Antlers parameter names distinctly from variables`.

---

## Self-Review
- A: 19 global vars added (asset-only excluded); end-to-end completion test for two of them.
- B: one metadata line; `patchPluginXml` validates.
- C: new `PARAMETER` key + annotator `AntlersParameterMixin` case (covers static + bound) + settings
  descriptor/demo; tests assert `from` and bound `src` get `ANTLERS_PARAMETER` while variables stay
  uncolored (existing `testPlainVariableNotColored`).
