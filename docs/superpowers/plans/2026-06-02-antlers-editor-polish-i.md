# Antlers Editor Polish (I) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the three editor-polish conveniences — a ColorSettingsPage, a bundled live-template set scoped to Antlers, and a "New → Antlers Template" file action — without touching existing language code.

**Architecture:** Three independent components, each registered through `plugin.xml` (+ resource files): `AntlersColorSettingsPage` (reuses the existing `AntlersSyntaxHighlighter` keys), `AntlersTemplateContextType` + `liveTemplates/Antlers.xml`, and `CreateAntlersFileAction` + an internal `.ft` template. No new dependency.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`ColorSettingsPage`, `TemplateContextType`, `CreateFileFromTemplateAction`, internal file templates), `BasePlatformTestCase`. Tests run via `./gradlew test`; the build cache returns cached results, so use `--rerun-tasks` and gate on `build/test-results/test/*.xml`.

**Existing facts (verified):**
- `highlighting/AntlersSyntaxHighlighter` exposes companion keys `BRACES`, `IDENTIFIER`, `STRING`, `NUMBER`, `COMMENT`, `OPERATOR`.
- `AntlersIcons.FILE` is the icon; `AntlersFileType` (name "Antlers", ext `antlers.html`).
- `plugin.xml` has an `<extensions defaultExtensionNs="com.intellij">` block ending with `</extensions>` then `</idea-plugin>`. There is **no `<actions>` block yet** — Task 3 adds one.

---

### Task 1: `AntlersColorSettingsPage`

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPageTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersColorSettingsPageTest : BasePlatformTestCase() {

    fun testDescriptorsCoverExactlyTheSixKeys() {
        val page = AntlersColorSettingsPage()
        val keys = page.attributeDescriptors.map { it.key }.toSet()
        val expected = setOf(
            AntlersSyntaxHighlighter.BRACES,
            AntlersSyntaxHighlighter.IDENTIFIER,
            AntlersSyntaxHighlighter.STRING,
            AntlersSyntaxHighlighter.NUMBER,
            AntlersSyntaxHighlighter.COMMENT,
            AntlersSyntaxHighlighter.OPERATOR,
        )
        assertEquals(expected, keys)
    }

    fun testBasics() {
        val page = AntlersColorSettingsPage()
        assertEquals("Antlers", page.displayName)
        assertTrue("demo text present", page.demoText.isNotBlank())
        assertTrue("reuses the Antlers highlighter", page.highlighter is AntlersSyntaxHighlighter)
        assertNull("no additional highlighting tags", page.additionalHighlightingTagToDescriptorMap)
    }
}
```

- [ ] **Step 2: Run, confirm failure (class missing)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.highlighting.AntlersColorSettingsPageTest"`
Expected: FAIL — `AntlersColorSettingsPage` does not exist.

- [ ] **Step 3: Implement**

```kotlin
package com.github.balotias.intellijantlers.highlighting

import com.github.balotias.intellijantlers.AntlersIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class AntlersColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = AntlersIcons.FILE
    override fun getHighlighter(): SyntaxHighlighter = AntlersSyntaxHighlighter()
    override fun getDemoText(): String = DEMO
    override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey>? = null
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = "Antlers"

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Braces & delimiters", AntlersSyntaxHighlighter.BRACES),
            AttributesDescriptor("Identifier", AntlersSyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("String", AntlersSyntaxHighlighter.STRING),
            AttributesDescriptor("Number", AntlersSyntaxHighlighter.NUMBER),
            AttributesDescriptor("Comment", AntlersSyntaxHighlighter.COMMENT),
            AttributesDescriptor("Operator", AntlersSyntaxHighlighter.OPERATOR),
        )

        private val DEMO = """
            {{# Featured posts #}}
            {{ collection:blog limit="3" as="posts" }}
              {{ title | upper }}
              {{ if count > 0 }}{{ price }}{{ /if }}
            {{ /collection }}
        """.trimIndent()
    }
}
```

- [ ] **Step 4: Register in `plugin.xml`** — add this line just inside `</extensions>` (e.g. directly below the `<postFormatProcessor .../>` line):

