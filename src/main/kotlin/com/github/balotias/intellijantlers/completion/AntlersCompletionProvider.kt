package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.AntlersIcons
import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.SystemVariables
import com.github.balotias.intellijantlers.references.StatamicProject
import com.github.balotias.intellijantlers.scope.FormVariables
import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.catalog.FieldtypeProperties
import com.github.balotias.intellijantlers.catalog.ModifierSignature
import com.github.balotias.intellijantlers.scope.AntlersFieldContext
import com.github.balotias.intellijantlers.scope.AntlersMemberResolver
import com.github.balotias.intellijantlers.scope.AntlersScopeResolver
import com.github.balotias.intellijantlers.scope.LoopVariables
import com.github.balotias.intellijantlers.scope.NavVariables
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.github.balotias.intellijantlers.scope.AntlersHintParser
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext

/** Classifies the caret context and offers the matching Antlers completions. */
class AntlersCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val hintPrefix = commentDirectivePrefix(parameters.position, parameters.offset)
        if (hintPrefix != null) {
            val r = result.withPrefixMatcher(hintPrefix)
            for (name in AntlersHintParser.DIRECTIVE_NAMES) {
                r.addElement(LookupElementBuilder.create(name).withIcon(AntlersIcons.FILE).withTypeText("Hint"))
            }
            return
        }

        val info = AntlersCompletionContext.classify(parameters.position)
        if (info.kind == AntlersCompletionKind.NONE) return

        val project = parameters.editor.project ?: return
        val catalog = AntlersCatalogService.getInstance(project)

        when (info.kind) {
            AntlersCompletionKind.TAG_NAME -> {
                val file = parameters.position.containingFile
                val stmt = PsiTreeUtil.getParentOfType(parameters.position, AntlersStatement::class.java)
                val stmtStart = stmt?.textRange?.startOffset ?: parameters.offset
                var unclosed: String? = null
                if (info.isClosing) {
                    unclosed = AntlersNestingTreeBuilder.nearestUnclosedAt(file, stmtStart, project)
                    if (unclosed != null) {
                        result.addElement(
                            com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority(
                                LookupElementBuilder.create(unclosed)
                                    .withIcon(AntlersIcons.FILE).withTypeText("Close tag"),
                                Double.MAX_VALUE
                            )
                        )
                    }
                } else {
                    // Logic keywords: openers always; followers scoped to the innermost open condition.
                    LOGIC_OPENERS.forEach { addLogic(result, it) }
                    when (AntlersNestingTreeBuilder.nearestUnclosedAt(file, stmtStart, project)) {
                        "if" -> LOGIC_IF_FOLLOWERS.forEach { addLogic(result, it) }
                        "unless" -> LOGIC_UNLESS_FOLLOWERS.forEach { addLogic(result, it) }
                    }
                }
                for (tag in catalog.tags()) {
                    if (tag.name == unclosed) continue   // already offered as the prioritized closer (no duplicate)
                    result.addElement(
                        LookupElementBuilder.create(tag.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(if (tag.isPair) "Tag (block)" else "Tag")
                            .withTailText(if (tag.description.isNotBlank()) "  ${tag.description}" else null, true)
                            .withInsertHandler(AntlersTagInsertHandler(tag.isPair, tag.parameters.isNotEmpty()))
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
                // Form runtime vars only inside a {{ form:… }} scope.
                if (scopes.any { it.namespace.kind == BlueprintNamespace.Kind.FORM }) {
                    for (fv in FormVariables.ALL) {
                        if (seen.add(fv.name)) {
                            result.addElement(
                                LookupElementBuilder.create(fv.name)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Form")
                                    .withTailText("  ${fv.description}", true)
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
                // `view` namespace — only when this file actually has front matter.
                if (com.github.balotias.intellijantlers.view.ViewFrontMatterService.getInstance(project)
                        .frontMatter(file) != null && seen.add("view")
                ) {
                    result.addElement(
                        LookupElementBuilder.create("view")
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText("Namespace")
                    )
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
                    // Colon shorthand handles: {{ collection:blog }}, {{ form:contact }}, etc.
                    val handles: List<Pair<String, String>> = when (info.tagHead) {
                        "collection" -> StatamicProject.listCollectionHandles(parameters.position).map { it to "Collection" }
                        "taxonomy" -> StatamicProject.listTaxonomyHandles(parameters.position).map { it to "Taxonomy" }
                        "form" -> StatamicProject.listFormHandles(parameters.position).map { it to "Form" }
                        "nav" -> StatamicProject.listNavHandles(parameters.position).map { it to "Nav" }
                        else -> emptyList()
                    }
                    for ((handle, type) in handles) {
                        result.addElement(
                            LookupElementBuilder.create(handle).withIcon(AntlersIcons.FILE).withTypeText(type)
                        )
                    }
                } else if (info.pathPrefix == listOf("view")) {
                    // `{{ view:<caret> }}` — offer the view's front-matter keys.
                    val file = parameters.position.containingFile
                    for (e in com.github.balotias.intellijantlers.view.ViewFrontMatterService.getInstance(project).topLevel(file)) {
                        result.addElement(
                            LookupElementBuilder.create(e.name)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("View")
                                .withTailText(if (e.valuePreview.isNotBlank()) "  ${e.valuePreview}" else null, true)
                        )
                    }
                } else {
                    // Not a catalog tag: a blueprint field member access via colon (e.g. {{ group:sub }}).
                    AntlersMemberResolver.resolveField(parameters.position, info.pathPrefix, project)
                        ?.let { offerMembers(it, project, result) }
                }
            }

            AntlersCompletionKind.TAG_SHORTHAND -> {
                for (name in SHORTHAND_TAGS) {
                    val tag = catalog.tag(name) ?: continue
                    result.addElement(
                        LookupElementBuilder.create(name)
                            .withPresentableText(":$name")
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText(if (tag.isPair) "Shorthand (block)" else "Shorthand")
                            .withInsertHandler(ShorthandTagInsertHandler(name, tag.isPair))
                    )
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
                    val args = ModifierSignature.render(mod).removePrefix(mod.name)   // "(p1, p2)" or ""
                    val desc = if (mod.description.isNotBlank()) "  ${mod.description}" else ""
                    result.addElement(
                        LookupElementBuilder.create(mod.name)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText("Modifier")
                            .withTailText(args + desc, true)
                            .withInsertHandler(ModifierInsertHandler(mod))
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

    /** When the caret sits in a `{{# … #}}` comment right after `@<word>`, the post-`@` prefix; else null. */
    private fun commentDirectivePrefix(position: com.intellij.psi.PsiElement, caretOffset: Int): String? {
        if (position.node.elementType != AntlersTypes.T_COMMENT_TEXT) return null
        val start = position.textRange.startOffset
        val end = (caretOffset - start).coerceIn(0, position.text.length)
        val before = position.text.substring(0, end)
        val at = before.lastIndexOf('@')
        if (at < 0) return null
        val after = before.substring(at + 1)
        return if (after.all { it.isLetter() }) after else null
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

    private fun addLogic(result: CompletionResultSet, kw: LogicKeyword) {
        result.addElement(
            LookupElementBuilder.create(kw.name)
                .withIcon(AntlersIcons.FILE)
                .withTypeText("Logic")
                .withInsertHandler(AntlersKeywordInsertHandler(kw))
        )
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
