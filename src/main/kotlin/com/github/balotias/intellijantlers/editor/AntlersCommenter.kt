package com.github.balotias.intellijantlers.editor

import com.intellij.lang.Commenter

/** Antlers has only block comments: `{{# ... #}}`. */
class AntlersCommenter : Commenter {
    override fun getLineCommentPrefix(): String? = null
    override fun getBlockCommentPrefix(): String = "{{#"
    override fun getBlockCommentSuffix(): String = "#}}"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
