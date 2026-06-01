// This is a generated file. Not intended for manual editing.
package com.github.balotias.intellijantlers.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class AntlersVisitor extends PsiElementVisitor {

  public void visitComment(@NotNull AntlersComment o) {
    visitPsiElement(o);
  }

  public void visitOuterHtml(@NotNull AntlersOuterHtml o) {
    visitPsiElement(o);
  }

  public void visitStatement(@NotNull AntlersStatement o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
