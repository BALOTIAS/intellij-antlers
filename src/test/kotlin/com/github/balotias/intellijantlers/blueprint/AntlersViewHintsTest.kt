package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersViewHintsTest : BasePlatformTestCase() {

    fun testLeadingCollectionHintBecomesNamespace() {
        val file = myFixture.configureByText("p.antlers.html", "{{# @collection blog #}}\n{{ x }}")
        val ns = AntlersViewHints.declaredNamespaces(file)
        assertEquals(listOf(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog")), ns)
    }

    fun testNoHintNoNamespaces() {
        val file = myFixture.configureByText("p.antlers.html", "{{# just a note #}}\n{{ x }}")
        assertTrue(AntlersViewHints.declaredNamespaces(file).isEmpty())
    }

    fun testFrontMatterBeforeHintIsTransparent() {
        val file = myFixture.configureByText(
            "p.antlers.html",
            "---\ntitle: Hi\n---\n{{# @collection blog #}}\n{{ x }}"
        )
        assertEquals(
            listOf(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "blog")),
            AntlersViewHints.declaredNamespaces(file)
        )
    }

    fun testLoopScopeWinsOverHint() {
        val text = "{{# @collection blog #}}\n{{ collection:other }}{{ <caret> }}{{ /collection }}"
        val caret = text.indexOf("<caret>")
        val file = myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        val el = file.findElementAt(caret)!!
        assertEquals(
            listOf(BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, "other")),
            AntlersFieldContext.namespacesFor(el)
        )
    }

    fun testCollectionHintRestrictsFieldsToThatBlueprint() {
        myFixture.addFileToProject(
            "resources/blueprints/collections/blog/blog.yaml",
            "fields:\n  - handle: hero_title\n    field:\n      type: text\n      display: X\n"
        )
        val file = myFixture.configureByText("p.antlers.html", "{{# @collection blog #}}\n{{ x }}")
        val antlers = file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        val el = antlers.findElementAt(antlers.text.lastIndexOf("x"))!!
        val handles = AntlersFieldContext.fieldsInScope(el, project)?.map { it.handle }
        assertEquals(listOf("hero_title"), handles)
    }
}
