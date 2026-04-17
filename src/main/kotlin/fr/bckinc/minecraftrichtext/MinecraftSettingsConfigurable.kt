package fr.bckinc.minecraftrichtext

import com.intellij.openapi.options.Configurable
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class MinecraftSettingsConfigurable : Configurable {
    private var checkbox: JCheckBox? = null

    override fun createComponent(): JComponent? {
        val panel = JPanel()
        checkbox = JCheckBox("Activer l'annotator Minecraft Rich Text")
        panel.add(checkbox)
        return panel
    }

    override fun isModified(): Boolean {
        val settings = MinecraftSettings.getInstance().state
        return checkbox?.isSelected != settings.enabled
    }

    override fun apply() {
        val settings = MinecraftSettings.getInstance().state
        settings.enabled = checkbox?.isSelected ?: true
    }

    override fun reset() {
        val settings = MinecraftSettings.getInstance().state
        checkbox?.isSelected = settings.enabled
    }

    override fun getDisplayName(): String = "Minecraft Rich Text"
}

