// This is a generated file. Not intended for manual editing.
package com.github.balotias.intellijantlers.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.github.balotias.intellijantlers.psi.AntlersTypes.*;
import static com.github.balotias.intellijantlers.parser.AntlersParserUtil.*;
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
  // node_*
  static boolean antlersFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "antlersFile")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!node_(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "antlersFile", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // exprToken_ (T_COMMA exprToken_)*
  static boolean argList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = exprToken_(builder_, level_ + 1);
    result_ = result_ && argList_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (T_COMMA exprToken_)*
  private static boolean argList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!argList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "argList_1", pos_)) break;
    }
    return true;
  }

  // T_COMMA exprToken_
  private static boolean argList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_COMMA);
    result_ = result_ && exprToken_(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // closingTag | condition | expr_
  static boolean body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "body")) return false;
    boolean result_;
    result_ = closingTag(builder_, level_ + 1);
    if (!result_) result_ = condition(builder_, level_ + 1);
    if (!result_) result_ = expr_(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // T_COLON T_DOLLAR? T_IDENT (T_EQUALS paramValue)?
  //                           | T_DOLLAR T_IDENT
  static boolean boundParameter_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundParameter_")) return false;
    if (!nextTokenIs(builder_, "", T_COLON, T_DOLLAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = boundParameter__0(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, T_DOLLAR, T_IDENT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // T_COLON T_DOLLAR? T_IDENT (T_EQUALS paramValue)?
  private static boolean boundParameter__0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundParameter__0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_COLON);
    result_ = result_ && boundParameter__0_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_IDENT);
    result_ = result_ && boundParameter__0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // T_DOLLAR?
  private static boolean boundParameter__0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundParameter__0_1")) return false;
    consumeToken(builder_, T_DOLLAR);
    return true;
  }

  // (T_EQUALS paramValue)?
  private static boolean boundParameter__0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundParameter__0_3")) return false;
    boundParameter__0_3_0(builder_, level_ + 1);
    return true;
  }

  // T_EQUALS paramValue
  private static boolean boundParameter__0_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundParameter__0_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_EQUALS);
    result_ = result_ && paramValue(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // T_LBRACKET groupToken_* T_RBRACKET
  public static boolean bracketAccess(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bracketAccess")) return false;
    if (!nextTokenIs(builder_, T_LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_LBRACKET);
    result_ = result_ && bracketAccess_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_RBRACKET);
    exit_section_(builder_, marker_, BRACKET_ACCESS, result_);
    return result_;
  }

  // groupToken_*
  private static boolean bracketAccess_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bracketAccess_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!groupToken_(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bracketAccess_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // T_SLASH namePath?
  public static boolean closingTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "closingTag")) return false;
    if (!nextTokenIs(builder_, T_SLASH)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_SLASH);
    result_ = result_ && closingTag_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, CLOSING_TAG, result_);
    return result_;
  }

  // namePath?
  private static boolean closingTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "closingTag_1")) return false;
    namePath(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // T_COMMENT_OPEN T_COMMENT_TEXT? T_COMMENT_CLOSE
  public static boolean comment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comment")) return false;
    if (!nextTokenIs(builder_, T_COMMENT_OPEN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMMENT, null);
    result_ = consumeToken(builder_, T_COMMENT_OPEN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, comment_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, T_COMMENT_CLOSE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // T_COMMENT_TEXT?
  private static boolean comment_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comment_1")) return false;
    consumeToken(builder_, T_COMMENT_TEXT);
    return true;
  }

  /* ********************************************************** */
  // <<atConditionKeyword>> conditionKeyword exprToken_*
  public static boolean condition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "condition")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONDITION, "<condition>");
    result_ = atConditionKeyword(builder_, level_ + 1);
    result_ = result_ && conditionKeyword(builder_, level_ + 1);
    result_ = result_ && condition_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // exprToken_*
  private static boolean condition_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "condition_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!exprToken_(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "condition_2", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // T_IDENT
  static boolean conditionKeyword(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, T_IDENT);
  }

  /* ********************************************************** */
  // T_OP | T_STRING | T_NUMBER | T_ARROW | T_DOT | T_COLON | T_COMMA | T_SEMICOLON
  //   | T_EQUALS | T_DOLLAR | T_AT | T_LPAREN | T_RPAREN | T_LBRACKET | T_RBRACKET
  //   | T_LBRACE | T_RBRACE | T_IDENT | T_SLASH
  static boolean exprToken_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "exprToken_")) return false;
    boolean result_;
    result_ = consumeToken(builder_, T_OP);
    if (!result_) result_ = consumeToken(builder_, T_STRING);
    if (!result_) result_ = consumeToken(builder_, T_NUMBER);
    if (!result_) result_ = consumeToken(builder_, T_ARROW);
    if (!result_) result_ = consumeToken(builder_, T_DOT);
    if (!result_) result_ = consumeToken(builder_, T_COLON);
    if (!result_) result_ = consumeToken(builder_, T_COMMA);
    if (!result_) result_ = consumeToken(builder_, T_SEMICOLON);
    if (!result_) result_ = consumeToken(builder_, T_EQUALS);
    if (!result_) result_ = consumeToken(builder_, T_DOLLAR);
    if (!result_) result_ = consumeToken(builder_, T_AT);
    if (!result_) result_ = consumeToken(builder_, T_LPAREN);
    if (!result_) result_ = consumeToken(builder_, T_RPAREN);
    if (!result_) result_ = consumeToken(builder_, T_LBRACKET);
    if (!result_) result_ = consumeToken(builder_, T_RBRACKET);
    if (!result_) result_ = consumeToken(builder_, T_LBRACE);
    if (!result_) result_ = consumeToken(builder_, T_RBRACE);
    if (!result_) result_ = consumeToken(builder_, T_IDENT);
    if (!result_) result_ = consumeToken(builder_, T_SLASH);
    return result_;
  }

  /* ********************************************************** */
  // namePath tail_* | tail_+
  static boolean expr_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expr_")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expr__0(builder_, level_ + 1);
    if (!result_) result_ = expr__1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // namePath tail_*
  private static boolean expr__0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expr__0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = namePath(builder_, level_ + 1);
    result_ = result_ && expr__0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // tail_*
  private static boolean expr__0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expr__0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!tail_(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "expr__0_1", pos_)) break;
    }
    return true;
  }

  // tail_+
  private static boolean expr__1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expr__1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = tail_(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!tail_(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "expr__1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // T_OP | T_STRING | T_NUMBER | T_ARROW | T_DOT | T_COLON | T_COMMA | T_SEMICOLON
  //   | T_EQUALS | T_DOLLAR | T_AT | T_LPAREN | T_RPAREN | T_LBRACKET
  //   | T_LBRACE | T_IDENT | T_SLASH
  static boolean groupToken_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "groupToken_")) return false;
    boolean result_;
    result_ = consumeToken(builder_, T_OP);
    if (!result_) result_ = consumeToken(builder_, T_STRING);
    if (!result_) result_ = consumeToken(builder_, T_NUMBER);
    if (!result_) result_ = consumeToken(builder_, T_ARROW);
    if (!result_) result_ = consumeToken(builder_, T_DOT);
    if (!result_) result_ = consumeToken(builder_, T_COLON);
    if (!result_) result_ = consumeToken(builder_, T_COMMA);
    if (!result_) result_ = consumeToken(builder_, T_SEMICOLON);
    if (!result_) result_ = consumeToken(builder_, T_EQUALS);
    if (!result_) result_ = consumeToken(builder_, T_DOLLAR);
    if (!result_) result_ = consumeToken(builder_, T_AT);
    if (!result_) result_ = consumeToken(builder_, T_LPAREN);
    if (!result_) result_ = consumeToken(builder_, T_RPAREN);
    if (!result_) result_ = consumeToken(builder_, T_LBRACKET);
    if (!result_) result_ = consumeToken(builder_, T_LBRACE);
    if (!result_) result_ = consumeToken(builder_, T_IDENT);
    if (!result_) result_ = consumeToken(builder_, T_SLASH);
    return result_;
  }

  /* ********************************************************** */
  // T_PIPE T_IDENT (T_LPAREN argList? T_RPAREN)?
  public static boolean modifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "modifier")) return false;
    if (!nextTokenIs(builder_, T_PIPE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, T_PIPE, T_IDENT);
    result_ = result_ && modifier_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, MODIFIER, result_);
    return result_;
  }

  // (T_LPAREN argList? T_RPAREN)?
  private static boolean modifier_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "modifier_2")) return false;
    modifier_2_0(builder_, level_ + 1);
    return true;
  }

  // T_LPAREN argList? T_RPAREN
  private static boolean modifier_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "modifier_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_LPAREN);
    result_ = result_ && modifier_2_0_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // argList?
  private static boolean modifier_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "modifier_2_0_1")) return false;
    argList(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // pathSegment ((T_COLON | T_DOT) pathSegment | bracketAccess)*
  public static boolean namePath(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namePath")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NAME_PATH, "<name path>");
    result_ = pathSegment(builder_, level_ + 1);
    result_ = result_ && namePath_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ((T_COLON | T_DOT) pathSegment | bracketAccess)*
  private static boolean namePath_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namePath_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!namePath_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "namePath_1", pos_)) break;
    }
    return true;
  }

  // (T_COLON | T_DOT) pathSegment | bracketAccess
  private static boolean namePath_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namePath_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = namePath_1_0_0(builder_, level_ + 1);
    if (!result_) result_ = bracketAccess(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (T_COLON | T_DOT) pathSegment
  private static boolean namePath_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namePath_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = namePath_1_0_0_0(builder_, level_ + 1);
    result_ = result_ && pathSegment(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // T_COLON | T_DOT
  private static boolean namePath_1_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namePath_1_0_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, T_COLON);
    if (!result_) result_ = consumeToken(builder_, T_DOT);
    return result_;
  }

  /* ********************************************************** */
  // comment | phpBlock | noparseBlock | statement | outerHtml
  static boolean node_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "node_")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = comment(builder_, level_ + 1);
    if (!result_) result_ = phpBlock(builder_, level_ + 1);
    if (!result_) result_ = noparseBlock(builder_, level_ + 1);
    if (!result_) result_ = statement(builder_, level_ + 1);
    if (!result_) result_ = outerHtml(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
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
  // T_STRING | (T_LBRACE groupToken_* T_RBRACE) | T_NUMBER | namePath
  static boolean paramValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramValue")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_STRING);
    if (!result_) result_ = paramValue_1(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, T_NUMBER);
    if (!result_) result_ = namePath(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // T_LBRACE groupToken_* T_RBRACE
  private static boolean paramValue_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramValue_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_LBRACE);
    result_ = result_ && paramValue_1_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // groupToken_*
  private static boolean paramValue_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramValue_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!groupToken_(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "paramValue_1_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // boundParameter_ | staticParameter_
  public static boolean parameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARAMETER, "<parameter>");
    result_ = boundParameter_(builder_, level_ + 1);
    if (!result_) result_ = staticParameter_(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // T_DOLLAR? (T_IDENT | T_NUMBER)
  static boolean pathSegment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pathSegment")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = pathSegment_0(builder_, level_ + 1);
    result_ = result_ && pathSegment_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // T_DOLLAR?
  private static boolean pathSegment_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pathSegment_0")) return false;
    consumeToken(builder_, T_DOLLAR);
    return true;
  }

  // T_IDENT | T_NUMBER
  private static boolean pathSegment_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pathSegment_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, T_IDENT);
    if (!result_) result_ = consumeToken(builder_, T_NUMBER);
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
  // T_LDOUBLE body? T_RDOUBLE
  public static boolean statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATEMENT, "<statement>");
    result_ = consumeToken(builder_, T_LDOUBLE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, statement_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, T_RDOUBLE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, AntlersParser::statement_recover);
    return result_ || pinned_;
  }

  // body?
  private static boolean statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_1")) return false;
    body(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // !(T_RDOUBLE | T_LDOUBLE | T_OUTER_HTML | T_COMMENT_OPEN | T_NOPARSE_OPEN | T_PHP_RAW_OPEN | T_PHP_ECHO_OPEN)
  static boolean statement_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !statement_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // T_RDOUBLE | T_LDOUBLE | T_OUTER_HTML | T_COMMENT_OPEN | T_NOPARSE_OPEN | T_PHP_RAW_OPEN | T_PHP_ECHO_OPEN
  private static boolean statement_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, T_RDOUBLE);
    if (!result_) result_ = consumeToken(builder_, T_LDOUBLE);
    if (!result_) result_ = consumeToken(builder_, T_OUTER_HTML);
    if (!result_) result_ = consumeToken(builder_, T_COMMENT_OPEN);
    if (!result_) result_ = consumeToken(builder_, T_NOPARSE_OPEN);
    if (!result_) result_ = consumeToken(builder_, T_PHP_RAW_OPEN);
    if (!result_) result_ = consumeToken(builder_, T_PHP_ECHO_OPEN);
    return result_;
  }

  /* ********************************************************** */
  // T_IDENT T_EQUALS paramValue
  static boolean staticParameter_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "staticParameter_")) return false;
    if (!nextTokenIs(builder_, T_IDENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, T_IDENT, T_EQUALS);
    result_ = result_ && paramValue(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // parameter | modifier | exprToken_
  static boolean tail_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tail_")) return false;
    boolean result_;
    result_ = parameter(builder_, level_ + 1);
    if (!result_) result_ = modifier(builder_, level_ + 1);
    if (!result_) result_ = exprToken_(builder_, level_ + 1);
    return result_;
  }

}
