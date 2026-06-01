# Antlers Blueprint Scoping E3b — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support member access after `.`/`:` — `{{ hero.headline }}`, `{{ group:sub }}`, `{{ hero_image.url }}` — by resolving the path so far to a field and offering its blueprint sub-fields (E3a tree) plus curated fieldtype augmentation properties; with go-to-def + docs.

**Architecture:** A `T_DOT` classifier branch produces a `FIELD_PATH` kind carrying the `pathPrefix` (segments before the caret, by offset). `AntlersMemberResolver` walks the path to a field (segment 0 scope-aware via `AntlersFieldContext`, deeper segments down the E3a sub-field tree). Member completion offers `fieldsFor(childNamespace)` + `FieldtypeProperties.forType(field.type)`. A soft `AntlersBlueprintMemberReference` and generalized docs handle non-head segments.

**Tech Stack:** Kotlin, IntelliJ Platform PSI, JUnit `BasePlatformTestCase` (runs via `./gradlew test`).

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-blueprint-scope-e3b-design.md`

**TDD note:** This environment runs `BasePlatformTestCase` via `./gradlew test`. One class:
`./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`.
Full suite is currently **116/116 green**. Keep it green.

---

## Task 1: Fieldtype augmentation-property catalog

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/catalog/FieldtypeProperties.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/catalog/FieldtypePropertiesTest.kt` (new)

