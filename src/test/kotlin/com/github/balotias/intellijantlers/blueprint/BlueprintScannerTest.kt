package com.github.balotias.intellijantlers.blueprint

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BlueprintScannerTest : BasePlatformTestCase() {

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
}
