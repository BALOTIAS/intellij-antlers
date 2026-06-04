// This is a generated file. Not intended for manual editing.
package com.github.balotias.intellijantlers.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.github.balotias.intellijantlers.psi.impl.*;

public interface AntlersTypes {

  IElementType BRACKET_ACCESS = new AntlersElementType("BRACKET_ACCESS");
  IElementType CLOSING_TAG = new AntlersElementType("CLOSING_TAG");
  IElementType COMMENT = new AntlersElementType("COMMENT");
  IElementType CONDITION = new AntlersElementType("CONDITION");
  IElementType FRONT_MATTER = new AntlersElementType("FRONT_MATTER");
  IElementType FRONT_MATTER_BODY = new AntlersElementType("FRONT_MATTER_BODY");
  IElementType MODIFIER = new AntlersElementType("MODIFIER");
  IElementType NAME_PATH = new AntlersElementType("NAME_PATH");
  IElementType NOPARSE_BLOCK = new AntlersElementType("NOPARSE_BLOCK");
  IElementType OUTER_HTML = new AntlersElementType("OUTER_HTML");
  IElementType PARAMETER = new AntlersElementType("PARAMETER");
  IElementType PHP_BLOCK = new AntlersElementType("PHP_BLOCK");
  IElementType PHP_BLOCK_BODY = new AntlersElementType("PHP_BLOCK_BODY");
  IElementType PHP_ECHO_BLOCK = new AntlersElementType("PHP_ECHO_BLOCK");
  IElementType PHP_RAW_BLOCK = new AntlersElementType("PHP_RAW_BLOCK");
  IElementType STATEMENT = new AntlersElementType("STATEMENT");

  IElementType T_ARROW = new AntlersTokenType("=>");
  IElementType T_AT = new AntlersTokenType("@");
  IElementType T_COLON = new AntlersTokenType(":");
  IElementType T_COMMA = new AntlersTokenType(",");
  IElementType T_COMMENT_CLOSE = new AntlersTokenType("#}}");
  IElementType T_COMMENT_OPEN = new AntlersTokenType("{{#");
  IElementType T_COMMENT_TEXT = new AntlersTokenType("T_COMMENT_TEXT");
  IElementType T_DOLLAR = new AntlersTokenType("$");
  IElementType T_DOT = new AntlersTokenType(".");
  IElementType T_EQUALS = new AntlersTokenType("=");
  IElementType T_FRONTMATTER_FENCE = new AntlersTokenType("T_FRONTMATTER_FENCE");
  IElementType T_FRONTMATTER_TEXT = new AntlersTokenType("T_FRONTMATTER_TEXT");
  IElementType T_IDENT = new AntlersTokenType("T_IDENT");
  IElementType T_LBRACE = new AntlersTokenType("{");
  IElementType T_LBRACKET = new AntlersTokenType("[");
  IElementType T_LDOUBLE = new AntlersTokenType("{{");
  IElementType T_LPAREN = new AntlersTokenType("(");
  IElementType T_NOPARSE_CLOSE = new AntlersTokenType("T_NOPARSE_CLOSE");
  IElementType T_NOPARSE_OPEN = new AntlersTokenType("T_NOPARSE_OPEN");
  IElementType T_NOPARSE_TEXT = new AntlersTokenType("T_NOPARSE_TEXT");
  IElementType T_NUMBER = new AntlersTokenType("T_NUMBER");
  IElementType T_OP = new AntlersTokenType("T_OP");
  IElementType T_OUTER_HTML = new AntlersTokenType("T_OUTER_HTML");
  IElementType T_PHP_ECHO_CLOSE = new AntlersTokenType("$}}");
  IElementType T_PHP_ECHO_OPEN = new AntlersTokenType("{{$");
  IElementType T_PHP_RAW_CLOSE = new AntlersTokenType("?}}");
  IElementType T_PHP_RAW_OPEN = new AntlersTokenType("{{?");
  IElementType T_PHP_TEXT = new AntlersTokenType("T_PHP_TEXT");
  IElementType T_PIPE = new AntlersTokenType("|");
  IElementType T_RBRACE = new AntlersTokenType("}");
  IElementType T_RBRACKET = new AntlersTokenType("]");
  IElementType T_RDOUBLE = new AntlersTokenType("}}");
  IElementType T_RPAREN = new AntlersTokenType(")");
  IElementType T_SEMICOLON = new AntlersTokenType(";");
  IElementType T_SLASH = new AntlersTokenType("/");
  IElementType T_STRING = new AntlersTokenType("T_STRING");
  IElementType T_WS = new AntlersTokenType("T_WS");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == BRACKET_ACCESS) {
        return new AntlersBracketAccessImpl(node);
      }
      else if (type == CLOSING_TAG) {
        return new AntlersClosingTagImpl(node);
      }
      else if (type == COMMENT) {
        return new AntlersCommentImpl(node);
      }
      else if (type == CONDITION) {
        return new AntlersConditionImpl(node);
      }
      else if (type == FRONT_MATTER) {
        return new AntlersFrontMatterImpl(node);
      }
      else if (type == FRONT_MATTER_BODY) {
        return new AntlersFrontMatterBodyImpl(node);
      }
      else if (type == MODIFIER) {
        return new AntlersModifierImpl(node);
      }
      else if (type == NAME_PATH) {
        return new AntlersNamePathImpl(node);
      }
      else if (type == NOPARSE_BLOCK) {
        return new AntlersNoparseBlockImpl(node);
      }
      else if (type == OUTER_HTML) {
        return new AntlersOuterHtmlImpl(node);
      }
      else if (type == PARAMETER) {
        return new AntlersParameterImpl(node);
      }
      else if (type == PHP_BLOCK) {
        return new AntlersPhpBlockImpl(node);
      }
      else if (type == PHP_BLOCK_BODY) {
        return new AntlersPhpBlockBodyImpl(node);
      }
      else if (type == PHP_ECHO_BLOCK) {
        return new AntlersPhpEchoBlockImpl(node);
      }
      else if (type == PHP_RAW_BLOCK) {
        return new AntlersPhpRawBlockImpl(node);
      }
      else if (type == STATEMENT) {
        return new AntlersStatementImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
