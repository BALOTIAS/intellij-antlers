package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.AntlersIcons
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.SystemVariables
import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext

/** Classifies the caret context and offers the matching Antlers completions. */
class AntlersCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val info = AntlersCompletionContext.classify(parameters.position)
        if (info.kind == AntlersCompletionKind.NONE) return

        val project = parameters.editor.project ?: return
        val catalog = AntlersCatalogService.getInstance(project)

        when (info.kind) {
            AntlersCompletionKind.TAG_NAME -> {
                for (tag in catalog.tags()) {
                    result.addElement(
                        LookupElementBuilder.create(tag.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(if (tag.isPair) "Tag (block)" else "Tag")
                            .withTailText(if (tag.description.isNotBlank()) "  ${tag.description}" else null, true)
                            .withInsertHandler(AntlersTagInsertHandler(tag.isPair))
                    )
                }
                val seen = catalog.tags().mapTo(mutableSetOf()) { it.name }
                for (field in BlueprintService.getInstance(project).fields()) {
                    if (seen.add(field.handle)) {
                        result.addElement(
                            LookupElementBuilder.create(field.handle)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("Field")
                                .withTailText(if (field.display.isNotBlank()) "  ${field.display}" else null, true)
                        )
                    }
                }
                for (sv in SystemVariables.ALL) {
                    if (seen.add(sv.name)) {
                        result.addElement(
                            LookupElementBuilder.create(sv.name)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("Variable")
                                .withTailText("  ${sv.description}", true)
                        )
                    }
                }
            }

            AntlersCompletionKind.TAG_METHOD ->
                catalog.tag(info.tagHead ?: "")?.methods?.forEach { m ->
                    result.addElement(
                        LookupElementBuilder.create(m).withIcon(AntlersIcons.FILE).withTypeText("Method")
                    )
                }

            AntlersCompletionKind.PARAMETER ->
                catalog.tag(info.tagHead ?: "")?.parameters?.forEach { p ->
                    result.addElement(
                        LookupElementBuilder.create(p.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(if (p.required) "Param*" else "Param")
                            .withTailText(if (p.description.isNotBlank()) "  ${p.description}" else null, true)
                            .withInsertHandler(ParameterInsertHandler)
                    )
                }

            AntlersCompletionKind.MODIFIER ->
                for (mod in catalog.modifiers()) {
                    result.addElement(
                        LookupElementBuilder.create(mod.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText("Modifier")
                            .withTailText(if (mod.description.isNotBlank()) "  ${mod.description}" else null, true)
                            .withInsertHandler(ModifierInsertHandler(mod.takesArguments))
                    )
                }

            AntlersCompletionKind.NONE -> {}
        }
    }
}
