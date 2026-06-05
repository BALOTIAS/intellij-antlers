package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.catalog.ModifierDef
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext

class AntlersModifierParameterInfoTest : BasePlatformTestCase() {

    fun testResolvesModifierAndItsDef() {
        val text = "{{ title | replace('a', <caret>'b') }}"
        myFixture.configureByText("p.antlers.html", text.replace("<caret>", ""))
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))

        val handler = AntlersModifierParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val element = handler.findElementForParameterInfo(context)

        assertNotNull("found the modifier element", element)
        val items = context.itemsToShow
        assertNotNull(items)
        assertEquals("replace", (items!![0] as ModifierDef).name)
    }
}
