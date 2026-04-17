package fr.bckinc.minecraftrichtext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "MinecraftRichTextSettings", storages = [Storage("minecraft-rich-text.xml")])
class MinecraftSettings : PersistentStateComponent<MinecraftSettings.State> {
    data class State(var enabled: Boolean = true)

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        fun getInstance(): MinecraftSettings = ApplicationManager.getApplication().getService(MinecraftSettings::class.java)
    }
}

