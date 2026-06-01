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
  // T_COMMENT_START T_COMMENT_TEXT? T_COMMENT_END
  public static boolean comment_block(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comment_block")) return false;
    if (!nextTokenIs(builder_, T_COMMENT_START)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_COMMENT_START);
    result_ = result_ && comment_block_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_COMMENT_END);
    exit_section_(builder_, marker_, COMMENT_BLOCK, result_);
    return result_;
  }

  // T_COMMENT_TEXT?
  private static boolean comment_block_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comment_block_1")) return false;
    consumeToken(builder_, T_COMMENT_TEXT);
    return true;
  }

  /* ********************************************************** */
  // outerHtml | tag_statement | comment_block
  static boolean item_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "item_")) return false;
    boolean result_;
    result_ = outerHtml(builder_, level_ + 1);
    if (!result_) result_ = tag_statement(builder_, level_ + 1);
    if (!result_) result_ = comment_block(builder_, level_ + 1);
    return result_;
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
  // (T_IDENTIFIER | T_STRING | T_NUMBER | T_MODIFIER_PIPE | T_EQUALS | T_SLASH | T_COLON | T_AT | T_OPERATOR)*
  public static boolean tag_content(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tag_content")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TAG_CONTENT, "<tag content>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!tag_content_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "tag_content", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  // T_IDENTIFIER | T_STRING | T_NUMBER | T_MODIFIER_PIPE | T_EQUALS | T_SLASH | T_COLON | T_AT | T_OPERATOR
  private static boolean tag_content_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tag_content_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, T_IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, T_STRING);
    if (!result_) result_ = consumeToken(builder_, T_NUMBER);
    if (!result_) result_ = consumeToken(builder_, T_MODIFIER_PIPE);
    if (!result_) result_ = consumeToken(builder_, T_EQUALS);
    if (!result_) result_ = consumeToken(builder_, T_SLASH);
    if (!result_) result_ = consumeToken(builder_, T_COLON);
    if (!result_) result_ = consumeToken(builder_, T_AT);
    if (!result_) result_ = consumeToken(builder_, T_OPERATOR);
    return result_;
  }

  /* ********************************************************** */
  // T_OPEN_BRACE tag_content T_CLOSE_BRACE
  public static boolean tag_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tag_statement")) return false;
    if (!nextTokenIs(builder_, T_OPEN_BRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, T_OPEN_BRACE);
    result_ = result_ && tag_content(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, T_CLOSE_BRACE);
    exit_section_(builder_, marker_, TAG_STATEMENT, result_);
    return result_;
  }

}
