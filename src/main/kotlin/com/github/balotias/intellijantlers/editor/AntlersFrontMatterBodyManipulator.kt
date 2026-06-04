package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.AntlersFileType
import com.github.balotias.intellijantlers.psi.AntlersFrontMatterBody
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil

/**
 * Write-back for edits made inside the injected YAML fragment: rebuild a `frontMatterBody` by parsing a
 * synthetic front matter with the new content and splicing it in. (Direct editing in the host document
 * does not use this; it is for injected-fragment / quick-fix edits.)
 *
 * If the new content contains a line that is itself a `---` fence, the re-parse would close the synthetic
 * front matter early and silently truncate the body — so we round-trip-check the rebuilt body and bail
 * (returning the element unchanged) rather than corrupt it.
 */
class AntlersFrontMatterBodyManipulator : AbstractElementManipulator<AntlersFrontMatterBody>() {
    override fun handleContentChange(
        element: AntlersFrontMatterBody,
        range: TextRange,
        newContent: String
    ): AntlersFrontMatterBody {
        val old = element.text
        val updated = old.substring(0, range.startOffset) + newContent + old.substring(range.endOffset)
        val body = if (updated.endsWith("\n")) updated else "$updated\n"
        val dummy = PsiFileFactory.getInstance(element.project)
            .createFileFromText("_fm.antlers.html", AntlersFileType.INSTANCE, "---\n$body---\n")
        val newBody = PsiTreeUtil.findChildOfType(dummy, AntlersFrontMatterBody::class.java) ?: return element
        // A bare `---` line in the new content would have re-closed the synthetic fence early; refuse to
        // splice a body that doesn't round-trip rather than drop the trailing lines.
        if (newBody.text != body) return element
        return element.replace(newBody) as AntlersFrontMatterBody
    }
}
