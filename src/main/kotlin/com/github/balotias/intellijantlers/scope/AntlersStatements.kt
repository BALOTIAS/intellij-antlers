package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * The file's [AntlersStatement]s sorted by start offset, cached per-file on the PSI modification count.
 * The whole-tree traversal (`findChildrenOfType`) is the dominant per-completion cost; memoizing it lets
 * the ~4 statement-replay calls in one completion share a single traversal.
 */
object AntlersStatements {
    fun sortedIn(file: PsiFile): List<AntlersStatement> =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(
                PsiTreeUtil.findChildrenOfType(file, AntlersStatement::class.java)
                    .sortedBy { it.textRange.startOffset },
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
}
