# Antlers Completion Gaps Implementation Plan

> #1 colon-handle completion (`{{ collection:‹caret› }}` → handles) + #6 form block variables. Execute INLINE.

**Spec:** `docs/superpowers/specs/2026-06-03-antlers-completion-gaps-design.md`
**Branch:** `antlers-completion-gaps` (from `main`).
**Gate:** `./gradlew --rerun-tasks test` then `grep -lo 'failures="[1-9]\|errors="[1-9]' build/test-results/test/*.xml` (empty).

**Files:** `StatamicProject.kt`, new `scope/FormVariables.kt`, `AntlersCompletionProvider.kt`; tests in
`completion/AntlersCompletionTest.kt` (or a new `AntlersCompletionGapsTest.kt`).

---

### Step 1: Failing tests

Create `src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionGapsTest.kt`:

```kotlin
package com.github.balotias.intellijantlers.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersCompletionGapsTest : BasePlatformTestCase() {

    private fun lookups(text: String): List<String> {
        myFixture.configureByText("page.antlers.html", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    fun testCollectionColonOffersHandles() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", "title: Blog\n")
        val l = lookups("{{ collection:<caret> }}")
        assertTrue("offers collection handle blog: $l", l.contains("blog"))
        assertTrue("still offers a method (count): $l", l.contains("count"))
    }

    fun testTaxonomyColonOffersHandles() {
        myFixture.addFileToProject("resources/blueprints/taxonomies/tags/tags.yaml", "title: Tags\n")
        assertTrue(lookups("{{ taxonomy:<caret> }}").contains("tags"))
    }

    fun testFormColonOffersHandles() {
        myFixture.addFileToProject("resources/blueprints/forms/contact.yaml", "title: Contact\n")
        assertTrue(lookups("{{ form:<caret> }}").contains("contact"))
    }

    fun testFormScopeOffersFormVars() {
        val l = lookups("{{ form:contact }}{{ <caret> }}{{ /form:contact }}")
        assertTrue("success: $l", l.contains("success"))
        assertTrue("errors: $l", l.contains("errors"))
        assertTrue("fields: $l", l.contains("fields"))
    }

    fun testNonFormScopeHasNoFormVars() {
        val l = lookups("{{ collection:blog }}{{ <caret> }}{{ /collection }}")
        assertFalse("no form var in a collection scope: $l", l.contains("success"))
        assertFalse("no form var in a collection scope: $l", l.contains("submission_created"))
    }
}
```

- [ ] Run `./gradlew --rerun-tasks test --tests "*AntlersCompletionGapsTest"` → expect FAIL (handles +
  form vars not offered yet).

---

### Step 2: `StatamicProject` form/nav handle listers

After `listTaxonomyHandles` (line ~83), add:
```kotlin
    /** Form handles = `*.yaml` basenames under `resources/blueprints/forms`. */
    fun listFormHandles(element: PsiElement): List<String> = blueprintFiles(element, "forms")

    /** Nav handles = `*.yaml` basenames under `resources/blueprints/navigation`. */
    fun listNavHandles(element: PsiElement): List<String> = blueprintFiles(element, "navigation")

    private fun blueprintFiles(element: PsiElement, kind: String): List<String> {
        val resources = viewsRoot(element)?.parent ?: return emptyList()
        val dir = resources.findChild("blueprints")?.findChild(kind) ?: return emptyList()
        return dir.children.filter { !it.isDirectory && it.name.endsWith(".yaml") }
            .map { it.name.removeSuffix(".yaml") }
    }
```

---

### Step 3: `scope/FormVariables.kt` (new)