- [ ] **Step 1: Write the failing catalog test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/catalog/FieldtypePropertiesTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldtypePropertiesTest {

    @Test fun assetAliasMatchesAssets() {
        assertEquals(FieldtypeProperties.forType("asset"), FieldtypeProperties.forType("assets"))
    }

    @Test fun assetsHasUrl() {
        assertTrue(FieldtypeProperties.forType("assets").any { it.name == "url" })
    }

    @Test fun entriesHasTitleAndUrl() {
        val names = FieldtypeProperties.forType("entries").map { it.name }
        assertTrue(names.contains("title"))
        assertTrue(names.contains("url"))
    }

    @Test fun unknownTypeIsEmpty() {
        assertTrue(FieldtypeProperties.forType("text").isEmpty())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.catalog.FieldtypePropertiesTest" --no-configuration-cache`
Expected: FAIL — unresolved reference `FieldtypeProperties`.

- [ ] **Step 3: Create the catalog**

Create `src/main/kotlin/com/github/balotias/intellijantlers/catalog/FieldtypeProperties.kt`:

```kotlin
package com.github.balotias.intellijantlers.catalog

/** A property exposed by a fieldtype's augmentation (e.g. `url` on an asset), for dotted access. */
data class FieldProperty(val name: String, val description: String)

/** Curated augmentation properties per Statamic fieldtype. Kotlin data (no JSON parser at runtime). */
object FieldtypeProperties {

    private val ASSETS = listOf(
        FieldProperty("url", "The asset's URL."),
        FieldProperty("permalink", "The absolute URL of the asset."),
        FieldProperty("path", "The path relative to the container."),
        FieldProperty("alt", "The alt text."),
        FieldProperty("title", "The asset's title."),
        FieldProperty("basename", "The filename with extension."),
        FieldProperty("filename", "The filename without extension."),
        FieldProperty("extension", "The file extension."),
        FieldProperty("size", "The human-readable file size."),
        FieldProperty("size_bytes", "The file size in bytes."),
        FieldProperty("is_image", "Whether the asset is an image."),
        FieldProperty("width", "The image width in pixels."),
        FieldProperty("height", "The image height in pixels."),
        FieldProperty("mime_type", "The MIME type."),
        FieldProperty("last_modified", "When the asset was last modified.")
    )
    private val ENTRIES = listOf(
        FieldProperty("id", "The entry's id."),
        FieldProperty("title", "The entry's title."),
        FieldProperty("slug", "The entry's slug."),
        FieldProperty("url", "The entry's URL."),
        FieldProperty("permalink", "The absolute URL."),
        FieldProperty("date", "The entry's date."),
        FieldProperty("status", "draft, published, or scheduled."),
        FieldProperty("published", "Whether the entry is published."),
        FieldProperty("author", "The entry's author."),
        FieldProperty("edit_url", "The control-panel edit URL.")
    )
    private val USERS = listOf(
        FieldProperty("id", "The user's id."),
        FieldProperty("name", "The user's name."),
        FieldProperty("email", "The user's email."),
        FieldProperty("avatar", "The user's avatar."),
        FieldProperty("initials", "The user's initials."),
        FieldProperty("is_admin", "Whether the user is a super admin."),
        FieldProperty("last_login", "When the user last logged in."),
        FieldProperty("edit_url", "The control-panel edit URL.")
    )
    private val TERMS = listOf(
        FieldProperty("id", "The term's id."),
        FieldProperty("title", "The term's title."),
        FieldProperty("slug", "The term's slug."),
        FieldProperty("url", "The term's URL."),
        FieldProperty("permalink", "The absolute URL."),
        FieldProperty("entries_count", "Number of entries with this term.")
    )
    private val LINK = listOf(
        FieldProperty("url", "The link URL."),
        FieldProperty("title", "The link title."),
        FieldProperty("element", "The rendered anchor element.")
    )
    private val DATE = listOf(
        FieldProperty("timestamp", "The Unix timestamp."),
        FieldProperty("iso", "The ISO-8601 string."),
        FieldProperty("day", "The day of the month."),
        FieldProperty("month", "The month number."),
        FieldProperty("year", "The year.")
    )

    /** Curated properties for an augmenting fieldtype; empty for types without dotted augmentation. */
    fun forType(type: String): List<FieldProperty> = when (type.lowercase()) {
        "assets", "asset" -> ASSETS
        "entries", "entry" -> ENTRIES
        "users", "user" -> USERS
        "terms", "term", "taxonomy" -> TERMS
        "link" -> LINK
        "date" -> DATE
        else -> emptyList()
    }
}
```

- [ ] **Step 4: Run the catalog test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.catalog.FieldtypePropertiesTest" --no-configuration-cache`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/catalog/FieldtypeProperties.kt src/test/kotlin/com/github/balotias/intellijantlers/catalog/FieldtypePropertiesTest.kt
git commit -m "Add curated fieldtype augmentation-property catalog"
```

---

## Task 2: namePath segment helpers + classifier + member resolver

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt`
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolver.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContextTest.kt` (extend; create if absent)

- [ ] **Step 1: Write the failing classifier tests**

Add to (or create) `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContextTest.kt`. If creating it, use this full file; if it exists, add the two `testDot…`/`testColon…` methods inside the class.

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionContextTest : BasePlatformTestCase() {

    private fun classifyAt(text: String): AntlersCompletionInfo {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret) ?: error("no element at caret")
        return AntlersCompletionContext.classify(el)
    }

    fun testDotProducesFieldPath() {
        val info = classifyAt("{{ a.b.<caret> }}")
        assertEquals(AntlersCompletionKind.FIELD_PATH, info.kind)
        assertEquals(listOf("a", "b"), info.pathPrefix)
    }

    fun testColonCarriesPathPrefix() {
        val info = classifyAt("{{ group:<caret> }}")
        assertEquals(AntlersCompletionKind.TAG_METHOD, info.kind)
        assertEquals(listOf("group"), info.pathPrefix)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.completion.AntlersCompletionContextTest" --no-configuration-cache`
Expected: FAIL — `FIELD_PATH`/`pathPrefix` don't exist; T_DOT returns NONE.

- [ ] **Step 3: Add segment helpers to `AntlersNamePathMixin`**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt`. Inside `class AntlersNamePathMixin`, after the `pathText` property, add:

```kotlin
    /** All identifier segments in order, e.g. ["a","b","c"] for `a.b.c`. */
    val segments: List<String>
        get() = node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }.map { it.text }

    /** Identifier segment texts whose token starts before [offset] (the segments preceding the caret). */
    fun segmentsBefore(offset: Int): List<String> =
        node.getChildren(null)
            .filter { it.elementType == AntlersTypes.T_IDENT && it.startOffset < offset }
            .map { it.text }
```

- [ ] **Step 4: Add the `FIELD_PATH` kind, `pathPrefix`, and the `T_DOT` branch**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt`.

Change the enum line:

```kotlin
enum class AntlersCompletionKind { TAG_NAME, TAG_METHOD, PARAMETER, MODIFIER, NONE }
```

to:

```kotlin
enum class AntlersCompletionKind { TAG_NAME, TAG_METHOD, PARAMETER, MODIFIER, FIELD_PATH, NONE }
```

Change the info data class:

```kotlin
data class AntlersCompletionInfo(val kind: AntlersCompletionKind, val tagHead: String? = null)
```

to:

```kotlin
data class AntlersCompletionInfo(
    val kind: AntlersCompletionKind,
    val tagHead: String? = null,
    val pathPrefix: List<String> = emptyList()
)
```

In `classify`, add a `T_DOT` branch and enrich `T_COLON`. Replace this block:

```kotlin
            AntlersTypes.T_COLON ->
                headOf(statement)?.let { AntlersCompletionInfo(AntlersCompletionKind.TAG_METHOD, it) }
                    ?: AntlersCompletionInfo(AntlersCompletionKind.NONE)
```

with:

```kotlin
            AntlersTypes.T_DOT ->
                AntlersCompletionInfo(AntlersCompletionKind.FIELD_PATH, pathPrefix = segmentsBeforeCaret(statement, position))

            AntlersTypes.T_COLON ->
                headOf(statement)?.let {
                    AntlersCompletionInfo(AntlersCompletionKind.TAG_METHOD, it, segmentsBeforeCaret(statement, position))
                } ?: AntlersCompletionInfo(AntlersCompletionKind.NONE)
```

Add the helper next to `headOf`:

```kotlin
    private fun segmentsBeforeCaret(statement: AntlersStatement, position: PsiElement): List<String> {
        val namePath = PsiTreeUtil.findChildOfType(statement, AntlersNamePathMixin::class.java) ?: return emptyList()
        return namePath.segmentsBefore(position.textRange.startOffset)
    }
```

- [ ] **Step 5: Create the member resolver**

Create `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolver.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Resolves a dotted/colon path (e.g. [hero, bio]) to the field it names, walking the E3a sub-field tree. */
object AntlersMemberResolver {

    /** The namespace whose fields are [field]'s direct children (its sub-fields). */
    fun childNamespace(field: BlueprintField): BlueprintNamespace =
        field.namespace.copy(path = field.namespace.path + field.handle)

    /** The field that [pathPrefix] names at [element], or null if any segment doesn't resolve. */
    fun resolveField(element: PsiElement, pathPrefix: List<String>, project: Project): BlueprintField? {
        if (pathPrefix.isEmpty()) return null
        var current = AntlersFieldContext.resolveField(element, pathPrefix[0], project) ?: return null
        val svc = BlueprintService.getInstance(project)
        for (i in 1 until pathPrefix.size) {
            current = svc.fieldsFor(childNamespace(current)).firstOrNull { it.handle == pathPrefix[i] } ?: return null
        }
        return current
    }
}
```

- [ ] **Step 6: Run the classifier test — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.completion.AntlersCompletionContextTest" --no-configuration-cache`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/psi/AntlersMixins.kt src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContext.kt src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolver.kt src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionContextTest.kt
git commit -m "Classify member access (FIELD_PATH + pathPrefix) and add member resolver"
```

---

## Task 3: Member completion

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberCompletionTest.kt` (new)

- [ ] **Step 1: Write the failing member-completion tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberCompletionTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMemberCompletionTest : BasePlatformTestCase() {

    private val blueprint = """
        tabs:
          main:
            sections:
              - fields:
                  - handle: hero
                    field:
                      type: group
                      fields:
                        - handle: headline
                          field:
                            type: text
                            display: Headline
                        - handle: subhead
                          field:
                            type: text
                  - handle: hero_image
                    field:
                      type: assets
    """.trimIndent()

    private fun setup() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
    }

    private fun lookupsAt(path: String, textWithCaret: String): List<String> {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject(path, textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testGroupDotOffersSubFields() {
        setup()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ hero.<caret> }}")
        assertTrue("sub-field headline: $l", l.contains("headline"))
        assertTrue("sub-field subhead: $l", l.contains("subhead"))
        assertFalse("page sibling hero_image not offered: $l", l.contains("hero_image"))
    }

    fun testAssetsDotOffersProperties() {
        setup()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ hero_image.<caret> }}")
        assertTrue("asset url: $l", l.contains("url"))
        assertTrue("asset alt: $l", l.contains("alt"))
    }

    fun testColonOffersSubFields() {
        setup()
        val l = lookupsAt("resources/views/blog/show.antlers.html", "{{ hero:<caret> }}")
        assertTrue("colon group sub-field: $l", l.contains("headline"))
    }

    fun testDotInsideCollectionLoop() {
        setup()
        val l = lookupsAt("page.antlers.html", "{{ collection:blog }}{{ hero.<caret> }}{{ /collection }}")
        assertTrue("scope-aware seg 0: $l", l.contains("headline"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersMemberCompletionTest" --no-configuration-cache`
Expected: FAIL — no `FIELD_PATH` handling and `TAG_METHOD` offers nothing for a group.

- [ ] **Step 3: Add the member offering + branches**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`.

Add imports (next to the existing scope/catalog imports):

```kotlin
import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.catalog.FieldtypeProperties
import com.github.balotias.intellijantlers.scope.AntlersMemberResolver
```

Replace the `TAG_METHOD` branch:

```kotlin
            AntlersCompletionKind.TAG_METHOD ->
                catalog.tag(info.tagHead ?: "")?.methods?.forEach { m ->
                    result.addElement(
                        LookupElementBuilder.create(m).withIcon(AntlersIcons.FILE).withTypeText("Method")
                    )
                }
```

with:

```kotlin
            AntlersCompletionKind.TAG_METHOD -> {
                val tag = catalog.tag(info.tagHead ?: "")
                if (tag != null) {
                    tag.methods.forEach { m ->
                        result.addElement(
                            LookupElementBuilder.create(m).withIcon(AntlersIcons.FILE).withTypeText("Method")
                        )
                    }
                } else {
                    // Not a catalog tag: a blueprint field member access via colon (e.g. {{ group:sub }}).
                    AntlersMemberResolver.resolveField(parameters.position, info.pathPrefix, project)
                        ?.let { offerMembers(it, project, result) }
                }
            }

            AntlersCompletionKind.FIELD_PATH ->
                AntlersMemberResolver.resolveField(parameters.position, info.pathPrefix, project)
                    ?.let { offerMembers(it, project, result) }
```

Add the `offerMembers` helper as a private method of the class (e.g. after `addCompletions`):

```kotlin
    private fun offerMembers(field: BlueprintField, project: com.intellij.openapi.project.Project, result: CompletionResultSet) {
        val seen = mutableSetOf<String>()
        for (sub in BlueprintService.getInstance(project).fieldsFor(AntlersMemberResolver.childNamespace(field))) {
            if (seen.add(sub.handle)) {
                result.addElement(
                    LookupElementBuilder.create(sub.handle)
                        .withIcon(AntlersIcons.FILE)
                        .withTypeText("Field")
                        .withTailText(if (sub.display.isNotBlank()) "  ${sub.display}" else null, true)
                )
            }
        }
        for (p in FieldtypeProperties.forType(field.type)) {
            if (seen.add(p.name)) {
                result.addElement(
                    LookupElementBuilder.create(p.name)
                        .withIcon(AntlersIcons.FILE)
                        .withTypeText("Property")
                        .withTailText("  ${p.description}", true)
                )
            }
        }
    }
```

- [ ] **Step 4: Run the member-completion tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersMemberCompletionTest" --no-configuration-cache`
Expected: PASS (4 tests).

- [ ] **Step 5: Run the completion regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopedCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersPageCompletionTest" --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersContainerScopeTest" --no-configuration-cache`
Expected: PASS — `TAG_NAME` and catalog-tag `TAG_METHOD` behavior unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberCompletionTest.kt
git commit -m "Offer blueprint sub-fields + augmentation properties after a dot/colon"
```

---

## Task 4: Member go-to-definition

**Files:**
- Create: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintMemberReference.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberNavTest.kt` (new)

- [ ] **Step 1: Write the failing nav tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberNavTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMemberNavTest : BasePlatformTestCase() {

    private val blueprint = """
        tabs:
          main:
            sections:
              - fields:
                  - handle: hero
                    field:
                      type: group
                      fields:
                        - handle: subhead
                          field:
                            type: text
                            display: Subhead
                  - handle: hero_image
                    field:
                      type: assets
    """.trimIndent()

    private fun setup() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
    }

    private fun resolveAt(textWithCaret: String): PsiFile? {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
    }

    fun testNavOnSubField() {
        setup()
        assertEquals("blog.yaml", resolveAt("{{ hero.sub<caret>head }}")?.name)
    }

    fun testNavOnAugmentationPropertyIsNull() {
        setup()
        assertNull(resolveAt("{{ hero_image.u<caret>rl }}"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersMemberNavTest" --no-configuration-cache`
Expected: FAIL — `subhead` (a non-head segment) gets no reference today, so it resolves to null.

- [ ] **Step 3: Create the member reference**

Create `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintMemberReference.kt`:

```kotlin
package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/** Soft reference from a dotted/colon sub-field segment to its blueprint declaration. */
class AntlersBlueprintMemberReference(
    element: PsiElement,
    private val namespace: BlueprintNamespace,
    private val handle: String
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {
    override fun resolve(): PsiElement? {
        val field = BlueprintService.getInstance(element.project).fieldsFor(namespace)
            .firstOrNull { it.handle == handle } ?: return null
        val psiFile = PsiManager.getInstance(element.project).findFile(field.file) ?: return null
        return psiFile.findElementAt(field.offset) ?: psiFile
    }
    override fun getVariants(): Array<Any> = emptyArray()
}
```

- [ ] **Step 4: Attach the member reference for non-head segments**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt`. Replace the final block:

```kotlin
        if (path.head == name &&
            path.node.findChildByType(AntlersTypes.T_IDENT)?.psi == element) {
            // Both refs are soft and coexist on the same range. Go-to-def/Ctrl-click go through
            // SharedPsiElementImplUtil.findReferenceAt, which wraps them in a PsiMultiReference and
            // picks the one that resolves non-null (PHP class for custom tags, YAML field for
            // blueprint variables). Single-ref consumers see only the first (PHP) ref.
            return arrayOf(
                AntlersPhpClassReference(element, name, isModifier = false),
                AntlersBlueprintFieldReference(element, name)
            )
        }

        return emptyArray()
```

with:

```kotlin
        val idents = path.node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }
        val index = idents.indexOfFirst { it.psi == element }

        if (index == 0) {
            // Head segment: PHP class ref + blueprint field ref (PsiMultiReference picks the resolver).
            return arrayOf(
                AntlersPhpClassReference(element, name, isModifier = false),
                AntlersBlueprintFieldReference(element, name)
            )
        }

        if (index > 0) {
            // Non-head dotted/colon segment: resolve the prefix to a field; if this segment is one of
            // its blueprint sub-fields, point at that sub-field's declaration. (Augmentation properties
            // have no declaration, so they get no reference.)
            val prefix = idents.take(index).map { it.text }
            val parent = com.github.balotias.intellijantlers.scope.AntlersMemberResolver
                .resolveField(element, prefix, element.project)
            if (parent != null) {
                val childNs = com.github.balotias.intellijantlers.scope.AntlersMemberResolver.childNamespace(parent)
                val hasMember = com.github.balotias.intellijantlers.blueprint.BlueprintService
                    .getInstance(element.project).fieldsFor(childNs).any { it.handle == name }
                if (hasMember) return arrayOf(AntlersBlueprintMemberReference(element, childNs, name))
            }
        }

        return emptyArray()
```

(The original check used `path.head == name && firstIdent == element`; the new `index == 0` is equivalent — the head is the first `T_IDENT` — and also handles the case where a later segment happens to share the head's text.)

- [ ] **Step 5: Run the nav tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersMemberNavTest" --no-configuration-cache`
Expected: PASS (2 tests).

- [ ] **Step 6: Run the nav/reference regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopedNavTest" --tests "com.github.balotias.intellijantlers.scope.AntlersPageNavTest" --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableNavTest" --tests "com.github.balotias.intellijantlers.scope.AntlersContainerScopeTest" --no-configuration-cache`
Expected: PASS — head-segment refs (PHP class + blueprint field) unchanged; existing nav tests green.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersBlueprintMemberReference.kt src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberNavTest.kt
git commit -m "Go-to-def on dotted/colon blueprint sub-field segments"
```

---

## Task 5: Member docs

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberDocTest.kt` (new)

- [ ] **Step 1: Write the failing docs tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberDocTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.documentation.AntlersDocumentationProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMemberDocTest : BasePlatformTestCase() {

    private val blueprint = """
        tabs:
          main:
            sections:
              - fields:
                  - handle: hero
                    field:
                      type: group
                      fields:
                        - handle: subhead
                          field:
                            type: text
                            display: Subhead
                  - handle: hero_image
                    field:
                      type: assets
    """.trimIndent()

    private fun setup() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
    }

    private fun docAt(textWithCaret: String): String? {
        val caret = textWithCaret.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", textWithCaret.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val el = myFixture.file.findElementAt(caret)!!
        return AntlersDocumentationProvider().generateDoc(el, el)
    }

    fun testDocOnSubField() {
        setup()
        val doc = docAt("{{ hero.sub<caret>head }}")
        assertNotNull(doc)
        assertTrue("names the hero container: $doc", doc!!.contains("hero"))
        assertTrue("shows the display: $doc", doc.contains("Subhead"))
    }

    fun testDocOnAugmentationProperty() {
        setup()
        val doc = docAt("{{ hero_image.u<caret>rl }}")
        assertNotNull(doc)
        assertTrue("names the assets type: $doc", doc!!.contains("assets"))
        assertTrue("shows the property description: $doc", doc.contains("URL"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersMemberDocTest" --no-configuration-cache`
Expected: FAIL — the second-segment branch only knows catalog tag methods today.

- [ ] **Step 3: Generalize the non-head namePath doc branch**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`.

Add imports (next to the existing ones):

```kotlin
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.catalog.FieldtypeProperties
import com.github.balotias.intellijantlers.scope.AntlersMemberResolver
```

Replace the method branch inside the namePath `let`:

```kotlin
            if (path.method == name) {
                val tag = catalog.tag(path.head) ?: return null
                return section(
                    "Method <b>${esc(name)}</b> of tag <code>${esc(tag.name)}</code>",
                    tag.description,
                    tag.docUrl
                )
            }
```

with:

```kotlin
            val idents = path.node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }
            val index = idents.indexOfFirst { it.psi == ident }
            if (index > 0) {
                val prefix = idents.take(index).map { it.text }
                val parent = AntlersMemberResolver.resolveField(ident, prefix, ident.project)
                if (parent != null) {
                    val childNs = AntlersMemberResolver.childNamespace(parent)
                    BlueprintService.getInstance(ident.project).fieldsFor(childNs)
                        .firstOrNull { it.handle == name }?.let { f ->
                            val type = if (f.type.isNotBlank()) " (${esc(f.type)})" else ""
                            val title = "Field <b>${esc(name)}</b>$type" +
                                (if (f.display.isNotBlank()) " — ${esc(f.display)}" else "") + namespaceLabel(f.namespace)
                            return section(title, f.display, "")
                        }
                    FieldtypeProperties.forType(parent.type).firstOrNull { it.name == name }?.let { p ->
                        return section("Property <b>${esc(name)}</b> · ${esc(parent.type)}", p.description, "")
                    }
                }
                // Fall back to the catalog tag-method doc (e.g. collection:count).
                val tag = catalog.tag(path.head) ?: return null
                return section(
                    "Method <b>${esc(name)}</b> of tag <code>${esc(tag.name)}</code>",
                    tag.description,
                    tag.docUrl
                )
            }
```

- [ ] **Step 4: Run the docs tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersMemberDocTest" --no-configuration-cache`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the docs regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersScopedNavTest" --tests "com.github.balotias.intellijantlers.blueprint.AntlersVariableNavTest" --no-configuration-cache`
Expected: PASS — head-segment field/system-var/tag docs and the catalog tag-method doc are unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberDocTest.kt
git commit -m "Docs for dotted/colon sub-fields and augmentation properties"
```

---

## Task 6: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew test --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm no failures**

Run: `grep -lo 'failures="[1-9]' build/test-results/test/*.xml | wc -l`
Expected: `0`.

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc`
Expected: **~131** (116 prior + ~15 new: 4 catalog + 2 classifier + 4 member-completion + 2 nav + 2 docs + extras). Exact count may differ; **0 failures** is the gate.

- [ ] **Step 3: Verify production compilation**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

---

## Notes for the implementer

- **No `plugin.xml`, grammar, or lexer changes.**
- `pathPrefix` is offset-based (segments whose ident starts before the caret), so it works both for live
  completion (where a dummy identifier sits at the caret) and for the static-file classifier test.
- Member completion (`FIELD_PATH`) offers ONLY Field/Property members — never tags or system variables;
  that's intentional (after a dot you're in member space). It's also why the member-completion test can
  assert a page sibling like `hero_image` is absent.
- `AntlersMemberResolver.resolveField` segment 0 goes through `AntlersFieldContext` (so it honors loop
  scope, page mapping, and global) — which is why `testDotInsideCollectionLoop` works without a page
  config, while the page-mapped tests work at top level.
- Nav/docs compute the prefix from the segment's index among the namePath's `T_IDENT` children (no
  dummy in a static file), then reuse `AntlersMemberResolver`.
- Augmentation properties get docs but no go-to-def (no declaration target) — `AntlersMemberNavTest`
  asserts the property segment resolves to null.
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
```
