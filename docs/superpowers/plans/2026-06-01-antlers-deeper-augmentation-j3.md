# Antlers Deeper Augmentation J3 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dotting into a relationship field (`entries`/`terms`/`users`/`assets`) reaches the linked blueprint's own fields — `{{ author.full_name }}` where `author` links collection `team` — with multi-hop and bracket access falling out.

**Architecture:** `BlueprintField` gains `linkedNamespaces` (captured by the scanner from `collections:`/`taxonomy:`/`container:`/`users`). `AntlersMemberResolver.childNamespace` becomes list-valued `childNamespaces` (container sub-fields OR linked blueprints); its three consumers (completion, references, docs) iterate it. Bracket access needs no code (`namePath.segments` already skips bracket nodes).

**Tech Stack:** Kotlin, IntelliJ Platform PSI, JUnit `BasePlatformTestCase`.

**Reference:** `docs/superpowers/specs/2026-06-01-antlers-deeper-augmentation-j3-design.md`

**TDD note:** `./gradlew test --tests "com.github.balotias.intellijantlers.<Class>" --no-configuration-cache`. Full suite is currently **150/150 green**. Keep it green.

---

## Task 1: Capture relationship links in the scanner

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt` (extend)

- [ ] **Step 1: Write the failing link-capture tests**

Add to `BlueprintScannerTest`:

```kotlin
    fun testRelationshipLinkCapture() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            """
            tabs:
              main:
                sections:
                  - fields:
                      - handle: author
                        field:
                          type: entries
                          collections:
                            - team
                      - handle: topics
                        field:
                          type: terms
                          taxonomy: tags
                      - handle: hero
                        field:
                          type: assets
                          container: images
                      - handle: editor
                        field:
                          type: users
            """.trimIndent()
        )
        val fields = BlueprintScanner.scan(project)
        fun linked(h: String) = fields.first { it.handle == h }.linkedNamespaces
        assertEquals(listOf(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "team")), linked("author"))
        assertEquals(listOf(BlueprintNamespace(BlueprintNamespace.Kind.TAXONOMY, "tags")), linked("topics"))
        assertEquals(listOf(BlueprintNamespace(BlueprintNamespace.Kind.ASSET, "images")), linked("hero"))
        assertEquals(listOf(BlueprintNamespace(BlueprintNamespace.Kind.USER, "user")), linked("editor"))
    }

    fun testMultiCollectionLinkCapture() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            """
            tabs:
              main:
                sections:
                  - fields:
                      - handle: related
                        field:
                          type: entries
                          collections:
                            - team
                            - news
            """.trimIndent()
        )
        assertEquals(
            listOf(
                BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "team"),
                BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "news")
            ),
            BlueprintScanner.scan(project).first { it.handle == "related" }.linkedNamespaces
        )
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.BlueprintScannerTest" --no-configuration-cache`
Expected: FAIL — `BlueprintField` has no `linkedNamespaces`.

- [ ] **Step 3: Add `linkedNamespaces` to `BlueprintField`**

Replace `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt` with:

```kotlin
package com.github.balotias.intellijantlers.blueprint

import com.intellij.openapi.vfs.VirtualFile

/** A field declared in a Statamic blueprint/fieldset: the `handle` is the `{{ variable }}` name. */
data class BlueprintField(
    val handle: String,
    val display: String,
    val type: String,
    val file: VirtualFile,
    val offset: Int,
    val namespace: BlueprintNamespace,
    val linkedNamespaces: List<BlueprintNamespace> = emptyList()
)
```

- [ ] **Step 4: Capture the link config in the scanner**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt`.

Add three regexes next to the existing ones:

```kotlin
    private val COLLECTIONS_RE = Regex("""^\s*collections:\s*(\[[^\]]*\])?\s*$""")
    private val TAXONOMY_RE = Regex("""^\s*taxonomy:\s*['"]?([A-Za-z0-9_-]+)['"]?\s*$""")
    private val CONTAINER_RE = Regex("""^\s*container:\s*['"]?([A-Za-z0-9_-]+)['"]?\s*$""")
    private val LIST_ITEM_RE = Regex("""^\s*-\s*['"]?([A-Za-z0-9_-]+)['"]?\s*$""")
