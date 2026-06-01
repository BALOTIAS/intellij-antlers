package com.github.balotias.intellijantlers.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.github.balotias.intellijantlers.psi.AntlersTypes

class AntlersDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        // If the user hovers over a T_IDENT inside an Antlers tag, we want to provide docs
        val targetElement = originalElement ?: element
        
        if (targetElement is LeafPsiElement && targetElement.elementType == AntlersTypes.T_IDENT) {
            val tagName = targetElement.text
            return getTagDocumentation(tagName)
        }
        
        return null
    }

    private fun getTagDocumentation(tagName: String): String? {
        // Provide rich HTML documentation for standard tags
        return when (tagName) {
            "asset" -> """
                <b>asset</b><br/>
                The <code>asset</code> tag allows you to retrieve an asset by its path or ID, enabling you to output its URL or access its metadata.
                <br/><br/>
                <b>Parameters:</b>
                <ul>
                    <li><code>src</code> - The path or ID of the asset.</li>
                </ul>
                <br/>
                <i>Example:</i> <code>{{ asset src="container::path/to/image.jpg" }} ... {{ /asset }}</code>
            """.trimIndent()
            
            "collection" -> """
                <b>collection</b><br/>
                The <code>collection</code> tag iterates through a collection of entries. It is the primary way to output lists of content.
                <br/><br/>
                <b>Parameters:</b>
                <ul>
                    <li><code>from</code> - The handle of the collection to fetch.</li>
                    <li><code>limit</code> - Number of entries to show.</li>
                    <li><code>sort</code> - Field to sort by (e.g., <code>date:desc</code>).</li>
                </ul>
            """.trimIndent()
            
            "nav" -> """
                <b>nav</b><br/>
                The <code>nav</code> tag is used to loop through your navigation structures.
                <br/><br/>
                <b>Parameters:</b>
                <ul>
                    <li><code>handle</code> - The handle of the navigation structure.</li>
                </ul>
            """.trimIndent()
            
            "foreach" -> """
                <b>foreach</b><br/>
                The <code>foreach</code> tag is used to iterate over a standard array or list.
                <br/><br/>
                <b>Parameters:</b>
                <ul>
                    <li><code>:array</code> - The array variable to loop through.</li>
                </ul>
            """.trimIndent()
            
            "if", "unless", "else", "elseif" -> """
                <b>$tagName</b><br/>
                Control structures for logic branching in your templates.
            """.trimIndent()
            
            else -> null
        }
    }
}
