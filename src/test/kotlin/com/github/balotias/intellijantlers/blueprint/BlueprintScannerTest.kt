package com.github.balotias.intellijantlers.blueprint

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BlueprintScannerTest : BasePlatformTestCase() {

    private val gridBlueprint = """
        tabs:
          main:
            sections:
              - fields:
                  - handle: rows
                    field:
                      type: grid
                      fields:
                        - handle: caption
                          field:
                            type: text
                            display: Caption
                        - handle: gallery
                          field:
                            type: grid
                            fields:
                              - handle: photo_alt
                                field:
                                  type: text
                  - handle: title
                    field: text
    """.trimIndent()

    private val blueprint = """
        title: Blog
        tabs:
          main:
            sections:
              - fields:
                  - handle: hero_title
                    field:
                      type: text
                      display: Hero Title
                  - handle: body
                    field: markdown
    """.trimIndent()

    fun testScanExtractsFields() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        val fields = BlueprintScanner.scan(project)
        val handles = fields.map { it.handle }
        assertTrue("has hero_title: $handles", handles.contains("hero_title"))
        assertTrue("has body: $handles", handles.contains("body"))
        val hero = fields.first { it.handle == "hero_title" }
        assertEquals("Hero Title", hero.display)
        assertEquals("text", hero.type)
    }

    fun testServiceDedupesAndFinds() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        myFixture.addFileToProject("resources/fieldsets/seo.yaml", "fields:\n  - handle: hero_title\n    field:\n      type: text\n")
        val svc = BlueprintService.getInstance(project)
        assertEquals("deduped by handle", 1, svc.fields().count { it.handle == "hero_title" })
        assertNotNull(svc.field("body"))
        assertNull(svc.field("does_not_exist"))
    }

    fun testFieldsCarryCollectionNamespace() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        val hero = BlueprintScanner.scan(project).first { it.handle == "hero_title" }
        assertEquals(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"), hero.namespace)
    }

    fun testFieldsForFiltersByNamespace() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", blueprint)
        myFixture.addFileToProject(
            "resources/blueprints/collections/news/news.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: News Hero\n"
        )
        val svc = BlueprintService.getInstance(project)
        val blog = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"))
        assertTrue("blog has hero_title", blog.any { it.handle == "hero_title" })
        assertTrue("blog has body", blog.any { it.handle == "body" })
        val news = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "news"))
        assertEquals("news hero_title display", "News Hero", news.first { it.handle == "hero_title" }.display)
        assertFalse("news does not include blog-only body", news.any { it.handle == "body" })
    }

    fun testImportInlinesFieldsetFields() {
        myFixture.addFileToProject(
            "resources/fieldsets/seo.yaml",
            "fields:\n  - handle: meta_title\n    field:\n      type: text\n      display: Meta Title\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "tabs:\n  main:\n    sections:\n      - fields:\n          - import: seo\n          - handle: body\n            field: text\n"
        )
        val svc = BlueprintService.getInstance(project)
        val top = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"))
        assertTrue("imported meta_title at top level", top.any { it.handle == "meta_title" })
        assertTrue("own body field", top.any { it.handle == "body" })
        assertEquals("go-to-def lands in the fieldset", "seo.yaml", top.first { it.handle == "meta_title" }.file.name)
    }

    fun testNestedImportUnderContainer() {
        myFixture.addFileToProject(
            "resources/fieldsets/seo.yaml",
            "fields:\n  - handle: meta_title\n    field:\n      type: text\n"
        )
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "tabs:\n  main:\n    sections:\n      - fields:\n          - handle: rows\n            field:\n              type: grid\n              fields:\n                - import: seo\n"
        )
        val svc = BlueprintService.getInstance(project)
        val grid = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog", listOf("rows")))
        assertTrue("imported field nested under the container", grid.any { it.handle == "meta_title" })
    }

    fun testCyclicImportDoesNotHang() {
        myFixture.addFileToProject("resources/fieldsets/a.yaml", "fields:\n  - import: b\n  - handle: a_field\n    field: text\n")
        myFixture.addFileToProject("resources/fieldsets/b.yaml", "fields:\n  - import: a\n  - handle: b_field\n    field: text\n")
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "tabs:\n  main:\n    sections:\n      - fields:\n          - import: a\n"
        )
        val svc = BlueprintService.getInstance(project)
        val top = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"))
        assertTrue("a's own field inlined", top.any { it.handle == "a_field" })
        assertTrue("b's field inlined via a", top.any { it.handle == "b_field" })
    }

    fun testNestedFieldCarriesContainerPath() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", gridBlueprint)
        val fields = BlueprintScanner.scan(project)
        assertEquals(emptyList<String>(), fields.first { it.handle == "rows" }.namespace.path)
        assertEquals(emptyList<String>(), fields.first { it.handle == "title" }.namespace.path)
        assertEquals(listOf("rows"), fields.first { it.handle == "caption" }.namespace.path)
        assertEquals(listOf("rows", "gallery"), fields.first { it.handle == "photo_alt" }.namespace.path)
    }

    fun testFlattenBugFixedTopLevelExcludesNested() {
        myFixture.addFileToProject("resources/blueprints/collections/blog/blog.yaml", gridBlueprint)
        val svc = BlueprintService.getInstance(project)
        val top = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog"))
        assertTrue("top-level has rows", top.any { it.handle == "rows" })
        assertTrue("top-level has title", top.any { it.handle == "title" })
        assertFalse("caption is nested, not top-level", top.any { it.handle == "caption" })
        val grid = svc.fieldsFor(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog", listOf("rows")))
        assertTrue("rows scope has caption", grid.any { it.handle == "caption" })
        assertTrue("rows scope has gallery", grid.any { it.handle == "gallery" })
        assertFalse("rows scope excludes deeper photo_alt", grid.any { it.handle == "photo_alt" })
    }

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
}
