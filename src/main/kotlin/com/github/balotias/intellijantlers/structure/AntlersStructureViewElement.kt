package com.github.balotias.intellijantlers.structure

import com.github.balotias.intellijantlers.psi.AntlersConditionMixin
import com.github.balotias.intellijantlers.psi.AntlersNamePathMixin
import com.github.balotias.intellijantlers.psi.AntlersStatement
import com.github.balotias.intellijantlers.scope.AntlersNestingTreeBuilder
import com.github.balotias.intellijantlers.scope.NestingNode
import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

/** A structure-view node: the file root, a paired-construct node, or a partial leaf. */
class AntlersStructureViewElement(
    element: PsiElement,
    private val label: String,
    private val nodeIcon: Icon?,
    private val childProvider: () -> Collection<StructureViewTreeElement>
) : PsiTreeElementBase<PsiElement>(element) {

    override fun getPresentableText(): String = label
    override fun getIcon(open: Boolean): Icon? = nodeIcon
    override fun getChildrenBase(): Collection<StructureViewTreeElement> = childProvider()

    companion object {
        fun forFile(file: PsiFile): AntlersStructureViewElement =
            AntlersStructureViewElement(file, file.name, AllIcons.Nodes.Tag) {
                val out = mutableListOf<StructureViewTreeElement>()
                AntlersNestingTreeBuilder.build(file, file.project).roots.forEach { out.add(fromNode(it)) }
                PsiTreeUtil.findChildrenOfType(file, AntlersStatement::class.java)
                    .filter { (it.namePath as? AntlersNamePathMixin)?.head == "partial" }
                    .sortedBy { it.textRange.startOffset }
                    .forEach { out.add(fromPartial(it)) }
                out
            }

        private fun fromNode(n: NestingNode): AntlersStructureViewElement {
            val cond = n.opener.condition
            val label = if (cond != null) (cond as? AntlersConditionMixin)?.keyword ?: n.name
            else (n.opener.namePath as? AntlersNamePathMixin)?.pathText ?: n.name
            val icon = if (cond != null) AllIcons.Nodes.Lambda else AllIcons.Nodes.Tag
            return AntlersStructureViewElement(n.opener, label, icon) { n.children.map { fromNode(it) } }
        }

        private fun fromPartial(stmt: AntlersStatement): AntlersStructureViewElement {
            val inner = stmt.text.trim().removePrefix("{{").removeSuffix("}}").trim()
            return AntlersStructureViewElement(stmt, inner, AllIcons.Nodes.Include) { emptyList() }
        }
    }
}
