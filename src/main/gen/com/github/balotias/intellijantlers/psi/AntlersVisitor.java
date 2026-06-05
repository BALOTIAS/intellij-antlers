// This is a generated file. Not intended for manual editing.
package com.github.balotias.intellijantlers.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;

public class AntlersVisitor extends PsiElementVisitor {

  public void visitBracketAccess(@NotNull AntlersBracketAccess o) {
    visitPsiElement(o);
  }

  public void visitClosingTag(@NotNull AntlersClosingTag o) {
    visitPsiElement(o);
  }

  public void visitComment(@NotNull AntlersComment o) {
    visitPsiElement(o);
  }

  public void visitCondition(@NotNull AntlersCondition o) {
    visitPsiElement(o);
  }

  public void visitFrontMatter(@NotNull AntlersFrontMatter o) {
    visitPsiElement(o);
  }

  public void visitFrontMatterBody(@NotNull AntlersFrontMatterBody o) {
    visitPsiLanguageInjectionHost(o);
  }

  public void visitModifier(@NotNull AntlersModifier o) {
    visitPsiElement(o);
  }

  public void visitNamePath(@NotNull AntlersNamePath o) {
    visitPsiElement(o);
  }

  public void visitNoparseBlock(@NotNull AntlersNoparseBlock o) {
    visitPsiElement(o);
  }

  public void visitOuterHtml(@NotNull AntlersOuterHtml o) {
    visitPsiElement(o);
  }

  public void visitParameter(@NotNull AntlersParameter o) {
    visitPsiElement(o);
  }

  public void visitPhpBlock(@NotNull AntlersPhpBlock o) {
    visitPsiElement(o);
  }

  public void visitPhpBlockBody(@NotNull AntlersPhpBlockBody o) {
    visitPsiLanguageInjectionHost(o);
  }

  public void visitPhpEchoBlock(@NotNull AntlersPhpEchoBlock o) {
    visitPsiElement(o);
  }

  public void visitPhpEchoTagBlock(@NotNull AntlersPhpEchoTagBlock o) {
    visitPsiElement(o);
  }

  public void visitPhpRawBlock(@NotNull AntlersPhpRawBlock o) {
    visitPsiElement(o);
  }

  public void visitPhpTagBlock(@NotNull AntlersPhpTagBlock o) {
    visitPsiElement(o);
  }

  public void visitStatement(@NotNull AntlersStatement o) {
    visitPsiElement(o);
  }

  public void visitPsiLanguageInjectionHost(@NotNull PsiLanguageInjectionHost o) {
    visitElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