```

Replace the `handle` look-ahead window (the block from `var display = ""` through the `out.add(...)` line) with:

```kotlin
                var display = ""
                var type = ""
                val collectionHandles = mutableListOf<String>()
                var taxonomyHandle: String? = null
                var containerHandle: String? = null
                var inCollections = false
                var collectionsIndent = -1
                var j = i + 1
                while (j < lines.size && j < i + 12) {
                    val l = lines[j]
                    if (HANDLE_RE.find(l) != null) break
                    if (display.isEmpty()) DISPLAY_RE.find(l)?.let { display = it.groupValues[1] }
                    if (type.isEmpty()) TYPE_RE.find(l)?.let { type = it.groupValues[1] }
                    COLLECTIONS_RE.find(l)?.let { mc ->
                        val inline = mc.groupValues[1]
                        if (inline.isNotBlank()) {
                            inline.trim('[', ']').split(',').forEach { h ->
                                h.trim().trim('\'', '"').takeIf { s -> s.isNotBlank() }?.let { collectionHandles.add(it) }
                            }
                        } else {
                            inCollections = true
                            collectionsIndent = leadingWs(l)
                        }
                    }
                    if (taxonomyHandle == null) TAXONOMY_RE.find(l)?.let { taxonomyHandle = it.groupValues[1] }
                    if (containerHandle == null) CONTAINER_RE.find(l)?.let { containerHandle = it.groupValues[1] }
                    if (inCollections) {
                        val li = LIST_ITEM_RE.find(l)
                        if (li != null && leadingWs(l) > collectionsIndent) collectionHandles.add(li.groupValues[1])
                    }
                    j++
                }
                val linkedNamespaces = when (type.lowercase()) {
                    "entries", "entry" -> collectionHandles.map { BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, it) }
                    "terms", "term" -> taxonomyHandle?.let { listOf(BlueprintNamespace(BlueprintNamespace.Kind.TAXONOMY, it)) } ?: emptyList()
                    "assets", "asset" -> containerHandle?.let { listOf(BlueprintNamespace(BlueprintNamespace.Kind.ASSET, it)) } ?: emptyList()
                    "users", "user" -> listOf(BlueprintNamespace(BlueprintNamespace.Kind.USER, "user"))
                    else -> emptyList()
                }
                out.add(BlueprintField(handle, display, type, file, handleOffset, base.copy(path = path), linkedNamespaces))
```

(The line that pushes the indent stack — `stack.addLast(indent to handle)` — stays immediately after, unchanged.)

- [ ] **Step 5: Run the scanner tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.BlueprintScannerTest" --no-configuration-cache`
Expected: PASS — including the existing scanner tests (the wider window still breaks at the next `handle:`, and `linkedNamespaces` defaults empty for non-relationship fields).

- [ ] **Step 6: Run the blueprint/scope regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.blueprint.*" --tests "com.github.balotias.intellijantlers.scope.*" --no-configuration-cache`
Expected: PASS — `linkedNamespaces` is additive; no consumer reads it yet.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScanner.kt src/test/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintScannerTest.kt
git commit -m "Capture relationship-field link targets (collections/taxonomy/container/users)"
```

---

## Task 2: Generalize the member resolver to follow links

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt` (add shared constant)
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolver.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolverTest.kt` (new)

- [ ] **Step 1: Write the failing `childNamespaces` test**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolverTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersMemberResolverTest : BasePlatformTestCase() {

    fun testChildNamespaces() {
        val vf = myFixture.addFileToProject("dummy.yaml", "x").virtualFile
        val blog = BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog")

        val container = BlueprintField("hero", "", "group", vf, 0, blog)
        assertEquals(listOf(blog.copy(path = listOf("hero"))), AntlersMemberResolver.childNamespaces(container))

        val team = BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "team")
        val relation = BlueprintField("author", "", "entries", vf, 0, blog, listOf(team))
        assertEquals(listOf(team), AntlersMemberResolver.childNamespaces(relation))

        val plain = BlueprintField("title", "", "text", vf, 0, blog)
        assertTrue(AntlersMemberResolver.childNamespaces(plain).isEmpty())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersMemberResolverTest" --no-configuration-cache`
