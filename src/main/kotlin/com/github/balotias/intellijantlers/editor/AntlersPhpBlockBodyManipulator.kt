package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersFileType
import com.github.balotias.intellijantlers.psi.AntlersPhpBlockBody
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil

/**
 * Write-back for edits made inside the injected PHP fragment: rebuild a `phpBlockBody` by parsing a
 * synthetic `{{? … ?}}` with the new content and splicing it in. A `?}}` in the new content would
 * re-close the synthetic block early, so we round-trip-check and bail rather than truncate.
 */
class AntlersPhpBlockBodyManipulator : AbstractElementManipulator<AntlersPhpBlockBody>() {
    override fun handleContentChange(
        element: AntlersPhpBlockBody,
        range: TextRange,
        newContent: String
    ): AntlersPhpBlockBody {
        val old = element.text
        val body = old.substring(0, range.startOffset) + newContent + old.substring(range.endOffset)
        // No padding around $body — the body keeps its own surrounding spaces, so the reparse round-trips.
        val dummy = PsiFileFactory.getInstance(element.project)
            .createFileFromText("_php.antlers.html", AntlersFileType.INSTANCE, "{{?$body?}}")
        val newBody = PsiTreeUtil.findChildOfType(dummy, AntlersPhpBlockBody::class.java) ?: return element
        if (newBody.text != body) return element
        return element.replace(newBody) as AntlersPhpBlockBody
    }
}