```kotlin
package com.github.balotias.intellijantlers.scope

/** A runtime variable available inside a `{{ form:… }}` block. */
data class FormVariable(val name: String, val description: String)

object FormVariables {
    val ALL: List<FormVariable> = listOf(
        FormVariable("fields", "Array of the form's fields, for dynamic rendering."),
        FormVariable("errors", "Indexed array of validation error messages after submission."),
        FormVariable("error", "Validation errors indexed by field handle."),
        FormVariable("old", "Submitted values from the previous request, to re-populate fields."),
        FormVariable("success", "Success message — truthy after a successful submission."),
        FormVariable("submission_created", "Boolean success (falsey when the honeypot is filled)."),
    )
}
```

---

### Step 4: `AntlersCompletionProvider` — offer handles + form vars

Add imports:
```kotlin
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.references.StatamicProject
import com.github.balotias.intellijantlers.scope.FormVariables
```

**(4a) Colon handles** — in the `TAG_METHOD` arm, inside the `if (tag != null) { … }` block, after the
existing `tag.methods.forEach { … }`, add:
```kotlin
                    val handles: List<Pair<String, String>> = when (info.tagHead) {
                        "collection" -> StatamicProject.listCollectionHandles(parameters.position).map { it to "Collection" }
                        "taxonomy" -> StatamicProject.listTaxonomyHandles(parameters.position).map { it to "Taxonomy" }
                        "form" -> StatamicProject.listFormHandles(parameters.position).map { it to "Form" }
                        "nav" -> StatamicProject.listNavHandles(parameters.position).map { it to "Nav" }
                        else -> emptyList()
                    }
                    for ((handle, type) in handles) {
                        result.addElement(
                            LookupElementBuilder.create(handle).withIcon(AntlersIcons.FILE).withTypeText(type)
                        )
                    }
```

**(4b) Form vars** — in the `TAG_NAME` arm, immediately after the nav block (the
`if (scopes.any { it.navMeta }) { … }`), add:
```kotlin
                // Form runtime vars only inside a {{ form:… }} scope.
                if (scopes.any { it.namespace.kind == BlueprintNamespace.Kind.FORM }) {
                    for (fv in FormVariables.ALL) {
                        if (seen.add(fv.name)) {
                            result.addElement(
                                LookupElementBuilder.create(fv.name)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Form")
                                    .withTailText("  ${fv.description}", true)
                            )
                        }
                    }
                }
```
(If `it.namespace.kind` doesn't resolve — e.g. `scopes` is typed as the `AntlersScope` supertype — cast
in the predicate: `scopes.any { (it as? com.github.balotias.intellijantlers.scope.BlueprintScope)?.namespace?.kind == BlueprintNamespace.Kind.FORM }`. The nav block uses `it.navMeta` directly, so the
element type is `BlueprintScope` and the direct form should compile.)

---

### Step 5: Run + gate

- [ ] `./gradlew --rerun-tasks test --tests "*AntlersCompletionGapsTest"` → PASS (5).
- [ ] Full gate: `./gradlew --rerun-tasks test` + grep (empty). If a pre-existing completion test now
  sees extra handles at `{{ collection:<caret> }}`, reconcile (report it).
- [ ] Commit:
```bash
git add src/main/kotlin/com/github/balotias/intellijantlers/references/StatamicProject.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/scope/FormVariables.kt \
        src/main/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionProvider.kt \
        src/test/kotlin/com/github/balotias/intellijantlers/completion/AntlersCompletionGapsTest.kt
git commit -m "$(cat <<'EOF'
feat: colon-shorthand handle completion + form block variables

`{{ collection:<caret> }}` (and taxonomy/form/nav) now offers handles alongside
methods; inside a `{{ form:X }}` block the form runtime vars (success/errors/
fields/error/old/submission_created) are offered.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Self-Review
- #1: `blueprintFiles` mirrors `blueprintSubdirs` (files vs dirs); the TAG_METHOD `when` adds handles per
  shorthand-handle tag, methods kept. collection/taxonomy reuse proven listers.
- #6: `FormVariables` mirrors `NavVariables`; offered only when a `FORM`-kind scope is present
  (regression-guarded against a collection scope).
- Additive; no parser/grammar change.
