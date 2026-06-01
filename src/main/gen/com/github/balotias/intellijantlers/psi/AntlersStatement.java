// This is a generated file. Not intended for manual editing.
package com.github.balotias.intellijantlers.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AntlersStatement extends PsiElement {

  @Nullable
  AntlersClosingTag getClosingTag();

  @Nullable
  AntlersCondition getCondition();

  @NotNull
  List<AntlersModifier> getModifierList();

  @Nullable
  AntlersNamePath getNamePath();

  @NotNull
  List<AntlersParameter> getParameterList();

}
