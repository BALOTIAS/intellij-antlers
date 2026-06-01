# Antlers Semantic Layer (Sub-project C: docs + navigation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add catalog-backed quick documentation, partial path go-to-definition + completion, and custom tag/modifier go-to-definition to the Antlers plugin — platform-only.

**Architecture:** A `DocumentationProvider` and two `PsiReferenceContributor`s, all built on the existing `AntlersCatalogService`, PSI mixins (`AntlersNamePath`/`AntlersParameter`/`AntlersModifier`/`AntlersStatement`), and the `TagScanner`/`ModifierScanner`. A small `StatamicProject` helper locates the `resources/views` root for partial resolution.

**Tech Stack:** Kotlin, IntelliJ Platform 2025.2, `BasePlatformTestCase`.

**Companion spec:** `docs/superpowers/specs/2026-06-01-antlers-semantic-layer-design.md`

**Test platform note:** `./gradlew test` runs the full suite (works now). In-sandbox, also verify with `./gradlew compileKotlin compileTestKotlin`.

---

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `documentation/AntlersDocumentationProvider.kt` | Quick docs for tag/method/param/modifier from the catalog | Rewrite |
| `references/StatamicProject.kt` | Locate `resources/views` root from a file | Create |
| `references/AntlersPartialReference.kt` | Reference from a partial path to its template file | Create |
| `references/AntlersPartialReferenceContributor.kt` | Attach the partial reference (`src=` and `:path` forms) | Create |
| `references/AntlersPhpClassReference.kt` | Reference from a custom tag/modifier name to its PHP class file | Create |
| `references/AntlersDefinitionReferenceContributor.kt` | Attach the PHP-class reference (tag head + modifier name) | Create |
| `catalog/scan/TagScanner.kt`, `ModifierScanner.kt` | Add `find(project, name): NavTarget?` | Modify |
| `resources/META-INF/plugin.xml` | Register 2 `psi.referenceContributor`s | Modify |
| tests under `src/test/.../documentation/`, `references/` | Docs, partial nav, custom-tag nav | Create |

---

## Task 1: Documentation provider

