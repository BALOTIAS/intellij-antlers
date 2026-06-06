package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.references.AntlersPartialParams.PartialParam
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext

class AntlersPartialParameterInfoTest : BasePlatformTestCase() {

    private val button = "{{#\n" +
        "@param* label The caption label.\n" +
        "@param as The wrapping element.\n" +
        "@param button_type Inline if needed.\n" +
        "@param faux Boolean.\n" +
        "#}}\n<button></button>"

    private fun setup() {
        myFixture.addFileToProject("resources/views/components/_button.antlers.html", button)
    }

    private fun configureInclude(text: String) {
        setup()
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))
    }

    fun testFindsPartialAndItsParams() {
        configureInclude("{{ partial:components/button <caret> }}")
        val ctx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val el = AntlersPartialParameterInfoHandler().findElementForParameterInfo(ctx)
        assertNotNull("found the partial include statement", el)
        @Suppress("UNCHECKED_CAST")
        val params = ctx.itemsToShow!![0] as List<PartialParam>
        assertEquals(listOf("label", "as", "button_type", "faux"), params.map { it.name })
    }

    fun testNonPartialTagIgnored() {
        myFixture.configureByText("p.antlers.html", "{{ collection:blog  }}")
        myFixture.editor.caretModel.moveToOffset("{{ collection:blog ".length)
        val ctx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        assertNull(AntlersPartialParameterInfoHandler().findElementForParameterInfo(ctx))
    }

    fun testUnresolvedPartialIgnored() {
        myFixture.configureByText("p.antlers.html", "{{ partial:does/not/exist  }}")
        myFixture.editor.caretModel.moveToOffset("{{ partial:does/not/exist ".length)
        val ctx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        assertNull(AntlersPartialParameterInfoHandler().findElementForParameterInfo(ctx))
    }

    fun testPartialWithoutParamsIgnored() {
        myFixture.addFileToProject("resources/views/components/_plain.antlers.html", "<div>no hints</div>")
        val file = myFixture.addFileToProject("resources/views/page.antlers.html", "{{ partial:components/plain  }}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset("{{ partial:components/plain ".length)
        val ctx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        assertNull(AntlersPartialParameterInfoHandler().findElementForParameterInfo(ctx))
    }

    fun testCurrentIndexAtParam() {
        configureInclude("{{ partial:components/button label=\"x\" a<caret>s=\"y\" }}")
        val handler = AntlersPartialParameterInfoHandler()
        val stmt = handler.partialStmtAt(myFixture.file, myFixture.caretOffset)!!
        assertEquals(1, handler.currentIndex(stmt, myFixture.caretOffset))
    }

    fun testRenderSignatureBoldsCurrent() {
        val params = listOf(PartialParam("label", true, ""), PartialParam("as", false, ""))
        val (text, hs, he) = AntlersPartialParameterInfoHandler.renderSignature(params, 1)
        assertEquals("label*, as", text)
        assertEquals("label*, ".length, hs)
        assertEquals("label*, as".length, he)
    }

    fun testRenderSignatureNoCurrent() {
        val params = listOf(PartialParam("label", true, ""), PartialParam("as", false, ""))
        val (text, hs, he) = AntlersPartialParameterInfoHandler.renderSignature(params, -1)
        assertEquals("label*, as", text)
        assertEquals(-1, hs)
        assertEquals(-1, he)
    }
}
