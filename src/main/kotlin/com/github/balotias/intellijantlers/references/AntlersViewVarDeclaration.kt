package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.AntlersLanguage
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
 * Synthetic, renamable declaration for a front-matter key, so go-to-def lands precisely on the
 * `key:` line (the front matter is part of a large outer-HTML leaf, so resolving to the raw PSI
 * element would jump to the top of the file). Identity is by (file, offset).
 */
class AntlersViewVarDeclaration(
    private val project: Project,
    val varName: String,
    val file: VirtualFile,
    val offset: Int
) : FakePsiElement(), PsiNamedElement {

    override fun getParent(): PsiElement? = containingFile
    override fun getContainingFile(): PsiFile? = PsiManager.getInstance(project).findFile(file)
    override fun getProject(): Project = project
    override fun getName(): String = varName
    override fun getTextOffset(): Int = offset
    override fun getTextRange(): TextRange = TextRange(offset, offset + varName.length)
    override fun isValid(): Boolean = file.isValid
    override fun getLanguage(): Language = AntlersLanguage.INSTANCE

    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(project, file, offset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = file.isValid
    override fun canNavigateToSource(): Boolean = file.isValid

    override fun setName(newName: String): PsiElement {
        val doc = FileDocumentManager.getInstance().getDocument(file)
            ?: throw IncorrectOperationException("no document for ${file.name}")
        doc.replaceString(offset, offset + varName.length, newName)
        PsiDocumentManager.getInstance(project).commitDocument(doc)
        return AntlersViewVarDeclaration(project, newName, file, offset)
    }

    override fun isEquivalentTo(another: PsiElement?): Boolean = this == another
    override fun equals(other: Any?): Boolean =
        other is AntlersViewVarDeclaration && file == other.file && offset == other.offset
    override fun hashCode(): Int = file.hashCode() * 31 + offset
}
