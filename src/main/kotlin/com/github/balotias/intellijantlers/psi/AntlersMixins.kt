package com.github.balotias.intellijantlers.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

open class AntlersClosingTagMixin(node: ASTNode) : ASTWrapperPsiElement(node)
open class AntlersConditionMixin(node: ASTNode) : ASTWrapperPsiElement(node)
open class AntlersNamePathMixin(node: ASTNode) : ASTWrapperPsiElement(node)
open class AntlersParameterMixin(node: ASTNode) : ASTWrapperPsiElement(node)
open class AntlersModifierMixin(node: ASTNode) : ASTWrapperPsiElement(node)