**Files:**
- Rewrite: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProviderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProviderTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersDocumentationProviderTest : BasePlatformTestCase() {

    private fun docAt(text: String): String? {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("t.antlers.html", text.replace("<caret>", ""))
        val element = myFixture.file.findElementAt(caret) ?: myFixture.file.findElementAt(caret - 1)!!
        return AntlersDocumentationProvider().generateDoc(element, element)
    }

    fun testTagDoc() {
        val doc = docAt("{{ colle<caret>ction }}")
        assertNotNull(doc)
        assertTrue("has description: $doc", doc!!.contains("collection"))
        assertTrue("has doc url: $doc", doc.contains("statamic.dev"))
    }

    fun testParameterDoc() {
        val doc = docAt("{{ collection li<caret>mit=\"5\" }}")
        assertNotNull(doc)
        assertTrue("mentions limit: $doc", doc!!.contains("limit"))
    }

    fun testModifierDoc() {
        val doc = docAt("{{ title | up<caret>per }}")
        assertNotNull(doc)
        assertTrue("mentions upper: $doc", doc!!.contains("upper"))
    }

    fun testNoDocForPlainVariable() {
        assertNull(docAt("{{ some_random_var<caret> }}"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersDocumentationProviderTest"` → FAIL (stub returns nothing useful). Sandbox: `./gradlew compileTestKotlin`.

- [ ] **Step 3: Rewrite the provider**

Replace `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`:

```kotlin
package com.github.balotias.intellijantlers.documentation

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.catalog.ParamDef
import com.github.balotias.intellijantlers.catalog.TagDef
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** Catalog-backed quick documentation for Antlers tags, methods, parameters, and modifiers. */
class AntlersDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val ident = originalElement ?: element ?: return null
        if (ident.node?.elementType != AntlersTypes.T_IDENT) return null
        val name = ident.text
        val catalog = AntlersCatalogService.getInstance(ident.project)

        // Modifier: the identifier is the modifier name.
        PsiTreeUtil.getParentOfType(ident, AntlersModifierMixin::class.java)?.let { mod ->
            if (mod.modifierName == name) {
                val def = catalog.modifiers().firstOrNull { it.name == name } ?: return null
                return section(
                    "Antlers modifier <b>${esc(name)}</b>",
                    def.description,
                    def.docUrl
                )
            }
        }

        // Parameter: the identifier is the parameter name of a known tag.
        PsiTreeUtil.getParentOfType(ident, AntlersParameterMixin::class.java)?.let { param ->
            if (param.parameterName == name) {
                val tag = enclosingTag(ident, catalog) ?: return null
                val p = tag.parameters.firstOrNull { it.name == name } ?: return null
                val type = if (p.type.isNotBlank()) " : ${esc(p.type)}" else ""
                val req = if (p.required) " (required)" else ""
                return section(
                    "Parameter <b>${esc(name)}</b>$type$req — tag <code>${esc(tag.name)}</code>",
                    p.description,
                    tag.docUrl
                )
            }
        }

        // Tag head or method.
        PsiTreeUtil.getParentOfType(ident, AntlersNamePathMixin::class.java)?.let { path ->
            if (path.head == name) {
                val tag = catalog.tag(name) ?: return null
                return tagDoc(tag)
            }
            if (path.method == name) {
                val tag = catalog.tag(path.head) ?: return null
                return section(
                    "Method <b>${esc(name)}</b> of tag <code>${esc(tag.name)}</code>",
                    tag.description,
                    tag.docUrl
                )
            }
        }
        return null
    }

    private fun enclosingTag(ident: PsiElement, catalog: AntlersCatalogService): TagDef? {
        val statement = PsiTreeUtil.getParentOfType(ident, AntlersStatement::class.java) ?: return null
        val head = PsiTreeUtil.findChildOfType(statement, AntlersNamePathMixin::class.java)?.head ?: return null
        return catalog.tag(head)
    }

    private fun tagDoc(tag: TagDef): String {
        val kind = if (tag.isPair) "block tag" else "tag"
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("Antlers $kind <b>${esc(tag.name)}</b>")
        sb.append(DocumentationMarkup.DEFINITION_END)
        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append(esc(tag.description))
        if (tag.parameters.isNotEmpty()) {
            sb.append("<br/><br/><b>Parameters</b><br/>")
            for (p: ParamDef in tag.parameters) {
                sb.append("<code>${esc(p.name)}</code> — ${esc(p.description)}<br/>")
            }
        }
        sb.append(DocumentationMarkup.CONTENT_END)
        appendDocUrl(sb, tag.docUrl)
        return sb.toString()
    }

    private fun section(title: String, description: String, docUrl: String): String {
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START).append(title).append(DocumentationMarkup.DEFINITION_END)
        if (description.isNotBlank()) {
            sb.append(DocumentationMarkup.CONTENT_START).append(esc(description)).append(DocumentationMarkup.CONTENT_END)
        }
        appendDocUrl(sb, docUrl)
        return sb.toString()
    }

    private fun appendDocUrl(sb: StringBuilder, docUrl: String) {
        if (docUrl.isNotBlank()) {
            sb.append(DocumentationMarkup.SECTIONS_START)
            sb.append("<a href=\"${esc(docUrl)}\">${esc(docUrl)}</a>")
            sb.append(DocumentationMarkup.SECTIONS_END)
        }
    }

    private fun esc(s: String): String = StringUtil.escapeXmlEntities(s)
}
```

- [ ] **Step 4: Verify**

Run: `./gradlew test --tests "*AntlersDocumentationProviderTest"` → PASS. Sandbox: `./gradlew compileTestKotlin`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/documentation src/test/kotlin/com/github/balotias/intellijantlers/documentation
git commit -m "Catalog-backed quick documentation for Antlers tags, params, modifiers"
```

---

## Task 2: Partial navigation (reference + completion)

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/StatamicProject.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReference.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceContributor.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersPartialReferenceTest : BasePlatformTestCase() {

    private fun setupViews() {
        myFixture.addFileToProject("resources/views/blog/card.antlers.html", "<div>card</div>")
    }

    private fun resolveAt(path: String, text: String): PsiFile? {
        setupViews()
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val ref = file.findReferenceAt(caret)
        return ref?.resolve() as? PsiFile
    }

    fun testResolveSrcForm() {
        val target = resolveAt("blog/card", "{{ partial:src=\"blog/c<caret>ard\" }}")
        assertNotNull("src= partial should resolve", target)
        assertEquals("card.antlers.html", target!!.name)
    }

    fun testResolveMethodForm() {
        val target = resolveAt("blog/card", "{{ partial:blog/c<caret>ard }}")
        assertNotNull(":path partial should resolve", target)
        assertEquals("card.antlers.html", target!!.name)
    }

    fun testUnknownPartialUnresolved() {
        assertNull(resolveAt("blog/card", "{{ partial:src=\"no/su<caret>ch\" }}"))
    }

    fun testCompletionListsPartials() {
        setupViews()
        myFixture.configureByText("page.antlers.html", "{{ partial:src=\"<caret>\" }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue("offers blog/card: $variants", variants.any { it.contains("blog/card") })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersPartialReferenceTest"` → FAIL. Sandbox: `./gradlew compileTestKotlin`.

- [ ] **Step 3: Create the views-root locator**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/StatamicProject.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/** Locates the Statamic/Laravel `resources/views` root for partial resolution. */
object StatamicProject {

    /** The `resources/views` directory that is an ancestor of [element]'s file, or null. */
    fun viewsRoot(element: PsiElement): VirtualFile? {
        var dir: VirtualFile? = element.containingFile?.originalFile?.virtualFile?.parent
        while (dir != null) {
            if (dir.name == "views" && dir.parent?.name == "resources") return dir
            dir = dir.parent
        }
        // Fallback: first `resources/views` under the project base dir.
        val base = element.project.baseDir ?: return null
        return findViews(base)
    }

    private fun findViews(root: VirtualFile): VirtualFile? {
        if (root.name == "views" && root.parent?.name == "resources") return root
        for (child in root.children) {
            if (child.isDirectory) findViews(child)?.let { return it }
        }
        return null
    }
}
```

- [ ] **Step 4: Create the reference**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReference.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.util.ProcessingContext

private val PARTIAL_EXTENSIONS = listOf("antlers.html", "html")

/** Resolves a partial path (e.g. "blog/card") to its template file under `resources/views`. */
class AntlersPartialReference(
    element: PsiElement,
    range: TextRange,
    private val path: String
) : PsiReferenceBase<PsiElement>(element, range) {

    override fun resolve(): PsiElement? {
        val root = StatamicProject.viewsRoot(element) ?: return null
        for (ext in PARTIAL_EXTENSIONS) {
            val vf = root.findFileByRelativePath("$path.$ext") ?: continue
            return PsiManager.getInstance(element.project).findFile(vf)
        }
        return null
    }

    override fun getVariants(): Array<Any> {
        val root = StatamicProject.viewsRoot(element) ?: return emptyArray()
        val result = mutableListOf<String>()
        VfsUtilCore.iterateChildrenRecursively(root, { it.isDirectory || it.name.endsWith(".antlers.html") || it.name.endsWith(".html") }) { vf ->
            if (!vf.isDirectory) {
                var rel = VfsUtilCore.getRelativePath(vf, root) ?: return@iterateChildrenRecursively true
                rel = rel.removeSuffix(".antlers.html").removeSuffix(".html")
                result.add(rel)
            }
            true
        }
        return result.toTypedArray()
    }
}
```

- [ ] **Step 5: Create the contributor**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPartialReferenceContributor.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersParameterMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

/** Attaches a partial reference to `{{ partial:src="path" }}` and `{{ partial:path }}`. */
class AntlersPartialReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // src="..." string value form
        registrar.registerReferenceProvider(
            psiElement(AntlersTypes.T_STRING),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val param = PsiTreeUtil.getParentOfType(element, AntlersParameterMixin::class.java) ?: return emptyArray()
                    if (param.parameterName != "src") return emptyArray()
                    if (!isPartialStatement(element)) return emptyArray()
                    val raw = element.text
                    val inner = raw.removeSurrounding("\"").removeSurrounding("'")
                    val start = if (raw.length >= 2) 1 else 0
                    return arrayOf(AntlersPartialReference(element, TextRange(start, start + inner.length), inner))
                }
            }
        )
        // partial:path method form — the method identifier after `partial:`
        registrar.registerReferenceProvider(
            psiElement(AntlersTypes.T_IDENT),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val path = PsiTreeUtil.getParentOfType(element, AntlersNamePathMixin::class.java) ?: return emptyArray()
                    if (path.head != "partial" || path.method != element.text) return emptyArray()
                    return arrayOf(AntlersPartialReference(element, TextRange(0, element.textLength), element.text))
                }
            }
        )
    }

    private fun isPartialStatement(element: PsiElement): Boolean {
        val statement = PsiTreeUtil.getParentOfType(element, AntlersStatement::class.java) ?: return false
        return PsiTreeUtil.findChildOfType(statement, AntlersNamePathMixin::class.java)?.head == "partial"
    }
}
```

- [ ] **Step 6: Register in plugin.xml**

Add inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
        <psi.referenceContributor language="Antlers" implementation="com.github.balotias.intellijantlers.references.AntlersPartialReferenceContributor"/>
```

