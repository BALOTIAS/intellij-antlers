package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersFileType
import com.github.balotias.intellijantlers.psi.AntlersPhpBlockBody
import com.github.balotias.intellijantlers.psi.AntlersPhpEchoBlock
import com.github.balotias.intellijantlers.psi.AntlersPhpEchoTagBlock
import com.github.balotias.intellijantlers.psi.AntlersPhpTagBlock
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil

/**
 * Write-back for edits made inside the injected PHP fragment: rebuild a `phpBlockBody` by reparsing the
 * new content wrapped in the host block's OWN delimiters and splicing it in. New content containing the
 * block's close sequence (`?}}`, `$}}`, or `?>`) would re-close the block early, so we round-trip-check
 * against the same delimiters and bail rather than truncate/corrupt — for every block kind, including the
 * literal `<?php … ?>` / `<?= … ?>` tags.
 */
class AntlersPhpBlockBodyManipulator : AbstractElementManipulator<AntlersPhpBlockBody>() {
    override fun handleContentChange(
        element: AntlersPhpBlockBody,
        range: TextRange,
        newContent: String
    ): AntlersPhpBlockBody {
        val old = element.text
        val body = old.substring(0, range.startOffset) + newContent + old.substring(range.endOffset)
        val (open, close) = delimitersFor(element)
        // No padding around $body — the body keeps its own surrounding spaces, so the reparse round-trips.
        val dummy = PsiFileFactory.getInstance(element.project)
            .createFileFromText("_php.antlers.html", AntlersFileType.INSTANCE, "$open$body$close")
        val newBody = PsiTreeUtil.findChildOfType(dummy, AntlersPhpBlockBody::class.java) ?: return element
        if (newBody.text != body) return element
        return element.replace(newBody) as AntlersPhpBlockBody
    }

    /** The open/close delimiters of the host block, so the reparse uses (and the guard catches) its own close. */
    private fun delimitersFor(element: AntlersPhpBlockBody): Pair<String, String> = when (element.parent) {
        is AntlersPhpEchoBlock -> "{{\$" to "\$}}"
        is AntlersPhpTagBlock -> "<?php" to "?>"
        is AntlersPhpEchoTagBlock -> "<?=" to "?>"
        else -> "{{?" to "?}}"   // AntlersPhpRawBlock
    }
}
