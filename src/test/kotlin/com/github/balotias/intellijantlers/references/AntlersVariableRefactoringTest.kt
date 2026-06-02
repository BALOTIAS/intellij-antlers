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
        )
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
        val newsTitleCaret = tmpl.text.indexOf("title")
        val decl = declAt(tmpl, newsTitleCaret)
        myFixture.renameElement(decl, "headline")
        commit()
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

    fun testRenameFieldsetFieldAcrossScopedCollections() {
        myFixture.addFileToProject(
            "resources/fieldsets/seo.yaml",
            "fields:\n  - handle: meta_title\n    field:\n      type: text\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml", "fields:\n  - import: seo\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml", "fields:\n  - import: seo\n"
        )
        myFixture.addFileToProject("content/collections/blog.yaml", "template: blog/show\n")
        myFixture.addFileToProject("content/collections/news.yaml", "template: news/show\n")
        val blogTmpl = myFixture.addFileToProject("resources/views/blog/show.antlers.html", "{{ meta_title }}")
        val newsTmpl = myFixture.addFileToProject("resources/views/news/show.antlers.html", "{{ meta_title }}")
        myFixture.configureFromExistingVirtualFile(blogTmpl.virtualFile)
        val decl = declAt(blogTmpl, "{{ meta_title }}".indexOf("meta"))
        myFixture.renameElement(decl, "seo_title")
        commit()
        assertEquals("{{ seo_title }}", blogTmpl.text)
        assertEquals("blog and news both import the same fieldset field; news usage must rename too: ${newsTmpl.text}",
            "{{ seo_title }}", newsTmpl.text)
    }

    fun testRenameFromUsageCaret() {
        addBlueprint("hero_title")
        myFixture.configureByText("page.antlers.html", "{{ hero_<caret>title }}")
        myFixture.renameElementAtCaret("hero_subtitle")
        commit()
        assertTrue("usage renamed at caret: ${myFixture.file.text}",
            myFixture.file.text.contains("{{ hero_subtitle }}"))
    }

    /**
     * A field whose handle equals a BUNDLED catalog tag name ("collection") still caret-renames
     * correctly: the head ident exposes [PHP-class ref, blueprint-field ref], and the PHP-class ref
     * resolves only PROJECT tag classes (none here) → the field reference wins. Pins the accepted
     * KNOWN LIMITATION in AntlersDefinitionReferenceHelper as NOT biting the common case.
     */
    fun testRenameFieldNamedLikeBundledTagWorks() {
        addBlueprint("collection")
        myFixture.configureByText("page.antlers.html", "{{ collec<caret>tion }}")
        myFixture.renameElementAtCaret("featured")
        commit()
        assertTrue("field named like a bundled tag still renames: ${myFixture.file.text}",
            myFixture.file.text.contains("{{ featured }}"))
    }
}
