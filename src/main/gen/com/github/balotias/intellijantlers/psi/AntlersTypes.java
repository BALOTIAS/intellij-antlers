// This is a generated file. Not intended for manual editing.
package com.github.balotias.intellijantlers.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.github.balotias.intellijantlers.psi.impl.*;

public interface AntlersTypes {

  IElementType COMMENT_BLOCK = new AntlersElementType("COMMENT_BLOCK");
  IElementType OUTER_HTML = new AntlersElementType("OUTER_HTML");
  IElementType TAG_CONTENT = new AntlersElementType("TAG_CONTENT");
  IElementType TAG_STATEMENT = new AntlersElementType("TAG_STATEMENT");

  IElementType T_AT = new AntlersTokenType("@");
  IElementType T_CLOSE_BRACE = new AntlersTokenType("}}");
  IElementType T_COLON = new AntlersTokenType(":");
  IElementType T_COMMENT_END = new AntlersTokenType("#}}");
  IElementType T_COMMENT_START = new AntlersTokenType("{{#");
  IElementType T_COMMENT_TEXT = new AntlersTokenType("T_COMMENT_TEXT");
  IElementType T_EQUALS = new AntlersTokenType("=");
  IElementType T_IDENTIFIER = new AntlersTokenType("T_IDENTIFIER");
  IElementType T_MODIFIER_PIPE = new AntlersTokenType("|");
  IElementType T_NUMBER = new AntlersTokenType("T_NUMBER");
  IElementType T_OPEN_BRACE = new AntlersTokenType("{{");
  IElementType T_OPERATOR = new AntlersTokenType("T_OPERATOR");
  IElementType T_OUTER_HTML = new AntlersTokenType("T_OUTER_HTML");
  IElementType T_SLASH = new AntlersTokenType("/");
  IElementType T_STRING = new AntlersTokenType("T_STRING");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == COMMENT_BLOCK) {
        return new AntlersCommentBlockImpl(node);
      }
      else if (type == OUTER_HTML) {
        return new AntlersOuterHtmlImpl(node);
      }
      else if (type == TAG_CONTENT) {
        return new AntlersTagContentImpl(node);
      }
      else if (type == TAG_STATEMENT) {
        return new AntlersTagStatementImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