Expected: FAIL — `childNamespaces` doesn't exist.

- [ ] **Step 3: Add the shared container-types constant**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt` — add a top-level constant below the data class:

```kotlin
/** Fieldtypes that nest sub-fields (Grid / Group / Replicator / Bard). Shared by the scope + member resolvers. */
val CONTAINER_FIELD_TYPES = setOf("grid", "group", "replicator", "bard")
```

- [ ] **Step 4: Add `childNamespaces` and use it in `resolveField`**

Replace `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolver.kt` with:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.CONTAINER_FIELD_TYPES
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Resolves a dotted/colon path to the field it names, following container sub-fields and relationships. */
object AntlersMemberResolver {

    /** The single sub-namespace of a container field (kept for callers migrating to childNamespaces). */
    fun childNamespace(field: BlueprintField): BlueprintNamespace =
        field.namespace.copy(path = field.namespace.path + field.handle)

    /** The namespace(s) whose fields are [field]'s members: container sub-fields OR linked blueprint(s). */
    fun childNamespaces(field: BlueprintField): List<BlueprintNamespace> = when {
        field.type.lowercase() in CONTAINER_FIELD_TYPES ->
            listOf(field.namespace.copy(path = field.namespace.path + field.handle))
        field.linkedNamespaces.isNotEmpty() -> field.linkedNamespaces
        else -> emptyList()
    }

    /** The field that [pathPrefix] names at [element], or null if any segment doesn't resolve. */
    fun resolveField(element: PsiElement, pathPrefix: List<String>, project: Project): BlueprintField? {
        if (pathPrefix.isEmpty()) return null
        var current = AntlersFieldContext.resolveField(element, pathPrefix[0], project) ?: return null
        val svc = BlueprintService.getInstance(project)
        for (i in 1 until pathPrefix.size) {
            current = childNamespaces(current)
                .firstNotNullOfOrNull { ns -> svc.fieldsFor(ns).firstOrNull { it.handle == pathPrefix[i] } }
                ?: return null
        }
        return current
    }
}
```

(`childNamespace` stays for now so the E3b consumers still compile; Task 3 removes it.)

- [ ] **Step 5: Point `AntlersScopeResolver` at the shared constant**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt`.

Add the import:

```kotlin
import com.github.balotias.intellijantlers.blueprint.CONTAINER_FIELD_TYPES
```

Remove the private declaration:

```kotlin
    private val CONTAINER_TYPES = setOf("grid", "group", "replicator", "bard")
```

Change the container-type check inside `containerScope`:

```kotlin
        if (field.type.lowercase() !in CONTAINER_TYPES) return null
```

to:

```kotlin
        if (field.type.lowercase() !in CONTAINER_FIELD_TYPES) return null
```

- [ ] **Step 6: Run the resolver test + scope regression — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersMemberResolverTest" --tests "com.github.balotias.intellijantlers.scope.AntlersContainerScopeTest" --tests "com.github.balotias.intellijantlers.scope.AntlersMemberCompletionTest" --no-configuration-cache`
Expected: PASS — container scoping unchanged (now via the shared constant), E3b member completion still works (it still calls `childNamespace`, which still exists).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/blueprint/BlueprintField.kt src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolver.kt src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersScopeResolver.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolverTest.kt
git commit -m "Add childNamespaces (container OR linked) + share CONTAINER_FIELD_TYPES"
```

---

## Task 3: Wire completion, references, and docs to `childNamespaces`

**Files:**
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt`
- Modify: `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolver.kt` (remove the singular helper)
- Test: `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersRelationTest.kt` (new)

- [ ] **Step 1: Write the failing relationship completion + nav tests**