```xml
        <colorSettingsPage implementation="com.github.balotias.intellijantlers.highlighting.AntlersColorSettingsPage"/>
```

- [ ] **Step 5: Run, confirm pass (2 tests)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.highlighting.AntlersColorSettingsPageTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPage.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/highlighting/AntlersColorSettingsPageTest.kt
git commit -m "Add Antlers ColorSettingsPage (recolorable token groups + demo)"
```

---

### Task 2: Live templates (context type + bundled set)

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/template/AntlersTemplateContextType.kt`
- Create: `src/main/resources/liveTemplates/Antlers.xml`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/template/AntlersLiveTemplatesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.template

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersLiveTemplatesTest : BasePlatformTestCase() {

    fun testInContextForAntlersFile() {
        myFixture.configureByText("page.antlers.html", "{{ x }}")
        val ctx = TemplateActionContext.expandTemplate(myFixture.file, myFixture.editor, 0, 0)
        assertTrue(AntlersTemplateContextType().isInContext(ctx))
    }

    fun testNotInContextForPlainText() {
        myFixture.configureByText("note.txt", "hello")
        val ctx = TemplateActionContext.expandTemplate(myFixture.file, myFixture.editor, 0, 0)
        assertFalse(AntlersTemplateContextType().isInContext(ctx))
    }

    fun testBundledTemplatesParseAndAreAntlersScoped() {
        val text = javaClass.classLoader.getResourceAsStream("liveTemplates/Antlers.xml")
            ?.bufferedReader()?.readText() ?: error("liveTemplates/Antlers.xml not on classpath")
        listOf("if", "ife", "unless", "coll", "partial", "pair").forEach { abbrev ->
            assertTrue("missing abbreviation '$abbrev': $text", text.contains("name=\"$abbrev\""))
        }
        assertTrue("templates must carry the ANTLERS context: $text",
            text.contains("name=\"ANTLERS\" value=\"true\""))
    }
}
```

- [ ] **Step 2: Run, confirm failure (class + resource missing)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.template.AntlersLiveTemplatesTest"`
Expected: FAIL — `AntlersTemplateContextType` missing and/or resource not found.

- [ ] **Step 3: Implement the context type**

```kotlin
package com.github.balotias.intellijantlers.template

import com.github.balotias.intellijantlers.AntlersFileType
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

/** Scopes the bundled live templates to Antlers files. The context id is set via the
 *  `<liveTemplateContext contextId="ANTLERS">` registration and referenced by the templates XML. */
class AntlersTemplateContextType : TemplateContextType("Antlers") {
    override fun isInContext(context: TemplateActionContext): Boolean =
        context.file.fileType is AntlersFileType
}
```

SDK-shape note: if the single-arg `TemplateContextType(String)` constructor doesn't compile, use the two-arg `TemplateContextType("ANTLERS", "Antlers")` form (id, presentableName) and keep `contextId="ANTLERS"` in plugin.xml consistent. If `TemplateActionContext.expandTemplate(PsiFile, Editor, int, int)` in the test doesn't match this SDK, adapt the `TemplateActionContext` factory call (e.g. the `expandTemplate(file, startOffset, endOffset, "")` overload) — keep the assertions (true for the antlers file, false for the txt file).

- [ ] **Step 4: Create `src/main/resources/liveTemplates/Antlers.xml`**

