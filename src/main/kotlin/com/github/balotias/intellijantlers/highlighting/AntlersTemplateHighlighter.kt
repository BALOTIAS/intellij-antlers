package com.github.balotias.intellijantlers.highlighting

import com.github.balotias.intellijantlers.AntlersLanguage
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class AntlersTemplateHighlighter(
    project: Project?,
    virtualFile: VirtualFile?,
    colors: EditorColorsScheme
) : LayeredLexerEditorHighlighter(AntlersSyntaxHighlighter(), colors) {

    init {
        val htmlHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
            com.intellij.lang.html.HTMLLanguage.INSTANCE, project, virtualFile
        )
        // Ensure that HTML is highlighted in the OUTER_HTML sections
        registerLayer(
            com.github.balotias.intellijantlers.psi.AntlersTypes.T_OUTER_HTML,
            LayerDescriptor(htmlHighlighter, "")
        )
    }
}