Create `src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersRelationTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.scope

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersRelationTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            """
            tabs:
              main:
                sections:
                  - fields:
                      - handle: author
                        field:
                          type: entries
                          collections:
                            - team
                      - handle: topics
                        field:
                          type: terms
                          taxonomy: tags
                      - handle: hero
                        field:
                          type: assets
                          container: images
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/team/team.yaml",
            """
            tabs:
              main:
                sections:
                  - fields:
                      - handle: full_name
                        field:
                          type: text
                          display: Full Name
                      - handle: avatar
                        field:
                          type: assets
                          container: images
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "resources/blueprints/taxonomies/tags/tags.yaml",
            "fields:\n  - handle: tag_color\n    field:\n      type: text\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/assets/images.yaml",
            "fields:\n  - handle: caption\n    field:\n      type: text\n"
        )
    }

    private fun lookupsAt(text: String): List<String> {
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testEntriesRelationOffersLinkedFieldsAndProps() {
        setup()
        val l = lookupsAt("{{ author.<caret> }}")
        assertTrue("linked field full_name: $l", l.contains("full_name"))
        assertTrue("entry prop url: $l", l.contains("url"))
        assertFalse("not a blog sibling: $l", l.contains("topics"))
    }

    fun testTermsRelation() {
        setup()
        assertTrue(lookupsAt("{{ topics.<caret> }}").contains("tag_color"))
    }

    fun testAssetsRelationOffersContainerFieldsAndProps() {
        setup()
        val l = lookupsAt("{{ hero.<caret> }}")
        assertTrue("container field caption: $l", l.contains("caption"))
        assertTrue("asset prop alt: $l", l.contains("alt"))
    }

    fun testMultiHop() {
        setup()
        assertTrue("asset prop via author->team->avatar", lookupsAt("{{ author.avatar.<caret> }}").contains("url"))
    }

    fun testBracketAccess() {
        setup()
        assertTrue("bracket access offers linked fields", lookupsAt("{{ author[0].<caret> }}").contains("full_name"))
    }

    fun testNavOnLinkedField() {
        setup()
        val text = "{{ author.full<caret>_name }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("resources/views/blog/show.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = file.findReferenceAt(caret)?.resolve()?.containingFile as? PsiFile
        assertEquals("team.yaml", target!!.name)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersRelationTest" --no-configuration-cache`
Expected: FAIL — relationship members not offered/resolved (childNamespace only does the path-extended namespace, which is empty for relationship fields).

