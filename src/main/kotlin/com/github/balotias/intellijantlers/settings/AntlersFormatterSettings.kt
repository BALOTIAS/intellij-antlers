package com.github.balotias.intellijantlers.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Per-project Antlers formatter preferences. Default = today's behavior (reformatting on). */
@Service(Service.Level.PROJECT)
@State(name = "AntlersFormatterSettings", storages = [Storage("antlers.xml")])
class AntlersFormatterSettings : PersistentStateComponent<AntlersFormatterSettings.State> {

    data class State(var reformatEnabled: Boolean = true)

    private var state = State()

    /** When false, Reformat Code leaves `.antlers.html` untouched (defer to Prettier / external). */
    var reformatEnabled: Boolean
        get() = state.reformatEnabled
        set(value) { state.reformatEnabled = value }

    override fun getState(): State = state
    override fun loadState(newState: State) { state = newState }

    companion object {
        fun getInstance(project: Project): AntlersFormatterSettings = project.service()
    }
}
