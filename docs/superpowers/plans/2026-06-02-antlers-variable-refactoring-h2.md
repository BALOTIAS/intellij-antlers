# Antlers Variable Refactoring (H2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give blueprint field handles full refactoring support — precise go-to-declaration, Find Usages, and Rename that propagates across the YAML `handle:` and every `{{ handle }}` / `{{ container:handle }}` / `{{ container.handle }}` usage in templates.

**Architecture:** Introduce a synthetic `AntlersFieldDeclaration` (a `FakePsiElement` + `PsiNamedElement`) built from a `BlueprintField`. Both `AntlersBlueprintFieldReference` and `AntlersBlueprintMemberReference` resolve to it (replacing today's `findElementAt(offset)`), giving the platform a stable, identity-bearing, renamable target. A `ReferencesSearch` QueryExecutor finds usages; a `RenamePsiElementProcessor` drives rename (edits YAML via `setName`, rewrites usages via each reference's `handleElementRename`).

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`FakePsiElement`, `QueryExecutorBase`, `RenamePsiElementProcessor`, `FindUsagesProvider`), JUnit via `BasePlatformTestCase`. Tests run here with `./gradlew test` (use `--rerun-tasks` to force re-execution; the build cache returns cached results otherwise).

**Reference (do as H1 did):** `references/AntlersPartialReferenceSearcher.kt` is the searcher template; `references/AntlersPartialReference.kt` shows the `LeafPsiElement.replaceWithText(...).psi` rename mechanic.

**Critical invariant:** Every existing test that asserts `reference.resolve()?.containingFile` (e.g. `AntlersVariableNavTest`, `AntlersMemberNavTest`, `AntlersScopedNavTest`, `AntlersPageNavTest`, `AntlersRelationTest`, `AntlersContainerScopeTest`, `AntlersDefinitionReferenceTest`) must stay green. Therefore `AntlersFieldDeclaration.getContainingFile()` MUST return the blueprint `.yaml` PSI file, and `resolve()` must still return `null` for unknown handles.

---

