package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class AntlersBraceMatcher : PairedBraceMatcher {
    val bracePairs = arrayOf(
        BracePair(AntlersTypes.T_LDOUBLE, AntlersTypes.T_RDOUBLE, true),
        BracePair(AntlersTypes.T_COMMENT_OPEN, AntlersTypes.T_COMMENT_CLOSE, false),
        BracePair(AntlersTypes.T_PHP_RAW_OPEN, AntlersTypes.T_PHP_RAW_CLOSE, false),
        BracePair(AntlersTypes.T_PHP_ECHO_OPEN, AntlersTypes.T_PHP_ECHO_CLOSE, false),
        BracePair(AntlersTypes.T_LPAREN, AntlersTypes.T_RPAREN, false),
        BracePair(AntlersTypes.T_LBRACKET, AntlersTypes.T_RBRACKET, false)
    )

    override fun getPairs(): Array<BracePair> = bracePairs
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
