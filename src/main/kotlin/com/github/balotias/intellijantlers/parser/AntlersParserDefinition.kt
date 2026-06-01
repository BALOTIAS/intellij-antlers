package com.github.balotias.intellijantlers.parser

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.lexer._AntlersLexer
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.extapi.psi.PsiFileBase

class AntlersParserDefinition : ParserDefinition {
    companion object {
        val FILE = IFileElementType(AntlersLanguage.INSTANCE)
    }

    override fun createLexer(project: Project?): Lexer = FlexAdapter(_AntlersLexer(null))

    override fun createParser(project: Project?): PsiParser = AntlersParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = TokenSet.create(AntlersTypes.T_COMMENT_START, AntlersTypes.T_COMMENT_TEXT, AntlersTypes.T_COMMENT_END)

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(com.intellij.psi.TokenType.WHITE_SPACE)

    override fun getStringLiteralElements(): TokenSet = TokenSet.create(AntlersTypes.T_STRING)

    override fun createElement(node: ASTNode?): PsiElement = AntlersTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = AntlersFile(viewProvider)
}

class AntlersFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, AntlersLanguage.INSTANCE) {
    override fun getFileType() = com.github.balotias.intellijantlers.AntlersFileType.INSTANCE
    override fun toString() = "Antlers File"
}
