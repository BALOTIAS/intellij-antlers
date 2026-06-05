// This is a generated file. Not intended for manual editing.
package com.github.balotias.intellijantlers.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AntlersPhpBlock extends PsiElement {

  @Nullable
  AntlersPhpEchoBlock getPhpEchoBlock();

  @Nullable
  AntlersPhpEchoTagBlock getPhpEchoTagBlock();

  @Nullable
  AntlersPhpRawBlock getPhpRawBlock();

  @Nullable
  AntlersPhpTagBlock getPhpTagBlock();

}
