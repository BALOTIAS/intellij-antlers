// This is a generated file. Not intended for manual editing.
package com.github.balotias.intellijantlers.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.github.balotias.intellijantlers.psi.AntlersTypes.*;
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin;
import com.github.balotias.intellijantlers.psi.*;

public class AntlersNamePathImpl extends AntlersNamePathMixin implements AntlersNamePath {

  public AntlersNamePathImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AntlersVisitor visitor) {
    visitor.visitNamePath(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AntlersVisitor) accept((AntlersVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<AntlersBracketAccess> getBracketAccessList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AntlersBracketAccess.class);
  }

}
