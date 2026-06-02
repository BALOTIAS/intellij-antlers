package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersLanguage
import com.github.balotias.intellijantlers.blueprint.BlueprintField
import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace
import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.IncorrectOperationException

/**
 * Synthetic, renamable declaration for a blueprint field handle. Both blueprint references resolve
 * to this so Find Usages and Rename have a stable, identity-bearing target. Identity is by
 * (file, offset) — the physical declaration site, so a fieldset field imported into several
 * collections is one declaration; navigation lands precisely on the YAML `handle:` token.
 */
class AntlersFieldDeclaration(
    private val project: Project,
    val handle: String,
    val namespace: BlueprintNamespace,
    val file: VirtualFile,
    val offset: Int
) : FakePsiElement(), PsiNamedElement {

    constructor(project: Project, field: BlueprintField) :
        this(project, field.handle, field.namespace, field.file, field.offset)

    override fun getParent(): PsiElement? = containingFile
    override fun getContainingFile(): PsiFile? = PsiManager.getInstance(project).findFile(file)
    override fun getProject(): Project = project
    override fun getName(): String = handle
    override fun getTextOffset(): Int = offset
    override fun getTextRange(): TextRange = TextRange(offset, offset + handle.length)
    override fun isValid(): Boolean = file.isValid
    override fun getLanguage(): Language = AntlersLanguage.INSTANCE

    override fun setName(newName: String): PsiElement {
        val doc = FileDocumentManager.getInstance().getDocument(file)
            ?: throw IncorrectOperationException("no document for ${file.name}")
        doc.replaceString(offset, offset + handle.length, newName)
        PsiDocumentManager.getInstance(project).commitDocument(doc)
        return AntlersFieldDeclaration(project, newName, namespace, file, offset)
    }

    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(project, file, offset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = file.isValid
    override fun canNavigateToSource(): Boolean = file.isValid

    override fun isEquivalentTo(another: PsiElement?): Boolean = this == another

    override fun equals(other: Any?): Boolean =
        other is AntlersFieldDeclaration && file == other.file && offset == other.offset

    override fun hashCode(): Int = file.hashCode() * 31 + offset
}
