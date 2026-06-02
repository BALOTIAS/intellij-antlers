package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lexer.FlexAdapter
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet

/** Feeds Antlers identifiers/strings to the word index so partial references are searchable. */
class AntlersFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        FlexAdapter(_AntlersLexer(null)),
        TokenSet.create(AntlersTypes.T_IDENT, AntlersTypes.T_STRING),
        TokenSet.create(AntlersTypes.T_COMMENT_TEXT),
        TokenSet.EMPTY
    )

    // We don't define custom find-usages target symbols (partials are file targets); only the scanner matters.
    override fun canFindUsagesFor(element: PsiElement): Boolean = element is AntlersFieldDeclaration
    override fun getType(element: PsiElement): String =
        if (element is AntlersFieldDeclaration) "blueprint field" else ""
    override fun getDescriptiveName(element: PsiElement): String = (element as? PsiNamedElement)?.name ?: ""
    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
    override fun getHelpId(element: PsiElement): String? = null
}