- [ ] **Step 3: Iterate `childNamespaces` in completion**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt` — replace the `offerMembers` body:

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

with:

```kotlin
    private fun offerMembers(field: BlueprintField, project: com.intellij.openapi.project.Project, result: CompletionResultSet) {
        val seen = mutableSetOf<String>()
        val svc = BlueprintService.getInstance(project)
        for (ns in AntlersMemberResolver.childNamespaces(field)) {
            for (sub in svc.fieldsFor(ns)) {
                if (seen.add(sub.handle)) {
                    result.addElement(
                        LookupElementBuilder.create(sub.handle)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText("Field")
                            .withTailText(if (sub.display.isNotBlank()) "  ${sub.display}" else null, true)
                    )
                }
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

- [ ] **Step 4: Iterate `childNamespaces` in the reference helper**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt` — replace the `index > 0` block:

```kotlin
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
```

with:

```kotlin
        if (index > 0) {
            // Non-head dotted/colon segment: resolve the prefix to a field; if this segment is one of
            // its sub-fields / linked-blueprint fields, point at that declaration. (Augmentation
            // properties have no declaration, so they get no reference.)
            val prefix = idents.take(index).map { it.text }
            val resolver = com.github.balotias.intellijantlers.scope.AntlersMemberResolver
            val parent = resolver.resolveField(element, prefix, element.project)
            if (parent != null) {
                val svc = com.github.balotias.intellijantlers.blueprint.BlueprintService.getInstance(element.project)
                for (childNs in resolver.childNamespaces(parent)) {
                    if (svc.fieldsFor(childNs).any { it.handle == name }) {
                        return arrayOf(AntlersBlueprintMemberReference(element, childNs, name))
                    }
                }
            }
        }
```

- [ ] **Step 5: Iterate `childNamespaces` in the docs provider**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt` — replace:

```kotlin
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
```

with:

```kotlin
                if (parent != null) {
                    val svc = BlueprintService.getInstance(ident.project)
                    for (childNs in AntlersMemberResolver.childNamespaces(parent)) {
                        svc.fieldsFor(childNs).firstOrNull { it.handle == name }?.let { f ->
                            val type = if (f.type.isNotBlank()) " (${esc(f.type)})" else ""
                            val title = "Field <b>${esc(name)}</b>$type" +
                                (if (f.display.isNotBlank()) " — ${esc(f.display)}" else "") + namespaceLabel(f.namespace)
                            return section(title, f.display, "")
                        }
                    }
                    FieldtypeProperties.forType(parent.type).firstOrNull { it.name == name }?.let { p ->
                        return section("Property <b>${esc(name)}</b> · ${esc(parent.type)}", p.description, "")
                    }
                }
```

- [ ] **Step 6: Remove the now-unused singular helper**

Edit `src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolver.kt` — delete the `childNamespace` (singular) function and its KDoc:

```kotlin
    /** The single sub-namespace of a container field (kept for callers migrating to childNamespaces). */
    fun childNamespace(field: BlueprintField): BlueprintNamespace =
        field.namespace.copy(path = field.namespace.path + field.handle)

```

- [ ] **Step 7: Run the relationship tests — green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersRelationTest" --no-configuration-cache`
Expected: PASS (6 tests).

- [ ] **Step 8: Run the member regression — still green**

Run: `./gradlew test --tests "com.github.balotias.intellijantlers.scope.AntlersMemberCompletionTest" --tests "com.github.balotias.intellijantlers.scope.AntlersMemberNavTest" --tests "com.github.balotias.intellijantlers.scope.AntlersMemberDocTest" --no-configuration-cache`
Expected: PASS — group sub-fields (container) resolve via `childNamespaces` exactly as before; augmentation-only fields still offer properties.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt src/main/kotlin/com/github/balotias/intellijantlers/references/AntlersDefinitionReferenceHelper.kt src/main/kotlin/com/github/balotias/intellijantlers/documentation/AntlersDocumentationProvider.kt src/main/kotlin/com/github/balotias/intellijantlers/scope/AntlersMemberResolver.kt src/test/kotlin/com/github/balotias/intellijantlers/scope/AntlersRelationTest.kt
git commit -m "Follow relationship links in member completion, nav, and docs"
```

---

## Task 4: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `./gradlew test --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm no failures**

Run: `grep -lo 'failures="[1-9]' build/test-results/test/*.xml | wc -l`
Expected: `0`.

Run: `grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc`
Expected: **~160** (150 prior + ~10 new). Exact count may differ; **0 failures** is the gate.

- [ ] **Step 3: Verify production compilation**

Run: `./gradlew compileKotlin --no-configuration-cache`
Expected: BUILD SUCCESSFUL.

---

## Notes for the implementer

- **No `plugin.xml`, grammar, or lexer changes.** Bracket access needs no code — `{{ field[0].member }}` already yields segments `[field, member]` because `namePath.segments` only takes direct `T_IDENT` children (the `[0]` lives in a `bracketAccess` node).
- Each task compiles on its own: Task 2 keeps the singular `childNamespace` so the E3b consumers still build; Task 3 migrates all three consumers and removes it.
- `CONTAINER_FIELD_TYPES` is one shared top-level constant in `BlueprintField.kt`, used by both `AntlersMemberResolver` and `AntlersScopeResolver` (no drift).
- Relationship members = linked-blueprint fields (typeText "Field") **plus** the curated `FieldtypeProperties` for the field's type (typeText "Property"), deduped so a real field shadows a same-named prop.
- The relationship tests place the file under `resources/views/blog/show.antlers.html` + a `content/collections/blog.yaml` so segment 0 (`author`) resolves via E2 page mapping; multi-hop and bracket then resolve through `childNamespaces`.
- After all tasks, the controlling skill dispatches the final spec-compliance + code-quality review.
```
