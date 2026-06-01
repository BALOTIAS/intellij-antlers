package com.github.balotias.intellijantlers.psi

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.psi.tree.IElementType

class AntlersTokenType(debugName: String) : IElementType(debugName, AntlersLanguage.INSTANCE) {
    override fun toString(): String = "AntlersTokenType." + super.toString()
}
