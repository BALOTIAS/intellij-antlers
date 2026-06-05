package com.github.balotias.intellijantlers.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.github.balotias.intellijantlers.psi.AntlersTypes;
import com.intellij.psi.TokenType;

%%

%class _AntlersLexer
%public
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

%{
  public _AntlersLexer() {
    this((java.io.Reader)null);
  }
%}

%state EXPR
%state COMMENT
%state PHP_RAW
%state PHP_ECHO
%state PHP_TAG
%state NOPARSE
%state CONTENT
%state FRONTMATTER

WS=\s+
NL=\r\n|\n|\r
IDENT=[a-zA-Z_]([a-zA-Z_0-9]|-[a-zA-Z_0-9])*
NUMBER=[0-9]+(\.[0-9]+)?
STRING=\"([^\"\\]|\\.)*\"|'([^'\\]|\\.)*'
NOPARSE_OPEN="{{"[ \t]*"noparse"[ \t]*"}}"
NOPARSE_CLOSE="{{"[ \t]*"/noparse"[ \t]*"}}"
OP="==="|"!=="|"<=>"|"=="|"!="|">="|"<="|">"|"<"|"&&"|"||"|"!"|"???"|"??"|"?="|"?"|"+="|"-="|"*="|"/="|"%="|"**"|"+"|"-"|"*"|"%"

%%

<YYINITIAL> {
  "---" [ \t]* {NL}   { yybegin(FRONTMATTER); return AntlersTypes.T_FRONTMATTER_FENCE; }
  [^]                 { yybegin(CONTENT); yypushback(1); }
}

<FRONTMATTER> {
  "---" [ \t]* {NL}   { yybegin(CONTENT); return AntlersTypes.T_FRONTMATTER_FENCE; }
  "---" [ \t]*        { yybegin(CONTENT); return AntlersTypes.T_FRONTMATTER_FENCE; }
  [^\r\n]* {NL}       { return AntlersTypes.T_FRONTMATTER_TEXT; }
  [^\r\n]+            { return AntlersTypes.T_FRONTMATTER_TEXT; }
}

<CONTENT> {
  {NOPARSE_OPEN}            { yybegin(NOPARSE); return AntlersTypes.T_NOPARSE_OPEN; }
  "@{{"                     { return AntlersTypes.T_OUTER_HTML; }
  "{{#"                     { yybegin(COMMENT);  return AntlersTypes.T_COMMENT_OPEN; }
  "{{?"                     { yybegin(PHP_RAW);  return AntlersTypes.T_PHP_RAW_OPEN; }
  "{{$"                     { yybegin(PHP_ECHO); return AntlersTypes.T_PHP_ECHO_OPEN; }
  "{{"                      { yybegin(EXPR);     return AntlersTypes.T_LDOUBLE; }
  "<?php"                   { yybegin(PHP_TAG);  return AntlersTypes.T_PHP_TAG_OPEN; }
  "<?="                     { yybegin(PHP_TAG);  return AntlersTypes.T_PHP_ECHO_TAG_OPEN; }
  ( [^{@<] | "<" [^?{@<] )+ { return AntlersTypes.T_OUTER_HTML; }
  "<"                       { return AntlersTypes.T_OUTER_HTML; }
  "@"                       { return AntlersTypes.T_OUTER_HTML; }
  "{"                       { return AntlersTypes.T_OUTER_HTML; }
}

<EXPR> {
  "}}"                { yybegin(CONTENT); return AntlersTypes.T_RDOUBLE; }
  {WS}                { return AntlersTypes.T_WS; }
  {STRING}            { return AntlersTypes.T_STRING; }
  {NUMBER}            { return AntlersTypes.T_NUMBER; }
  "=>"                { return AntlersTypes.T_ARROW; }
  {OP}                { return AntlersTypes.T_OP; }
  "|"                 { return AntlersTypes.T_PIPE; }
  "/"                 { return AntlersTypes.T_SLASH; }
  ":"                 { return AntlersTypes.T_COLON; }
  "."                 { return AntlersTypes.T_DOT; }
  ","                 { return AntlersTypes.T_COMMA; }
  ";"                 { return AntlersTypes.T_SEMICOLON; }
  "="                 { return AntlersTypes.T_EQUALS; }
  "("                 { return AntlersTypes.T_LPAREN; }
  ")"                 { return AntlersTypes.T_RPAREN; }
  "["                 { return AntlersTypes.T_LBRACKET; }
  "]"                 { return AntlersTypes.T_RBRACKET; }
  "{"                 { return AntlersTypes.T_LBRACE; }
  "}"                 { return AntlersTypes.T_RBRACE; }
  "@"                 { return AntlersTypes.T_AT; }
  "$"                 { return AntlersTypes.T_DOLLAR; }
  {IDENT}             { return AntlersTypes.T_IDENT; }
  .                   { return TokenType.BAD_CHARACTER; }
}

<COMMENT> {
  "#}}"               { yybegin(CONTENT); return AntlersTypes.T_COMMENT_CLOSE; }
  [^#]+               { return AntlersTypes.T_COMMENT_TEXT; }
  "#"                 { return AntlersTypes.T_COMMENT_TEXT; }
}

<PHP_RAW> {
  "?}}"               { yybegin(CONTENT); return AntlersTypes.T_PHP_RAW_CLOSE; }
  [^?]+               { return AntlersTypes.T_PHP_TEXT; }
  "?"                 { return AntlersTypes.T_PHP_TEXT; }
}

<PHP_ECHO> {
  "$}}"               { yybegin(CONTENT); return AntlersTypes.T_PHP_ECHO_CLOSE; }
  [^$]+               { return AntlersTypes.T_PHP_TEXT; }
  "$"                 { return AntlersTypes.T_PHP_TEXT; }
}

<PHP_TAG> {
  "?>"                { yybegin(CONTENT); return AntlersTypes.T_PHP_TAG_CLOSE; }
  [^?]+               { return AntlersTypes.T_PHP_TEXT; }
  "?"                 { return AntlersTypes.T_PHP_TEXT; }
}

<NOPARSE> {
  {NOPARSE_CLOSE}     { yybegin(CONTENT); return AntlersTypes.T_NOPARSE_CLOSE; }
  [^{]+               { return AntlersTypes.T_NOPARSE_TEXT; }
  "{"                 { return AntlersTypes.T_NOPARSE_TEXT; }
}