### Task 1: `AntlersFieldDeclaration` synthetic element

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersFieldDeclaration.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersFieldDeclarationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFieldDeclarationTest : BasePlatformTestCase() {

    private fun addBlueprint(handle: String) = myFixture.addFileToProject(
        "resources/blueprints/collections/blog/blog.yaml",
        "fields:\n  - handle: $handle\n    field:\n      type: text\n      display: X\n"
    )

    private fun fieldDecl(handle: String): AntlersFieldDeclaration {
        val field = BlueprintService.getInstance(project).field(handle) ?: error("field not scanned")
        return AntlersFieldDeclaration(project, field)
    }

    fun testNameOffsetAndContainingFile() {
        val bp = addBlueprint("hero_title")
        val decl = fieldDecl("hero_title")
        assertEquals("hero_title", decl.name)
        assertEquals(bp.text.indexOf("hero_title"), decl.textOffset)
        assertEquals("blog.yaml", decl.containingFile?.name)
        assertTrue(decl.isValid)
    }

    fun testEquality() {
        addBlueprint("hero_title")
        assertEquals(fieldDecl("hero_title"), fieldDecl("hero_title"))
        assertTrue(fieldDecl("hero_title").isEquivalentTo(fieldDecl("hero_title")))
    }

    fun testSetNameEditsYaml() {
        val bp = addBlueprint("hero_title")
        val decl = fieldDecl("hero_title")
        WriteCommandAction.runWriteCommandAction(project) { decl.setName("hero_subtitle") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue("yaml handle renamed: ${bp.text}", bp.text.contains("handle: hero_subtitle"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersFieldDeclarationTest"`
Expected: FAIL — `AntlersFieldDeclaration` does not exist (compile error).

- [ ] **Step 3: Write the implementation**

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.IncorrectOperationException

/**
 * Synthetic, renamable declaration for a blueprint field handle. Both blueprint references resolve
 * to this so Find Usages and Rename have a stable, identity-bearing target. Identity is by
 * (handle, namespace, file, offset); navigation lands precisely on the YAML `handle:` token.
 */
class AntlersFieldDeclaration(
    private val project: Project,
    val handle: String,
    val namespace: BlueprintNamespace,
    val file: VirtualFile,
    val offset: Int
) : FakePsiElement(), PsiNamedElement {

    constructor(project: Project, field: BlueprintField) :
        this(project, field.handle, field.namespace, field.file, field.offset)

    override fun getParent(): PsiElement? = containingFile
    override fun getContainingFile(): PsiFile? = PsiManager.getInstance(project).findFile(file)
    override fun getProject(): Project = project
    override fun getName(): String = handle
    override fun getTextOffset(): Int = offset
    override fun getTextRange(): TextRange = TextRange(offset, offset + handle.length)
    override fun isValid(): Boolean = file.isValid
    override fun getLanguage(): Language = AntlersLanguage.INSTANCE

    override fun setName(newName: String): PsiElement {
        val doc = FileDocumentManager.getInstance().getDocument(file)
            ?: throw IncorrectOperationException("no document for ${file.name}")
        doc.replaceString(offset, offset + handle.length, newName)
        PsiDocumentManager.getInstance(project).commitDocument(doc)
        return AntlersFieldDeclaration(project, newName, namespace, file, offset)
    }

    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(project, file, offset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = file.isValid
    override fun canNavigateToSource(): Boolean = file.isValid

    override fun isEquivalentTo(another: PsiElement?): Boolean = this == another

    override fun equals(other: Any?): Boolean =
        other is AntlersFieldDeclaration &&
            handle == other.handle && namespace == other.namespace &&
            file == other.file && offset == other.offset

    override fun hashCode(): Int =
        ((handle.hashCode() * 31 + namespace.hashCode()) * 31 + file.hashCode()) * 31 + offset
}
```

Note: `getLanguage()` returns `AntlersLanguage.INSTANCE` so the Antlers `FindUsagesProvider` (registered for language "Antlers", extended in Task 3) is the handler selected for this element when Find Usages / Rename runs.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersFieldDeclarationTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersFieldDeclaration.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersFieldDeclarationTest.kt
git commit -m "Add AntlersFieldDeclaration synthetic blueprint-field element"
```

---

### Task 2: Resolve references to the declaration + isReferenceTo + precise nav

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintFieldReference.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintMemberReference.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersVariableRefactoringTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersVariableRefactoringTest : BasePlatformTestCase() {

    private fun addBlueprint(handle: String) = myFixture.addFileToProject(
        "resources/blueprints/collections/blog/blog.yaml",
        "fields:\n  - handle: $handle\n    field:\n      type: text\n      display: X\n"
    )

    private fun addTemplate(name: String, text: String): PsiFile =
        myFixture.addFileToProject("resources/views/$name.antlers.html", text)

    private fun declAt(file: PsiFile, caret: Int): AntlersFieldDeclaration =
        file.findReferenceAt(caret)?.resolve() as? AntlersFieldDeclaration
            ?: error("no AntlersFieldDeclaration at $caret")

    fun testResolvesToDeclarationWithPreciseOffset() {
        val bp = addBlueprint("hero_title")
        val file = addTemplate("page", "{{ hero_title }}")
        val decl = declAt(file, "{{ hero_title }}".indexOf("hero"))
        assertEquals("hero_title", decl.handle)
        assertEquals(bp.text.indexOf("hero_title"), decl.textOffset)
        assertEquals("blog.yaml", decl.containingFile?.name)
    }

    fun testIsReferenceToMatchesSameHandle() {
        addBlueprint("hero_title")
        val a = addTemplate("a", "{{ hero_title }}")
        val b = addTemplate("b", "{{ hero_title }}")
        val declA = declAt(a, "{{ hero_title }}".indexOf("hero"))
        val refB = b.findReferenceAt("{{ hero_title }}".indexOf("hero"))!!
        assertTrue(refB.isReferenceTo(declA))
    }

    fun testUnknownHandleStillNull() {
        addBlueprint("hero_title")
        val file = addTemplate("c", "{{ totally_unknown }}")
        assertNull(file.findReferenceAt("{{ totally_unknown }}".indexOf("tot"))?.resolve())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersVariableRefactoringTest"`
Expected: FAIL — `resolve()` returns a plain-text leaf, not `AntlersFieldDeclaration`; `declAt` errors.

- [ ] **Step 3: Update `AntlersBlueprintFieldReference.kt`**

Replace the whole class body with:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafPsiElement

/** Soft reference from a `{{ }}` variable to its blueprint field declaration, scope/page aware. */
class AntlersBlueprintFieldReference(
    element: PsiElement,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val field = AntlersFieldContext.resolveField(element, handle, element.project) ?: return null
        return AntlersFieldDeclaration(element.project, field)
    }

    override fun isReferenceTo(target: PsiElement): Boolean {
        if (target !is AntlersFieldDeclaration) return false
        val field = AntlersFieldContext.resolveField(element, handle, element.project) ?: return false
        return field.handle == target.handle && field.namespace == target.namespace
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val leaf = element as? LeafPsiElement ?: return element
        return leaf.replaceWithText(newElementName).psi
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
```

- [ ] **Step 4: Update `AntlersBlueprintMemberReference.kt`**

Replace the whole class body with:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafPsiElement

/** Soft reference from a dotted/colon sub-field segment to its blueprint declaration. */
class AntlersBlueprintMemberReference(
    element: PsiElement,
    private val namespace: BlueprintNamespace,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val field = BlueprintService.getInstance(element.project).fieldsFor(namespace)
            .firstOrNull { it.handle == handle } ?: return null
        return AntlersFieldDeclaration(element.project, field)
    }

    override fun isReferenceTo(target: PsiElement): Boolean =
        target is AntlersFieldDeclaration && handle == target.handle && namespace == target.namespace

    override fun handleElementRename(newElementName: String): PsiElement {
        val leaf = element as? LeafPsiElement ?: return element
        return leaf.replaceWithText(newElementName).psi
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
```

- [ ] **Step 5: Run the new test + the existing nav suite to verify all pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersVariableRefactoringTest" --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableNavTest" --tests "com.github.balotias.intellijantlers.scope.AntlersMemberNavTest" --tests "com.github.balotias.intellijantlers.scope.AntlersScopedNavTest" --tests "com.github.balotias.intellijantlers.scope.AntlersRelationTest" --tests "com.github.balotias.intellijantlers.references.AntlersDefinitionReferenceTest"`
Expected: PASS. The nav tests rely on `resolve()?.containingFile` → `blog.yaml`/`news.yaml`, which `getContainingFile()` still returns.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintFieldReference.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintMemberReference.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersVariableRefactoringTest.kt
git commit -m "Resolve blueprint references to AntlersFieldDeclaration (precise nav + isReferenceTo)"
```

---

### Task 3: Find Usages — searcher + provider wiring

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersFieldReferenceSearcher.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersFindUsagesProvider.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: append to `AntlersVariableRefactoringTest.kt`

- [ ] **Step 1: Write the failing test (append methods to `AntlersVariableRefactoringTest`)**

```kotlin
    fun testFindUsagesTopLevelField() {
        addBlueprint("hero_title")
        val file = addTemplate("page", "{{ hero_title }}")
        val decl = declAt(file, "{{ hero_title }}".indexOf("hero"))
        val usages = myFixture.findUsages(decl)
        assertTrue("expected a usage: ${usages.map { it.element?.text }}", usages.isNotEmpty())
    }

    fun testFindUsagesDistinctByHandle() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n" +
                "  - handle: subtitle\n    field:\n      type: text\n"
        )
        val file = addTemplate("page", "{{ title }} {{ subtitle }}")
        val titleDecl = declAt(file, "{{ title }}".indexOf("title"))
        val usages = myFixture.findUsages(titleDecl)
        assertEquals("only the title usage, not subtitle: ${usages.map { it.element?.text }}",
            1, usages.size)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersVariableRefactoringTest"`
Expected: FAIL — `findUsages` throws "cannot find handler" (no searcher / provider does not claim the element).

- [ ] **Step 3: Create `AntlersFieldReferenceSearcher.kt`**

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersFileType
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * Makes ReferencesSearch find blueprint-field references when the target is an
 * AntlersFieldDeclaration. Iterates Antlers files and reports field/member references that resolve
 * to the same (handle, namespace).
 */
class AntlersFieldReferenceSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val target = params.elementToSearch as? AntlersFieldDeclaration ?: return
        val project = params.project
        val scope = params.effectiveSearchScope as? GlobalSearchScope
            ?: GlobalSearchScope.allScope(project)
        val psiManager = PsiManager.getInstance(project)
        FileTypeIndex.getFiles(AntlersFileType.INSTANCE, scope).forEach { vf ->
            val psiFile = psiManager.findFile(vf) ?: return@forEach
            psiFile.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    for (ref in element.references) {
                        if ((ref is AntlersBlueprintFieldReference || ref is AntlersBlueprintMemberReference) &&
                            ref.isReferenceTo(target)
                        ) {
                            consumer.process(ref)
                        }
                    }
                }
            })
        }
    }
}
```

- [ ] **Step 4: Update `AntlersFindUsagesProvider.kt`** so it claims the declaration

Change exactly these two methods (leave the words scanner and the rest unchanged):

```kotlin
    override fun canFindUsagesFor(element: PsiElement): Boolean = element is AntlersFieldDeclaration
    override fun getType(element: PsiElement): String =
        if (element is AntlersFieldDeclaration) "blueprint field" else ""
```

- [ ] **Step 5: Register the searcher in `plugin.xml`**

Add directly below the existing `AntlersPartialReferenceSearcher` line (the `<referencesSearch>` near the end of `<extensions>`):

```xml
        <referencesSearch implementation="com.github.balotias.intellijantlers.references.AntlersFieldReferenceSearcher"/>
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersVariableRefactoringTest"`
Expected: PASS (5 tests now).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersFieldReferenceSearcher.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersFindUsagesProvider.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersVariableRefactoringTest.kt
git commit -m "Find Usages for blueprint fields (searcher + provider claims declaration)"
```

---

### Task 4: Rename processor + end-to-end rename

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersFieldRenameProcessor.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: append to `AntlersVariableRefactoringTest.kt`

- [ ] **Step 1: Write the failing tests (append to `AntlersVariableRefactoringTest`)**

```kotlin
    private fun commit() =
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()

    fun testRenameFieldUpdatesUsagesAndYaml() {
        val bp = addBlueprint("hero_title")
        val tmpl = addTemplate("page", "{{ hero_title }}")
        val decl = declAt(tmpl, "{{ hero_title }}".indexOf("hero"))
        myFixture.renameElement(decl, "hero_subtitle")
        commit()
        assertEquals("{{ hero_subtitle }}", tmpl.text)
        assertTrue("yaml updated: ${bp.text}", bp.text.contains("handle: hero_subtitle"))
    }

    fun testRenameNestedMember() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            """
            fields:
              - handle: hero
                field:
                  type: group
                  fields:
                    - handle: subhead
                      field:
                        type: text
            """.trimIndent()
        )
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        val tmpl = myFixture.addFileToProject(
            "resources/views/blog/show.antlers.html", "{{ hero.subhead }}"
        ) as PsiFile
        myFixture.configureFromExistingVirtualFile(tmpl.virtualFile)
        val decl = declAt(tmpl, "{{ hero.subhead }}".indexOf("subhead"))
        myFixture.renameElement(decl, "subtitle")
        commit()
        assertEquals("{{ hero.subtitle }}", tmpl.text)
    }

    fun testRenameScopedFieldLeavesUnrelatedSameHandleAlone() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - handle: title\n    field:\n      type: text\n"
        )
        val tmpl = addTemplate(
            "page",
            "{{ collection:news }}{{ title }}{{ /collection }}" +
                "{{ collection:blog }}{{ title }}{{ /collection }}"
        )
        myFixture.configureFromExistingVirtualFile(tmpl.virtualFile)
        // caret on the news-scoped title (first occurrence)
        val newsTitleCaret = tmpl.text.indexOf("title")
        val decl = declAt(tmpl, newsTitleCaret)
        myFixture.renameElement(decl, "headline")
        commit()
        // news usage renamed, blog usage untouched
        assertTrue("news usage renamed: ${tmpl.text}",
            tmpl.text.contains("{{ collection:news }}{{ headline }}{{ /collection }}"))
        assertTrue("blog usage untouched: ${tmpl.text}",
            tmpl.text.contains("{{ collection:blog }}{{ title }}{{ /collection }}"))
    }

    fun testRenameFieldsetFieldUpdatesAllImportingTemplates() {
        myFixture.addFileToProject(
            "resources/fieldsets/seo.yaml",
            "fields:\n  - handle: meta_title\n    field:\n      type: text\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - import: seo\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - import: seo\n"
        )
        val t1 = addTemplate("a", "{{ meta_title }}")
        val t2 = addTemplate("b", "{{ meta_title }}")
        val decl = declAt(t1, "{{ meta_title }}".indexOf("meta"))
        myFixture.renameElement(decl, "seo_title")
        commit()
        assertEquals("{{ seo_title }}", t1.text)
        assertEquals("{{ seo_title }}", t2.text)
    }
