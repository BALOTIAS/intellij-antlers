package com.github.balotias.intellijantlers.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/** Settings -> Languages & Frameworks -> Antlers. */
class AntlersSettingsConfigurable(private val project: Project) : Configurable {

    private var reformatCheckbox: JBCheckBox? = null

    override fun getDisplayName(): String = "Antlers"

    override fun createComponent(): JComponent {
        val cb = JBCheckBox(
            "Reformat Antlers code on Reformat Code (uncheck to defer to Prettier / an external formatter)"
        )
        reformatCheckbox = cb
        return FormBuilder.createFormBuilder()
            .addComponent(cb)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean =
        reformatCheckbox?.isSelected != AntlersFormatterSettings.getInstance(project).reformatEnabled

    override fun apply() {
        AntlersFormatterSettings.getInstance(project).reformatEnabled = reformatCheckbox?.isSelected ?: true
    }

    override fun reset() {
        reformatCheckbox?.isSelected = AntlersFormatterSettings.getInstance(project).reformatEnabled
    }
}
