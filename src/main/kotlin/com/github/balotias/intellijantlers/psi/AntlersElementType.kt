package com.github.balotias.intellijantlers.psi

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.psi.tree.IElementType

class AntlersElementType(debugName: String) : IElementType(debugName, AntlersLanguage.INSTANCE)