```xml
<templateSet group="Antlers">
  <template name="if" value="{{ if $COND$ }}$END${{ /if }}" description="Antlers if block" toReformat="false" toShortenFQNames="false">
    <variable name="COND" expression="" defaultValue="" alwaysStopAt="true"/>
    <context>
      <option name="ANTLERS" value="true"/>
    </context>
  </template>
  <template name="ife" value="{{ if $COND$ }}$SELECTION$$END${{ else }}{{ /if }}" description="Antlers if/else block" toReformat="false" toShortenFQNames="false">
    <variable name="COND" expression="" defaultValue="" alwaysStopAt="true"/>
    <context>
      <option name="ANTLERS" value="true"/>
    </context>
  </template>
  <template name="unless" value="{{ unless $COND$ }}$END${{ /unless }}" description="Antlers unless block" toReformat="false" toShortenFQNames="false">
    <variable name="COND" expression="" defaultValue="" alwaysStopAt="true"/>
    <context>
      <option name="ANTLERS" value="true"/>
    </context>
  </template>
  <template name="coll" value="{{ collection:$HANDLE$ }}$END${{ /collection }}" description="Antlers collection loop" toReformat="false" toShortenFQNames="false">
    <variable name="HANDLE" expression="" defaultValue="&quot;blog&quot;" alwaysStopAt="true"/>
    <context>
      <option name="ANTLERS" value="true"/>
    </context>
  </template>
  <template name="partial" value="{{ partial:src=&quot;$PATH$&quot; }}" description="Antlers partial include" toReformat="false" toShortenFQNames="false">
    <variable name="PATH" expression="" defaultValue="" alwaysStopAt="true"/>
    <context>
      <option name="ANTLERS" value="true"/>
    </context>
  </template>
  <template name="pair" value="{{ $TAG$ }}$END${{ /$TAG$ }}" description="Antlers paired tag" toReformat="false" toShortenFQNames="false">
    <variable name="TAG" expression="" defaultValue="" alwaysStopAt="true"/>
    <context>
      <option name="ANTLERS" value="true"/>
    </context>
  </template>
</templateSet>
```

- [ ] **Step 5: Register in `plugin.xml`** — add these two lines just inside `</extensions>` (below the `<colorSettingsPage>` from Task 1):

```xml
        <liveTemplateContext contextId="ANTLERS" implementation="com.github.balotias.intellijantlers.template.AntlersTemplateContextType"/>
        <defaultLiveTemplates file="liveTemplates/Antlers"/>
```

Note: the `file` attribute is the resource path WITHOUT the `.xml` suffix; the file on disk is `Antlers.xml`. The `contextId="ANTLERS"` MUST equal the `<option name="ANTLERS">` used in the XML.