```

Add the import `import com.intellij.psi.PsiFile` if not already present (it is, from Task 2).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersVariableRefactoringTest"`
Expected: FAIL — without the processor, `renameElement` on the fake element either renames nothing in the template (soft refs skipped) or throws. Capture the exact failure to confirm the root cause before implementing (systematic debugging: read the message).

- [ ] **Step 3: Create `AntlersFieldRenameProcessor.kt`**

```kotlin
package com.github.balotias.intellijantlers.references

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenamePsiElementProcessor

/**
 * Claims AntlersFieldDeclaration for rename and force-collects its (soft) references through
 * ReferencesSearch so every `{{ handle }}` usage is renamed. The default renameElement then edits
 * the YAML via the element's setName and rewrites each usage via its handleElementRename.
 */
class AntlersFieldRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean =
        element is AntlersFieldDeclaration

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> =
        ReferencesSearch.search(element, searchScope).findAll()
}
```

Note: if the installed SDK exposes only the 2-arg `findReferences(element, searchInCommentsAndStrings)` signature, override that overload instead and use `ReferencesSearch.search(element).findAll()`. Match whichever signature compiles against this SDK.

- [ ] **Step 4: Register the processor in `plugin.xml`**

Add below the `AntlersFieldReferenceSearcher` line (inside `<extensions>`):

