package com.github.balotias.intellijantlers.editor

import com.github.balotias.intellijantlers.highlighting.AntlersSyntaxHighlighter
import com.github.balotias.intellijantlers.parser.AntlersFile
import com.github.balotias.intellijantlers.view.TextSpan
import com.github.balotias.intellijantlers.view.ViewFrontMatterService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * YAML-style highlighting of a view's `---…---` front-matter block (B-visual). The block flows to HTML
 * as plain text; this overlays colors on the fences/keys/values (the same INFORMATION-overlay mechanism
 * as the other Antlers annotators). Coloring only — not a real YAML editor (see spec: B-real follow-up).
 */
class AntlersFrontMatterHighlightAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is AntlersFile) return                    // run once per file
        val fm = ViewFrontMatterService.getInstance(element.project).frontMatter(element) ?: return
        paint(holder, fm.openFence, AntlersSyntaxHighlighter.FRONTMATTER_FENCE)
        paint(holder, fm.closeFence, AntlersSyntaxHighlighter.FRONTMATTER_FENCE)
        for (e in fm.entries) {
            paint(holder, e.key, AntlersSyntaxHighlighter.FRONTMATTER_KEY)
            e.value?.let { paint(holder, it, AntlersSyntaxHighlighter.FRONTMATTER_VALUE) }
        }
    }

    private fun paint(holder: AnnotationHolder, span: TextSpan, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(span.start, span.end))
            .textAttributes(key)
            .create()
    }
}
