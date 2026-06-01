package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace.Kind
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersScopeResolverTest : BasePlatformTestCase() {

    /** Returns the BlueprintScope namespaces (innermost first) at the <caret> in [text]. */
    private fun scopesAt(text: String): List<BlueprintNamespace> {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("page.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret) ?: error("no element at caret")
        return AntlersScopeResolver.scopesAt(el).map { (it as BlueprintScope).namespace }
    }

    fun testInsideCollection() {
        assertEquals(
            listOf(BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection:blog }}{{ <caret> }}{{ /collection }}")
        )
    }

    fun testNestedCascadeInnermostFirst() {
        assertEquals(
            listOf(BlueprintNamespace(Kind.TAXONOMY, "tags"), BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection:blog }}{{ taxonomy:tags }}{{ <caret> }}{{ /taxonomy }}{{ /collection }}")
        )
    }

    fun testTopLevelEmpty() {
        assertEquals(emptyList<BlueprintNamespace>(), scopesAt("{{ <caret> }}"))
    }

    fun testConditionIsTransparent() {
        assertEquals(emptyList<BlueprintNamespace>(), scopesAt("{{ if foo }}{{ <caret> }}{{ /if }}"))
    }

    fun testFromParamHandle() {
        assertEquals(
            listOf(BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection from=\"blog\" }}{{ <caret> }}{{ /collection }}")
        )
    }

    fun testAliasBodyEmptyButAliasTagScoped() {
        // In the collection body (before the alias tag) there is no scope...
        assertEquals(
            emptyList<BlueprintNamespace>(),
            scopesAt("{{ collection:blog as=\"entries\" }}{{ <caret> }}{{ /collection }}")
        )
    }

    fun testInsideAliasTagScoped() {
        // ...but inside {{ entries }} the collection's fields apply.
        assertEquals(
            listOf(BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection:blog as=\"entries\" }}{{ entries }}{{ <caret> }}{{ /entries }}{{ /collection }}")
        )
    }

    fun testTransparentTagPreservesOuterScope() {
        assertEquals(
            listOf(BlueprintNamespace(Kind.COLLECTION, "blog")),
            scopesAt("{{ collection:blog }}{{ cache }}{{ <caret> }}{{ /cache }}{{ /collection }}")
        )
    }

    fun testUnresolvedHandleNoScope() {
        // collection with no handle and no from/in → cannot scope → empty (global fallback)
        assertEquals(emptyList<BlueprintNamespace>(), scopesAt("{{ collection }}{{ <caret> }}{{ /collection }}"))
    }

    private fun rawScopesAt(text: String): List<BlueprintScope> {
        val caret = text.indexOf("<caret>")
        myFixture.configureByText("page.antlers.html", text.replace("<caret>", ""))
        val el = myFixture.file.findElementAt(caret) ?: error("no element at caret")
        return AntlersScopeResolver.scopesAt(el)
    }

    fun testNavShorthandScope() {
        val s = rawScopesAt("{{ nav:main }}{{ <caret> }}{{ /nav }}")
        assertEquals(1, s.size)
        assertEquals(BlueprintNamespace(Kind.NAVIGATION, "main"), s[0].namespace)
        assertTrue("navMeta flagged", s[0].navMeta)
    }

    fun testNavCollectionScope() {
        val s = rawScopesAt("{{ nav:collection:blog }}{{ <caret> }}{{ /nav }}")
        assertEquals(BlueprintNamespace(Kind.COLLECTION, "blog"), s[0].namespace)
        assertTrue(s[0].navMeta)
    }

    fun testNavHandleParam() {
        val s = rawScopesAt("{{ nav handle=\"main\" }}{{ <caret> }}{{ /nav }}")
        assertEquals(BlueprintNamespace(Kind.NAVIGATION, "main"), s[0].namespace)
    }

    fun testNavBareDefaultsToPages() {
        val s = rawScopesAt("{{ nav }}{{ <caret> }}{{ /nav }}")
        assertEquals(BlueprintNamespace(Kind.COLLECTION, "pages"), s[0].namespace)
        assertTrue(s[0].navMeta)
    }

    fun testNavChildrenRecursiveReentry() {
        val s = rawScopesAt("{{ nav:main }}{{ children }}{{ <caret> }}{{ /children }}{{ /nav }}")
        assertTrue(s.isNotEmpty())
        assertEquals(BlueprintNamespace(Kind.NAVIGATION, "main"), s[0].namespace)
        assertTrue("children re-entry keeps navMeta", s[0].navMeta)
    }
}
