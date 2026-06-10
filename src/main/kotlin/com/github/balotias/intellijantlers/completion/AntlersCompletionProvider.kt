package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.AntlersIcons
import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.github.balotias.intellijantlers.blueprint.BlueprintService
import com.github.balotias.intellijantlers.blueprint.SystemVariables
import com.github.balotias.intellijantlers.references.AntlersPartialParams
import com.github.balotias.intellijantlers.references.AntlersPartialReferenceHelper
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

/** The `groupby` builder operator as a whole word (used to gate the group-variable suggestions). */
private val GROUPBY_OPERATOR = Regex("\\bgroupby\\b")

/** Variables a `groupby` group exposes. */
private val GROUPBY_VARIABLES = listOf(
    "key" to "The current group's key",
    "values" to "The current group's items (loop with {{ values }}…{{ /values }})",
)

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
                    // `next:field` / `prev:field` reach the adjacent iteration's fields.
                    for (rel in listOf("next", "prev")) {
                        if (seen.add(rel)) {
                            result.addElement(
                                LookupElementBuilder.create(rel)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Loop")
                                    .withTailText("  the $rel iteration's fields (use $rel:field)", true)
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
                // `groupby` produces groups, each exposing `key` and a loopable `values`. Offer those
                // once a groupby operator has appeared earlier in the template. v1 limits (documented):
                // the `as 'alias'` rename and the outer group-loop's own scope aren't modelled — these
                // are the default names, and fields inside `{{ values }}` come from the global fallback.
                val beforeCaret = parameters.editor.document.immutableCharSequence.subSequence(0, parameters.offset)
                if (GROUPBY_OPERATOR.containsMatchIn(beforeCaret)) {
                    for ((name, desc) in GROUPBY_VARIABLES) {
                        if (seen.add(name)) {
                            result.addElement(
                                LookupElementBuilder.create(name)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Group")
                                    .withTailText("  $desc", true)
                            )
                        }
                    }
                }
                // `{{ foreach }}` exposes its key/value vars (default key/value, or the `as="k|v"` aliases).
                AntlersNestingTreeBuilder.enclosingForeachAt(file, stmtStart, project)?.let { opener ->
                    for (v in foreachVars(opener)) {
                        if (seen.add(v)) {
                            result.addElement(
                                LookupElementBuilder.create(v)
                                    .withIcon(AntlersIcons.FILE)
                                    .withTypeText("Loop")
                                    .withTailText("  foreach key/value", true)
                            )
                        }
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
                if ((info.tagHead == "next" || info.tagHead == "prev") &&
                    AntlersScopeResolver.scopesAt(parameters.position).isNotEmpty()
                ) {
                    // `{{ next:field }}` / `{{ prev:field }}` — the adjacent iteration shares the loop's fields.
                    for (field in AntlersFieldContext.fieldsInScope(parameters.position, project).orEmpty()) {
                        result.addElement(
                            LookupElementBuilder.create(field.handle)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("Field")
                                .withTailText(if (field.display.isNotBlank()) "  ${field.display}" else null, true)
                        )
                    }
                } else if (tag != null) {
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

            AntlersCompletionKind.PARAMETER -> {
                // Use the original (non-completion-modified) PSI so the dummy identifier injected by the
                // completion framework does not corrupt `extractPartialPath` (which would otherwise
                // append "IntellijIdeaRulezzz" to the resolved partial path).
                val origPos = parameters.originalPosition ?: parameters.position
                val partialStmt = if (info.tagHead == "partial")
                    PsiTreeUtil.getParentOfType(origPos, AntlersStatement::class.java) else null

                // Params declared by the included partial via `{{# @param … #}}`.
                partialStmt?.let { stmt ->
                    AntlersPartialReferenceHelper.includedPartialFile(stmt)?.let { pf ->
                        for (p in AntlersPartialParams.of(pf))
                            result.addElement(paramElement(p.name, p.required, p.description))
                    }
                }

                // Catalog params (e.g. partial's `src`). Suppressed for the colon form so it shows only
                // the component's own params.
                val suppressCatalog = partialStmt?.let { AntlersPartialReferenceHelper.hasColonPath(it) } == true
                if (!suppressCatalog) {
                    catalog.tag(info.tagHead ?: "")?.parameters?.forEach { p ->
                        result.addElement(paramElement(p.name, p.required, p.description))
                    }
                }

                // After a plain expression (the head is a variable/array, not a tag) the next token can
                // be a query/builder operator — `{{ players where (…) }}`. Offer them only here so tag
                // parameter completion isn't polluted with operators.
                if (catalog.tag(info.tagHead ?: "") == null) {
                    for (op in QUERY_OPERATORS) {
                        result.addElement(
                            LookupElementBuilder.create(op).withIcon(AntlersIcons.FILE).withTypeText("Operator")
                        )
                    }
                }

                // On a condition-capable tag, offer the target collection/taxonomy/user blueprint fields
                // as the left-hand side of a `field:operator="value"` query condition.
                if (info.tagHead in AntlersConditionOperators.CONDITION_TAGS) {
                    for (f in conditionFields(parameters.position, info.tagHead!!, project)) {
                        result.addElement(
                            LookupElementBuilder.create(f.handle)
                                .withIcon(AntlersIcons.FILE)
                                .withTypeText("Condition field")
                                .withTailText(if (f.display.isNotBlank()) "  ${f.display}" else null, true)
                        )
                    }
                }
            }

            AntlersCompletionKind.CONDITION_OPERATOR ->
                for ((op, desc) in AntlersConditionOperators.PRIMARY) {
                    result.addElement(
                        LookupElementBuilder.create(op)
                            .withIcon(AntlersIcons.FILE)
                            .withTypeText("Condition")
                            .withTailText("  $desc", true)
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

    /** A parameter-name lookup element (shared by catalog params and partial `@param`s). */
    private fun paramElement(name: String, required: Boolean, description: String) =
        LookupElementBuilder.create(name)
            .withIcon(AntlersIcons.FILE)
            .withTypeText(if (required) "Param*" else "Param")
            .withTailText(if (description.isNotBlank()) "  $description" else null, true)
            .withInsertHandler(ParameterInsertHandler)

    private fun addLogic(result: CompletionResultSet, kw: LogicKeyword) {
        result.addElement(
            LookupElementBuilder.create(kw.name)
                .withIcon(AntlersIcons.FILE)
                .withTypeText("Logic")
                .withInsertHandler(AntlersKeywordInsertHandler(kw))
        )
    }

    /** The key/value variable names a foreach loop exposes: the `as="k|v"` aliases, else [key, value]. */
    private fun foreachVars(opener: AntlersStatement): List<String> {
        val asValue = PsiTreeUtil.getChildrenOfTypeAsList(
            opener, com.github.balotias.intellijantlers.psi.AntlersParameterMixin::class.java
        ).firstOrNull { it.parameterName == "as" }?.valueElement?.text?.trim()?.trim('"', '\'')
        return if (!asValue.isNullOrBlank()) asValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        else listOf("key", "value")
    }

    /** Blueprint fields of the collection/taxonomy/users tag at [position] — the targets of a condition. */
    private fun conditionFields(
        position: com.intellij.psi.PsiElement,
        tagHead: String,
        project: com.intellij.openapi.project.Project,
    ): List<BlueprintField> {
        val stmt = PsiTreeUtil.getParentOfType(position, AntlersStatement::class.java) ?: return emptyList()
        val handle = PsiTreeUtil.findChildOfType(stmt, com.github.balotias.intellijantlers.psi.AntlersNamePathMixin::class.java)?.method
        val ns = when (tagHead) {
            "collection" -> handle?.let { BlueprintNamespace(BlueprintNamespace.Kind.COLLECTION, it) }
            "taxonomy" -> handle?.let { BlueprintNamespace(BlueprintNamespace.Kind.TAXONOMY, it) }
            "users" -> BlueprintNamespace(BlueprintNamespace.Kind.USER, "user")
            else -> null
        } ?: return emptyList()
        return BlueprintService.getInstance(project).fieldsFor(ns)
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