```xml
        <renamePsiElementProcessor implementation="com.github.balotias.intellijantlers.references.AntlersFieldRenameProcessor"/>
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew --rerun-tasks test --tests "com.github.balotias.intellijantlers.references.AntlersVariableRefactoringTest"`
Expected: PASS (9 tests).

If `testRenameNestedMember` or the scoped test still fails, debug systematically: print `myFixture.findUsages(decl)` to confirm the searcher returns the expected references, and verify `isReferenceTo` namespace equality for the member case before changing the processor. Do NOT add speculative fixes. If, after root-cause investigation, end-to-end rename for the member/scoped path proves intractable on this SDK, fall back to the spec's documented position: keep Find Usages + precise go-to-declaration green, mark the affected rename test(s) with a `// KNOWN LIMITATION` comment and an explanation, and surface this to the controller rather than forcing it.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersFieldRenameProcessor.kt \
        src/main/resources/META-INF/plugin.xml \
        src/test/kotlin/com/github/balotias/intellijantlers/references/AntlersVariableRefactoringTest.kt
git commit -m "Rename blueprint fields across YAML + template usages (rename processor)"
```

---

### Task 5: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the entire test suite, forcing re-execution**

Run: `./gradlew --rerun-tasks test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Gate on zero failures across all result XMLs**

