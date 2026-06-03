package com.github.balotias.intellijantlers.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CatalogDynamicScanTest : BasePlatformTestCase() {

    /** Returns true if the lookup list contains [name], or if a sole-match auto-insert produced it. */
    private fun completionOffers(text: String, name: String): Boolean {
        myFixture.configureByText("p.antlers.html", text)
        val elements = myFixture.completeBasic()
        if (elements != null) {
            return elements.any { it.lookupString == name }
        }
        // completeBasic returned null → it auto-inserted a sole match; check the document.
        return myFixture.editor.document.text.contains(name)
    }

    fun testCustomTagSurfacesInCatalogAndCompletion() {
        myFixture.addFileToProject("app/Tags/MyThing.php", "<?php\nclass MyThing extends Tags {}")
        assertTrue(AntlersCatalogService.getInstance(project).tags().any { it.name == "my_thing" })
        assertTrue(completionOffers("{{ my_<caret> }}", "my_thing"))
    }

    fun testCustomModifierSurfacesInCatalogAndCompletion() {
        myFixture.addFileToProject("app/Modifiers/ShoutLoud.php", "<?php\nclass ShoutLoud extends Modifier {}")
        assertTrue(AntlersCatalogService.getInstance(project).modifiers().any { it.name == "shout_loud" })
        assertTrue(completionOffers("{{ title | sho<caret> }}", "shout_loud"))
    }
}
