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

// States
%state ANTLERS
%state ANTLERS_COMMENT

// Macros
WHITE_SPACE=\s+
IDENTIFIER=[a-zA-Z_][a-zA-Z_0-9\-:]*
NUMBER=\d+
STRING=\"[^\"]*\"|'[^']*'

%%

<YYINITIAL> {
  "{{#"               { yybegin(ANTLERS_COMMENT); return AntlersTypes.T_COMMENT_START; }
  "{{"                { yybegin(ANTLERS); return AntlersTypes.T_OPEN_BRACE; }
  [^\{]+              { return AntlersTypes.T_OUTER_HTML; }
  "{"                 { return AntlersTypes.T_OUTER_HTML; }
}

<ANTLERS> {
  "}}"                { yybegin(YYINITIAL); return AntlersTypes.T_CLOSE_BRACE; }
  {WHITE_SPACE}       { return TokenType.WHITE_SPACE; }
  {IDENTIFIER}        { return AntlersTypes.T_IDENTIFIER; }
  {STRING}            { return AntlersTypes.T_STRING; }
  {NUMBER}            { return AntlersTypes.T_NUMBER; }
  "|"                 { return AntlersTypes.T_MODIFIER_PIPE; }
  "="                 { return AntlersTypes.T_EQUALS; }
  "/"                 { return AntlersTypes.T_SLASH; }
  ":"                 { return AntlersTypes.T_COLON; }
  "@"                 { return AntlersTypes.T_AT; }
  "==" | "!=" | "<" | ">" | "<=" | ">=" | "&&" | "||" | "+" | "-" | "*" | "%" | "?" { return AntlersTypes.T_OPERATOR; }
  .                   { return TokenType.BAD_CHARACTER; }
}

<ANTLERS_COMMENT> {
  "#}}"               { yybegin(YYINITIAL); return AntlersTypes.T_COMMENT_END; }
  [^#]+               { return AntlersTypes.T_COMMENT_TEXT; }
  "#"                 { return AntlersTypes.T_COMMENT_TEXT; }
}