- [ ] **Step 7: Verify + commit**

Run: `./gradlew test --tests "*AntlersPartialReferenceTest"` → PASS. Sandbox: `./gradlew compileTestKotlin`.

```bash
git add -A
git commit -m "Partial path go-to-definition and completion under resources/views"
```

---

## Task 3: Custom tag/modifier go-to-definition

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/scan/TagScanner.kt`, `ModifierScanner.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPhpClassReference.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceContributor.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersDefinitionReferenceTest : BasePlatformTestCase() {

    private fun setupTag() {
        myFixture.addFileToProject(
            "app/Tags/MyThing.php",
            "<?php\nnamespace App\\Tags;\nuse Statamic\\Tags\\Tags;\nclass MyThing extends Tags {}\n"
        )
    }

    private fun resolveAt(text: String): PsiFile? {
        setupTag()
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return file.findReferenceAt(caret)?.resolve()?.containingFile
    }

    fun testCustomTagResolves() {
        val target = resolveAt("{{ my_th<caret>ing }}")
        assertNotNull("custom tag should resolve to its PHP file", target)
        assertEquals("MyThing.php", target!!.name)
    }

    fun testNativeTagNotResolved() {
        // collection is a catalog tag, not a project file -> no reference target
        assertNull(resolveAt("{{ colle<caret>ction }}"))
    }

    fun testUnknownNameNotResolved() {
        assertNull(resolveAt("{{ totally_unkno<caret>wn }}"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AntlersDefinitionReferenceTest"` → FAIL. Sandbox: `./gradlew compileTestKotlin`.

- [ ] **Step 3: Extend the scanners with `find`**

In `TagScanner.kt`, add (keep the existing `scan`/`scanDir`/`camelToSnake`):

```kotlin
import com.intellij.openapi.vfs.VirtualFile

data class NavTarget(val file: VirtualFile, val offset: Int)
```

Then add a `find` to `TagScanner`:

```kotlin
    /** The PHP file + class-declaration offset for the custom tag named [snakeName], or null. */
    fun find(project: Project, snakeName: String): NavTarget? = findInDir(project, "/Tags/", CLASS_PATTERN, snakeName)
```

Make `CLASS_PATTERN` accessible to the shared helper (it already is a private val — change it so `findInDir` can use the right pattern; pass the pattern in). Add the shared `findInDir` to `TagScanner` (so `ModifierScanner` can reuse it):

```kotlin
    internal fun findInDir(project: Project, dirMarker: String, pattern: java.util.regex.Pattern, snakeName: String): NavTarget? {
        try {
            val phpFiles = FilenameIndex.getAllFilesByExt(project, "php", GlobalSearchScope.projectScope(project))
            for (file in phpFiles) {
                if (!file.path.contains(dirMarker)) continue
                try {
                    val text = VfsUtilCore.loadText(file)
                    val matcher = pattern.matcher(text)
                    if (matcher.find() && camelToSnake(matcher.group(1)) == snakeName) {
                        return NavTarget(file, matcher.start(1))
                    }
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) { /* ignore */ }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) { /* index not ready */ }
        return null
    }
```

(Reference `TagScanner.CLASS_PATTERN` — to expose it, change `private val CLASS_PATTERN` to `internal val CLASS_PATTERN` in `TagScanner`. The `find` above passes `CLASS_PATTERN`.)

In `ModifierScanner.kt`, add:

```kotlin
    fun find(project: Project, snakeName: String): NavTarget? =
        TagScanner.findInDir(project, "/Modifiers/", MODIFIER_PATTERN, snakeName)
```

and expose its pattern as `internal val MODIFIER_PATTERN` (rename the existing `CLASS_PATTERN` in `ModifierScanner`).

- [ ] **Step 4: Create the reference**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersPhpClassReference.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.catalog.scan.NavTarget
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/** Resolves a custom tag/modifier name to the PHP class file the scanner found. */
class AntlersPhpClassReference(
    element: PsiElement,
    private val target: NavTarget
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)) {

    override fun resolve(): PsiElement? {
        val psiFile = PsiManager.getInstance(element.project).findFile(target.file) ?: return null
        return psiFile.findElementAt(target.offset) ?: psiFile
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
```

- [ ] **Step 5: Create the contributor**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceContributor.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.catalog.scan.ModifierScanner
import com.github.balotias.intellijantlers.catalog.scan.TagScanner
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

/** Go-to-definition from a custom tag head or modifier name to its PHP class file. */
class AntlersDefinitionReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement(AntlersTypes.T_IDENT),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val name = element.text
                    // Modifier name
                    PsiTreeUtil.getParentOfType(element, AntlersModifierMixin::class.java)?.let { mod ->
                        if (mod.modifierName == name) {
                            val t = ModifierScanner.find(element.project, name) ?: return emptyArray()
                            return arrayOf(AntlersPhpClassReference(element, t))
                        }
                    }
                    // Tag head (first identifier of the name path)
                    val path = PsiTreeUtil.getParentOfType(element, AntlersNamePathMixin::class.java) ?: return emptyArray()
                    if (path.head != name) return emptyArray()
                    val t = TagScanner.find(element.project, name) ?: return emptyArray()
                    return arrayOf(AntlersPhpClassReference(element, t))
                }
            }
        )
    }
}
```

- [ ] **Step 6: Register in plugin.xml**

Add inside `<extensions>`:

```xml
        <psi.referenceContributor language="Antlers" implementation="com.github.balotias.intellijantlers.references.AntlersDefinitionReferenceContributor"/>
```

- [ ] **Step 7: Verify + commit**

Run: `./gradlew test --tests "*AntlersDefinitionReferenceTest"` → PASS. Sandbox: `./gradlew compileTestKotlin`.

```bash
git add -A
git commit -m "Go-to-definition from custom tags/modifiers to their PHP class file"
```

---

## Task 4: Full build, regression, review

- [ ] **Step 1: Full suite**

Run: `./gradlew test` → all green (the A+B tests plus the 3 new C test classes). Sandbox: `./gradlew compileKotlin compileTestKotlin`.

- [ ] **Step 2: Manual smoke (developer env)**

`./gradlew runIde`: hover a tag/modifier (quick docs), Ctrl-click a `{{ partial:src="..." }}` path (navigates), Ctrl-click a custom `{{ tag }}` (navigates to PHP).

- [ ] **Step 3: Commit any touch-ups**

```bash
git add -A
git commit -m "Antlers semantic layer complete (sub-project C: docs + navigation)" || echo "nothing to commit"
```

---

## Self-Review (against the spec)

- §3.1 documentation provider (tag/method/param/modifier from catalog, null for plain vars) → Task 1 ✓
- §3.2 partial reference resolve (`src=` + `:path`), completion, unresolved → Task 2 ✓
- §3.3 custom tag/modifier go-to-def (only for scanned customs; native/unknown → null) → Task 3 ✓
- §3.4 views-root locator (walk-up + project search) → Task 2 (`StatamicProject`) ✓
- §5 tests for each via `BasePlatformTestCase` → Tasks 1–3 ✓
- Platform-only (FilenameIndex/VFS, no PHP PSI) → Tasks 2–3 ✓

**Placeholder scan:** every step has complete code. **Type consistency:** `NavTarget(file, offset)` defined in `catalog/scan` and used by `AntlersPhpClassReference`; `TagScanner.find`/`ModifierScanner.find`/`findInDir` signatures consistent; reference classes use `PsiReferenceBase`; `AntlersCatalogService.tag(name)`/`modifiers()` match B.
```
