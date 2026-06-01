package com.github.balotias.intellijantlers.references

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

        // Tag head: element must be the first T_IDENT child of an AntlersNamePathMixin
        val path = PsiTreeUtil.getParentOfType(element, AntlersNamePathMixin::class.java)
            ?: return emptyArray()

        // A name path inside a parameter value (limit=myVar) or a closing tag is not a tag head.
        if (PsiTreeUtil.getParentOfType(path, com.github.balotias.intellijantlers.psi.AntlersParameterMixin::class.java) != null) return emptyArray()
        if (PsiTreeUtil.getParentOfType(path, com.github.balotias.intellijantlers.psi.AntlersClosingTagMixin::class.java) != null) return emptyArray()

        val idents = path.node.getChildren(null).filter { it.elementType == AntlersTypes.T_IDENT }
        val index = idents.indexOfFirst { it.psi == element }

        if (index == 0) {
            // Head segment: PHP class ref + blueprint field ref (PsiMultiReference picks the resolver).
            return arrayOf(
                AntlersPhpClassReference(element, name, isModifier = false),
                AntlersBlueprintFieldReference(element, name)
            )
        }

        if (index > 0) {
            // Non-head dotted/colon segment: resolve the prefix to a field; if this segment is one of
            // its blueprint sub-fields, point at that sub-field's declaration. (Augmentation properties
            // have no declaration, so they get no reference.)
            val prefix = idents.take(index).map { it.text }
            val parent = com.github.balotias.intellijantlers.scope.AntlersMemberResolver
                .resolveField(element, prefix, element.project)
            if (parent != null) {
                val childNs = com.github.balotias.intellijantlers.scope.AntlersMemberResolver.childNamespace(parent)
                val hasMember = com.github.balotias.intellijantlers.blueprint.BlueprintService
                    .getInstance(element.project).fieldsFor(childNs).any { it.handle == name }
                if (hasMember) return arrayOf(AntlersBlueprintMemberReference(element, childNs, name))
            }
        }

        return emptyArray()
    }
}
