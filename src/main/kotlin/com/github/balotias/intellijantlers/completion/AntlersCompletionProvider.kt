package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.AntlersIcons
import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.SystemVariables
import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.catalog.FieldtypeProperties
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.github.balotias.intellijantlers.scope.AntlersMemberResolver
import com.github.balotias.intellijantlers.scope.AntlersScopeResolver
import com.github.balotias.intellijantlers.scope.LoopVariables
import com.github.balotias.intellijantlers.scope.NavVariables
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
                val scopedFields = AntlersFieldContext.fieldsInScope(parameters.position, project)
                val fields = scopedFields ?: BlueprintService.getInstance(project).fields()
                for (field in fields) {
                    if (seen.add(field.handle)) {
                        result.addElement(
                            LookupElementBuilder.create(field.handle)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("Field")
                                .withTailText(if (field.display.isNotBlank()) "  ${field.display}" else null, true)
                        )
                    }
                }
                // Loop-meta vars only inside an actual iterating tag (E1) — NOT for a page match.
                val scopes = AntlersScopeResolver.scopesAt(parameters.position)
                if (scopes.isNotEmpty()) {
                    for (lv in LoopVariables.ALL) {
                        if (seen.add(lv.name)) {
                            result.addElement(
                                LookupElementBuilder.create(lv.name)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Loop")
                                    .withTailText("  ${lv.description}", true)
                            )
                        }
                    }
                }
                // Nav-tree vars only inside a {{ nav … }} scope.
                if (scopes.any { it.navMeta }) {
                    for (nv in NavVariables.ALL) {
                        if (seen.add(nv.name)) {
                            result.addElement(
                                LookupElementBuilder.create(nv.name)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Nav")
                                    .withTailText("  ${nv.description}", true)
                            )
                        }
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

            AntlersCompletionKind.TAG_METHOD -> {
                val tag = catalog.tag(info.tagHead ?: "")
                if (tag != null) {
                    tag.methods.forEach { m ->
                        result.addElement(
                            LookupElementBuilder.create(m).withIcon(AntlersIcons.FILE).withTypeText("Method")
                        )
                    }
                } else {
                    // Not a catalog tag: a blueprint field member access via colon (e.g. {{ group:sub }}).
                    AntlersMemberResolver.resolveField(parameters.position, info.pathPrefix, project)
                        ?.let { offerMembers(it, project, result) }
                }
            }

            AntlersCompletionKind.FIELD_PATH ->
                AntlersMemberResolver.resolveField(parameters.position, info.pathPrefix, project)
                    ?.let { offerMembers(it, project, result) }

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

            AntlersCompletionKind.PARAMETER_VALUE -> {
                val matched = paramValueResultSet(parameters, result)
                for (v in AntlersParamValueSource.valuesFor(info.tagHead, info.paramName, parameters.position, project)) {
                    matched.addElement(
                        LookupElementBuilder.create(v.text)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(v.typeText)
                    )
                }
            }

            AntlersCompletionKind.NONE -> {}
        }
    }

    /** Inside a quoted value the default prefix includes the opening quote (matching nothing);
     *  re-base the matcher on the string's inner text up to the caret. */
    private fun paramValueResultSet(
        parameters: CompletionParameters,
        result: CompletionResultSet
    ): CompletionResultSet {
        val pos = parameters.position
        if (pos.node.elementType != com.github.balotias.intellijantlers.psi.AntlersTypes.T_STRING) return result
        val caretInStr = (parameters.offset - pos.textRange.startOffset).coerceIn(0, pos.text.length)
        val inner = pos.text.substring(0, caretInStr)
            .removePrefix("\"").removePrefix("'")
            .replace(com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "")
        return result.withPrefixMatcher(inner)
    }

    private fun offerMembers(field: BlueprintField, project: com.intellij.openapi.project.Project, result: CompletionResultSet) {
        val seen = mutableSetOf<String>()
        val svc = BlueprintService.getInstance(project)
        for (ns in AntlersMemberResolver.childNamespaces(field)) {
            for (sub in svc.fieldsFor(ns)) {
                if (seen.add(sub.handle)) {
                    result.addElement(
                        LookupElementBuilder.create(sub.handle)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText("Field")
                            .withTailText(if (sub.display.isNotBlank()) "  ${sub.display}" else null, true)
                    )
                }
            }
        }
        for (p in FieldtypeProperties.forType(field.type)) {
            if (seen.add(p.name)) {
                result.addElement(
                    LookupElementBuilder.create(p.name)
                        .withIcon(AntlersIcons.FILE)
                        .withTypeText("Property")
                        .withTailText("  ${p.description}", true)
                )
            }
        }
    }
}