Run: `ls build/test-results/test/*.xml >/dev/null 2>&1 && echo "files=$(ls build/test-results/test/*.xml | wc -l)" && echo "failed_files=$(grep -lo 'failures=\"[1-9]' build/test-results/test/*.xml | wc -l)" && grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc | xargs echo "total_tests="`
Expected: `failed_files=0`. Note the `total_tests` count (should be the prior 190 plus the new ~15 H2 tests).

- [ ] **Step 3: Also confirm `compileTestKotlin` is clean (sandbox-independent compile check)**

Run: `./gradlew compileTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (only if any cleanup was needed; otherwise skip)**

```bash
git commit -am "H2 variable refactoring: full suite green" || echo "nothing to commit"
```

---

## Self-Review (filled in by plan author)

**Spec coverage:**
- Synthetic `AntlersFieldDeclaration` (RenameableFakePsiElement intent → realized as `FakePsiElement` + `PsiNamedElement`, equivalent and simpler) → Task 1.
- Both references resolve to it + `isReferenceTo` + precise nav → Task 2.
- `AntlersFieldReferenceSearcher` (`<referencesSearch>`) + Find Usages → Task 3.
- `AntlersFieldRenameProcessor` (`<renamePsiElementProcessor>`) + cross-language rename → Task 4.
- Edge cases: fieldset-imported (`testRenameFieldsetFieldUpdatesAllImportingTemplates`), same-handle-different-namespace (`testRenameScopedFieldLeavesUnrelatedSameHandleAlone`, `testFindUsagesDistinctByHandle`), nested members (`testRenameNestedMember`), precise nav (`testResolvesToDeclarationWithPreciseOffset`) → covered.
- Risk/fallback (soft-ref rename) → documented in Task 4 Step 5.
- Out-of-scope (YAML-side rename, YAML dependency) → not implemented, by design.

**Placeholder scan:** none — every code step shows full code; commands have expected output.

**Type consistency:** `AntlersFieldDeclaration(project, field)` constructor used in Tasks 1–3; `handle`/`namespace`/`offset`/`textOffset` property names consistent across the class, references, searcher, and processor. `isReferenceTo` signature matches `PsiReference`. `getContainingFile()` returns the blueprint file in every place the nav tests depend on it.

**Known SDK-shape risk flagged inline:** `findReferences` overload signature (Task 4 Step 3 note) — the implementer matches whichever overload compiles against this SDK.
