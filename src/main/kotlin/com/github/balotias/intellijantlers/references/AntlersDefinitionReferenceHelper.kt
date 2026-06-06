package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.psi.AntlersInlineTags
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil

/** Soft go-to-def references from a custom tag-head / modifier-name identifier to its PHP class. */
object AntlersDefinitionReferenceHelper {
    fun refsForIdent(element: PsiElement): Array<PsiReference> {
        val name = element.text

        // Modifier name: element must be the first T_IDENT child of an AntlersModifierMixin
        PsiTreeUtil.getParentOfType(element, AntlersModifierMixin::class.java)?.let { mod ->
            if (mod.modifierName == name &&
                mod.node.findChildByType(AntlersTypes.T_IDENT)?.psi == element) {
                return arrayOf(AntlersPhpClassReference(element, name, isModifier = true))
            }
        }

        // Inline tag call `{tag …}` (e.g. `href = {obfuscate_link …}`): the tag name is a bare T_IDENT,
        // not a NAME_PATH head, so attach the PHP-class reference here too (mirrors the inline-tag coloring,
        // sharing AntlersInlineTags.isInlineTagHead). Tags only — modifiers are never inline-call syntax.
        if (AntlersInlineTags.isInlineTagHead(element)) {
            return arrayOf(AntlersPhpClassReference(element, name, isModifier = false))
        }

        // Tag head: element must be the first T_IDENT child of an AntlersNamePathMixin
        val path = PsiTreeUtil.getParentOfType(element, AntlersNamePathMixin::class.java)
            ?: return emptyArray()

        // A name path inside a parameter value (limit=myVar) or a closing tag is not a tag head.
        if (PsiTreeUtil.getParentOfType(path, com.github.balotias.intellijantlers.psi.AntlersParameterMixin::class.java) != null) return emptyArray()
        if (PsiTreeUtil.getParentOfType(path, com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin::class.java) != null) return emptyArray()

        val idents = path.node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }
        val index = idents.indexOfFirst { it.psi == element }

        // `{{ view:<name> }}` — the second ident resolves to the front-matter key.
        if (index == 1 && idents.firstOrNull()?.psi?.text == "view") {
            return arrayOf(AntlersViewVariableReference(element, name))
        }

        if (index == 0) {
            // Head segment: PHP class ref + blueprint field ref (PsiMultiReference picks the
            // first that resolves — PHP class first).
            //
            // KNOWN LIMITATION (caret-rename): when a blueprint field handle collides with a
            // *project-defined* custom tag/modifier whose PHP class resolves (via TagScanner),
            // the PHP-class reference wins, so invoking Rename on `{{ handle }}` renames the tag
            // class, not the field. Narrow: it only bites when the project ALSO ships a custom
            // tag/modifier of that exact name. A field named like a *bundled* catalog tag (no
            // project PHP class) still renames correctly — see
            // AntlersVariableRefactoringTest.testRenameFieldNamedLikeBundledTagWorks. PHP-first is
            // the right default for genuine tags, so we keep this ordering.
            return arrayOf(
                AntlersPhpClassReference(element, name, isModifier = false),
                AntlersBlueprintFieldReference(element, name)
            )
        }

        if (index > 0) {
            // Non-head dotted/colon segment: resolve the prefix to a field; if this segment is one of
            // its sub-fields / linked-blueprint fields, point at that declaration. (Augmentation
            // properties have no declaration, so they get no reference.)
            val prefix = idents.take(index).map { it.text }
            val resolver = com.github.balotias.intellijantlers.scope.AntlersMemberResolver
            val parent = resolver.resolveField(element, prefix, element.project)
            if (parent != null) {
                val svc = com.github.balotias.intellijantlers.blueprint.BlueprintService.getInstance(element.project)
                for (childNs in resolver.childNamespaces(parent)) {
                    if (svc.fieldsFor(childNs).any { it.handle == name }) {
                        return arrayOf(AntlersBlueprintMemberReference(element, childNs, name))
                    }
                }
            }
        }

        return emptyArray()
    }
}
