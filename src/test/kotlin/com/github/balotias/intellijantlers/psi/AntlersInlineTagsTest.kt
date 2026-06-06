package com.github.balotias.intellijantlers.psi

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersInlineTagsTest : BasePlatformTestCase() {

    // `collection` is a bundled catalog tag, so isTag("collection") is true without any project setup.
    private fun identNamed(text: String, name: String): PsiElement {
        myFixture.configureByText("p.antlers.html", text)
        val antlers = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
        return PsiTreeUtil.collectElements(antlers) {
            it.node?.elementType == AntlersTypes.T_IDENT && it.text == name
        }.first()
    }

    fun testInlineTagHeadIsRecognized() {
        assertTrue(AntlersInlineTags.isInlineTagHead(identNamed("{{ x = {collection from=\"blog\"} }}", "collection")))
    }

    fun testArrayKeyIsNotInlineTagHead() {
        assertFalse(AntlersInlineTags.isInlineTagHead(identNamed("{{ x = {collection: 'y'} }}", "collection")))
    }

    fun testStandaloneTagHeadIsNotInlineTagHead() {
        assertFalse(AntlersInlineTags.isInlineTagHead(identNamed("{{ collection from=\"blog\" }}", "collection")))
    }

    fun testUnknownNameAfterBraceIsNotInlineTagHead() {
        assertFalse(AntlersInlineTags.isInlineTagHead(identNamed("{{ x = {not_a_tag y=\"z\"} }}", "not_a_tag")))
    }
}
