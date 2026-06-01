// This is a generated file. Not intended for manual editing.
package com.github.balotias.intellijantlers.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.github.balotias.intellijantlers.psi.AntlersTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class AntlersParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, null);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    result_ = parse_root_(root_, builder_);
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_) {
    return parse_root_(root_, builder_, 0);
  }

  static boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return antlersFile(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // item_*
  static boolean antlersFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "antlersFile")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!item_(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "antlersFile", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // T_COMMENT_OPEN T_COMMENT_TEXT? T_COMMENT_CLOSE
  public static boolean comment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comment")) return false;
    if (!nextTokenIs(builder_, T_COMMENT_OPEN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_COMMENT_OPEN);
    result_ = result_ && comment_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_COMMENT_CLOSE);
    exit_section_(builder_, marker_, COMMENT, result_);
    return result_;
  }

  // T_COMMENT_TEXT?
  private static boolean comment_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comment_1")) return false;
    consumeToken(builder_, T_COMMENT_TEXT);
    return true;
  }

  /* ********************************************************** */
  // outerHtml | statement | comment | phpBlock | noparseBlock
  static boolean item_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "item_")) return false;
    boolean result_;
    result_ = outerHtml(builder_, level_ + 1);
    if (!result_) result_ = statement(builder_, level_ + 1);
    if (!result_) result_ = comment(builder_, level_ + 1);
    if (!result_) result_ = phpBlock(builder_, level_ + 1);
    if (!result_) result_ = noparseBlock(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // T_NOPARSE_OPEN T_NOPARSE_TEXT* T_NOPARSE_CLOSE
  public static boolean noparseBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noparseBlock")) return false;
    if (!nextTokenIs(builder_, T_NOPARSE_OPEN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_NOPARSE_OPEN);
    result_ = result_ && noparseBlock_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_NOPARSE_CLOSE);
    exit_section_(builder_, marker_, NOPARSE_BLOCK, result_);
    return result_;
  }

  // T_NOPARSE_TEXT*
  private static boolean noparseBlock_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noparseBlock_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, T_NOPARSE_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "noparseBlock_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // T_OUTER_HTML
  public static boolean outerHtml(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "outerHtml")) return false;
    if (!nextTokenIs(builder_, T_OUTER_HTML)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_OUTER_HTML);
    exit_section_(builder_, marker_, OUTER_HTML, result_);
    return result_;
  }

  /* ********************************************************** */
  // T_PHP_RAW_OPEN T_PHP_TEXT? T_PHP_RAW_CLOSE
  //            | T_PHP_ECHO_OPEN T_PHP_TEXT? T_PHP_ECHO_CLOSE
  public static boolean phpBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "phpBlock")) return false;
    if (!nextTokenIs(builder_, "<php block>", T_PHP_ECHO_OPEN, T_PHP_RAW_OPEN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PHP_BLOCK, "<php block>");
    result_ = phpBlock_0(builder_, level_ + 1);
    if (!result_) result_ = phpBlock_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // T_PHP_RAW_OPEN T_PHP_TEXT? T_PHP_RAW_CLOSE
  private static boolean phpBlock_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "phpBlock_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_PHP_RAW_OPEN);
    result_ = result_ && phpBlock_0_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_PHP_RAW_CLOSE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // T_PHP_TEXT?
  private static boolean phpBlock_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "phpBlock_0_1")) return false;
    consumeToken(builder_, T_PHP_TEXT);
    return true;
  }

  // T_PHP_ECHO_OPEN T_PHP_TEXT? T_PHP_ECHO_CLOSE
  private static boolean phpBlock_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "phpBlock_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_PHP_ECHO_OPEN);
    result_ = result_ && phpBlock_1_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_PHP_ECHO_CLOSE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // T_PHP_TEXT?
  private static boolean phpBlock_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "phpBlock_1_1")) return false;
    consumeToken(builder_, T_PHP_TEXT);
    return true;
  }

  /* ********************************************************** */
  // T_LDOUBLE statement_body* T_RDOUBLE
  public static boolean statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement")) return false;
    if (!nextTokenIs(builder_, T_LDOUBLE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_LDOUBLE);
    result_ = result_ && statement_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_RDOUBLE);
    exit_section_(builder_, marker_, STATEMENT, result_);
    return result_;
  }

  // statement_body*
  private static boolean statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!statement_body(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "statement_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // T_IDENT | T_STRING | T_NUMBER | T_OP | T_PIPE | T_SLASH | T_COLON | T_DOT | T_COMMA | T_SEMICOLON | T_EQUALS | T_ARROW | T_LPAREN | T_RPAREN | T_LBRACKET | T_RBRACKET | T_LBRACE | T_RBRACE | T_AT | T_DOLLAR | T_WS
  static boolean statement_body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_body")) return false;
    boolean result_;
    result_ = consumeToken(builder_, T_IDENT);
    if (!result_) result_ = consumeToken(builder_, T_STRING);
    if (!result_) result_ = consumeToken(builder_, T_NUMBER);
    if (!result_) result_ = consumeToken(builder_, T_OP);
    if (!result_) result_ = consumeToken(builder_, T_PIPE);
    if (!result_) result_ = consumeToken(builder_, T_SLASH);
    if (!result_) result_ = consumeToken(builder_, T_COLON);
    if (!result_) result_ = consumeToken(builder_, T_DOT);
    if (!result_) result_ = consumeToken(builder_, T_COMMA);
    if (!result_) result_ = consumeToken(builder_, T_SEMICOLON);
    if (!result_) result_ = consumeToken(builder_, T_EQUALS);
    if (!result_) result_ = consumeToken(builder_, T_ARROW);
    if (!result_) result_ = consumeToken(builder_, T_LPAREN);
    if (!result_) result_ = consumeToken(builder_, T_RPAREN);
    if (!result_) result_ = consumeToken(builder_, T_LBRACKET);
    if (!result_) result_ = consumeToken(builder_, T_RBRACKET);
    if (!result_) result_ = consumeToken(builder_, T_LBRACE);
    if (!result_) result_ = consumeToken(builder_, T_RBRACE);
    if (!result_) result_ = consumeToken(builder_, T_AT);
    if (!result_) result_ = consumeToken(builder_, T_DOLLAR);
    if (!result_) result_ = consumeToken(builder_, T_WS);
    return result_;
  }

}