- [ ] **Step 6: Run, confirm pass (3 tests)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.template.AntlersLiveTemplatesTest"`
Expected: PASS. If `testInContext*` fails on the `TemplateActionContext` API, fix the factory call per the SDK note (the XML test must pass regardless — it's plain text inspection).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/template/AntlersTemplateContextType.kt \
        src/main/resources/liveTemplates/Antlers.xml \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/template/AntlersLiveTemplatesTest.kt
git commit -m "Add Antlers live templates (context type + bundled if/unless/coll/partial/pair set)"
```

---

### Task 3: File template — "New → Antlers Template"

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/actions/CreateAntlersFileAction.kt`
- Create: `src/main/resources/fileTemplates/internal/Antlers Template.antlers.html.ft`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/actions/AntlersFileTemplateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFileTemplateTest : BasePlatformTestCase() {

    fun testInternalTemplateContent() {
        val text = javaClass.classLoader
            .getResourceAsStream("fileTemplates/internal/Antlers Template.antlers.html.ft")
            ?.bufferedReader()?.readText()
            ?: error("internal file template not on classpath")
        assertTrue("starter contains a body placeholder: $text", text.contains("template_content"))
        assertTrue("starter is an html skeleton: $text", text.contains("<html"))
    }

    fun testActionPresentation() {
        val action = CreateAntlersFileAction()
        assertEquals("Antlers Template", action.templatePresentation.text)
    }
}
```

- [ ] **Step 2: Run, confirm failure (action + resource missing)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.actions.AntlersFileTemplateTest"`
Expected: FAIL — `CreateAntlersFileAction` missing and/or resource not found.

- [ ] **Step 3: Implement the action**

```kotlin
package com.github.balotias.intellijantlers.actions

import com.github.balotias.intellijantlers.AntlersIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

/** Adds "New → Antlers Template", creating a starter `.antlers.html` from the bundled template. */
class CreateAntlersFileAction : CreateFileFromTemplateAction(
    "Antlers Template", "Creates a new Antlers template", AntlersIcons.FILE
) {
    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
        "Create Antlers Template"

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle("New Antlers Template")
            .addKind("Antlers Template", AntlersIcons.FILE, "Antlers Template")
    }
}
```

SDK-shape note: `CreateFileFromTemplateAction`'s abstract members across SDK versions are `getActionName(PsiDirectory, String, String)` and `buildDialog(Project, PsiDirectory, Builder)`. If the constructor signature differs, pass the same three values (text, description, icon) in the form that compiles. The `addKind` third argument (`"Antlers Template"`) MUST equal the internal template name registered below and the `.ft` filename stem.

- [ ] **Step 4: Create `src/main/resources/fileTemplates/internal/Antlers Template.antlers.html.ft`**

```
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>{{ title }}</title>
</head>
<body>
    {{ template_content }}
</body>
</html>
```

- [ ] **Step 5: Register in `plugin.xml`**

(a) Add the internal template inside `</extensions>` (below the Task-2 lines):
```xml
        <internalFileTemplate name="Antlers Template"/>
```

(b) Add a NEW `<actions>` block AFTER `</extensions>` and BEFORE `</idea-plugin>` (there is no actions block yet):
```xml
    <actions>
        <action id="Antlers.CreateFile"
                class="com.github.balotias.intellijantlers.actions.CreateAntlersFileAction">
            <add-to-group group-id="NewGroup" anchor="before" relative-to-action="NewFile"/>
        </action>
    </actions>
```

- [ ] **Step 6: Run, confirm pass (2 tests)**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.actions.AntlersFileTemplateTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/actions/CreateAntlersFileAction.kt \
        "src/main/resources/fileTemplates/internal/Antlers Template.antlers.html.ft" \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/actions/AntlersFileTemplateTest.kt
git commit -m "Add New > Antlers Template file action + bundled starter template"
```

---

### Task 4: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the entire suite, forcing re-execution**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on zero failures**

Run: `echo "failed_files=$(grep -lo 'failures=\"[1-9]\|errors=\"[1-9]' build/test-results/test/*.xml | wc -l | tr -d ' ')"; echo "total_tests=$(grep -ho 'tests=\"[0-9]*\"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc)"`
Expected: `failed_files=0`. `total_tests` should be the prior 204 plus the new ~7 (2 color + 3 templates + 2 file-template).

- [ ] **Step 3: Confirm `compileKotlin`/`compileTestKotlin` are clean**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify plugin.xml is well-formed (no duplicate/misplaced tags)**

Run: `grep -c "<colorSettingsPage\|<liveTemplateContext\|<defaultLiveTemplates\|<internalFileTemplate" src/main/resources/META-INF/plugin.xml` (expect 4) and confirm the `<actions>` block sits after `</extensions>`.

---

## Self-Review

**Spec coverage:**
- Component A (ColorSettingsPage) → Task 1; descriptors cover all six keys (tested).
- Component B (live templates: context type + bundled XML, six abbreviations, Antlers-scoped) → Task 2.
- Component C (file template: action + `.ft` + New-group registration) → Task 3.
- Out-of-scope (ParameterInfo, breadcrumbs) → not implemented.
- Testing approach (descriptor coverage, in-context predicate, XML inspection, template content + action presentation) → matches the spec's testing section.

**Placeholder scan:** none — all code/resources are complete; commands have expected output.

**Type/string consistency:** the context id string `ANTLERS` is identical across the context-type registration (`contextId="ANTLERS"`), the XML (`<option name="ANTLERS">`), and the test assertion. The internal template name `Antlers Template` is identical across the `.ft` filename stem, `<internalFileTemplate name>`, `addKind(...)`'s third arg, and the action presentation text. The six attribute keys referenced in Task 1's descriptors match the companion vals in `AntlersSyntaxHighlighter`.

**SDK-shape risks flagged inline:** `TemplateContextType` constructor arity (Task 2 Step 3), `TemplateActionContext` factory (Task 2 Step 1/6), `CreateFileFromTemplateAction` member signatures (Task 3 Step 3). The implementer matches whichever compiles; behavior contracts are pinned by the tests.
