package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.AntlersIcons
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext

class AntlersCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        // Only contribute Antlers tags when the caret is inside an Antlers `{{ ... }}` expression.
        // Outside braces this leaves the field clear for HTML/CSS completion.
        if (!isInsideAntlersTag(parameters)) {
            return
        }

        // Native Tags
        for (tag in StatamicNativeTags.TAGS) {
            val isPair = tag in StatamicNativeTags.PAIR_TAGS
            result.addElement(
                LookupElementBuilder.create(tag)
                    .withIcon(AntlersIcons.FILE)
                    .withTypeText(if (isPair) "Native Block Tag" else "Native Tag")
                    .withInsertHandler(AntlersTagInsertHandler(isPair))
            )
        }

        // Custom Tags (discovered from the project's PHP Tags classes)
        val project = parameters.editor.project
        if (project != null) {
            val customTags = com.github.balotias.intellijantlers.catalog.scan.TagScanner.scan(project)
            for (customTag in customTags) {
                result.addElement(
                    LookupElementBuilder.create(customTag)
                        .withIcon(AntlersIcons.FILE)
                        .withTypeText("Custom Tag")
                        .withBoldness(true)
                        .withInsertHandler(AntlersTagInsertHandler(isPair = false))
                )
            }
        }
    }

    /**
     * True when [offset][CompletionParameters.getOffset] sits inside an unclosed `{{ ... }}` —
     * i.e. the nearest `{{` before the caret is closer than the nearest `}}`.
     */
    private fun isInsideAntlersTag(parameters: CompletionParameters): Boolean {
        val offset = parameters.offset
        val text = parameters.editor.document.charsSequence
        val before = text.subSequence(0, offset.coerceIn(0, text.length)).toString()
        val lastOpen = before.lastIndexOf("{{")
        val lastClose = before.lastIndexOf("}}")
        // Exclude Antlers comments `{{# ... #}}`.
        val isComment = lastOpen >= 0 && before.startsWith("{{#", lastOpen)
        return lastOpen > lastClose && !isComment
    }
}
