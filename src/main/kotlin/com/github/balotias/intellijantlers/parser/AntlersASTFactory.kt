package com.github.balotias.intellijantlers.parser

import com.github.balotias.intellijantlers.psi.AntlersIdentLeaf
import com.github.balotias.intellijantlers.psi.AntlersStringLeaf
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.template.ANTLERS_FRAGMENT
import com.intellij.lang.DefaultASTFactoryImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.templateLanguages.OuterLanguageElementImpl
import com.intellij.psi.tree.IElementType

/**
 * Creates the leaf PSI for the hidden Antlers `{{ }}` ranges in the generated HTML data tree as
 * [OuterLanguageElementImpl]. `TemplateDataElementType` requires the outer-element type
 * ([ANTLERS_FRAGMENT]) to produce an `OuterLanguageElement`; without this the default factory makes
 * a plain element and the platform fails with "Wrong element created by ASTFactory".
 *
 * Also creates custom leaf elements for T_STRING and T_IDENT that override getReferences()
 * for partial path resolution.
 */
class AntlersASTFactory : DefaultASTFactoryImpl() {
    override fun createLeaf(type: IElementType, text: CharSequence): LeafElement {
        return when {
            type === ANTLERS_FRAGMENT -> OuterLanguageElementImpl(type, text)
            type === AntlersTypes.T_STRING -> AntlersStringLeaf(type, text)
            type === AntlersTypes.T_IDENT -> AntlersIdentLeaf(type, text)
            else -> super.createLeaf(type, text)
        }
    }
}
