package fr.bckinc.minecraftrichtext

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.content.ContentFactory
import javax.swing.JButton
import javax.swing.JTextField
import javax.swing.JPanel
import javax.swing.BoxLayout
import javax.swing.JScrollPane
import javax.swing.JEditorPane
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JColorChooser
import java.awt.GridLayout
import java.awt.Dimension

class MyToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow()
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    class MyToolWindow {
        private val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            // Input et rendu HTML
            val inputField = JTextField("§4caca §lgras §nunderline §rnormal §aVert")
            inputField.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 30)
            val renderButton = JButton("Afficher le rendu")
            val htmlPane = JEditorPane("text/html", "")
            htmlPane.isEditable = false
            htmlPane.border = BorderFactory.createLineBorder(JBColor.LIGHT_GRAY)
            htmlPane.preferredSize = Dimension(300, 100)

            // Palette de couleurs Minecraft
            val palettePanel = JPanel(GridLayout(2, 8, 4, 4))
            val codes = listOf('0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f')
            val sampleText = "Texte"
            codes.forEach { code ->
                val btn = JButton(code.toString())
                btn.toolTipText = "§$code"
                val codeHex = MinecraftColorParser.hexForCode(code) ?: "#FFFFFF"
                btn.background = javax.swing.plaf.ColorUIResource(java.awt.Color.decode(codeHex))
                btn.addActionListener {
                    // Insère le code couleur dans le champ d'entrée
                    inputField.text += "§$code"
                    htmlPane.text = MinecraftColorParser.toHtml(inputField.text)
                }
                palettePanel.add(btn)
            }

            // Color picker pour hex
            val hexField = JTextField("#ffffff")
            hexField.maximumSize = Dimension(120, 30)
            val pickColorBtn = JButton("Choisir couleur")
            val previewPane = JEditorPane("text/html", "")
            previewPane.isEditable = false
            previewPane.preferredSize = Dimension(200, 40)
            pickColorBtn.addActionListener {
                val chosen = JColorChooser.showDialog(this@apply, "Choisir une couleur", java.awt.Color.WHITE)
                if (chosen != null) {
                    val hex = String.format("#%02x%02x%02x", chosen.red, chosen.green, chosen.blue)
                    hexField.text = hex
                    // affiche un aperçu du rendu dans le jeu (simple CSS)
                    previewPane.text = "<html><body style='background:#222;padding:4px;'><span style='color:$hex;font-family:monospace;text-shadow:1px 1px #000;'>Aperçu: $hex</span></body></html>"
                }
            }

            val insertHexBtn = JButton("Insérer #hex")
            insertHexBtn.addActionListener {
                inputField.text += hexField.text
                htmlPane.text = MinecraftColorParser.toHtml(inputField.text)
                previewPane.text = MinecraftColorParser.toHtml(inputField.text)
            }

            val insertLegacyBtn = JButton("Insérer §x legacy")
            insertLegacyBtn.addActionListener {
                // transforme #RRGGBB en §x§R§R§G§G§B§B
                val hex = hexField.text.trim().removePrefix("#")
                if (hex.length == 6) {
                    val sb = StringBuilder()
                    sb.append("§x")
                    for (ch in hex) {
                        sb.append('§')
                        sb.append(ch)
                    }
                    inputField.text += sb.toString()
                    htmlPane.text = MinecraftColorParser.toHtml(inputField.text)
                    previewPane.text = MinecraftColorParser.toHtml(inputField.text)
                }
            }

            add(inputField)
            add(renderButton)
            add(JScrollPane(htmlPane))
            add(JLabel("Palette Minecraft:"))
            add(palettePanel)
            val hexPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(hexField)
                add(pickColorBtn)
                add(previewPane)
            }
            add(hexPanel)

            renderButton.addActionListener {
                val input = inputField.text
                htmlPane.text = MinecraftColorParser.toHtml(input)
                previewPane.text = MinecraftColorParser.toHtml(input) // montre aussi le rendu
            }
        }

        fun getContent(): JPanel = content
    }
}

// Utilitaire pour parser les codes couleurs Minecraft et générer du HTML
object MinecraftColorParser {
    private val colorMap = mapOf(
        '0' to "#000000",
        '1' to "#0000AA",
        '2' to "#00AA00",
        '3' to "#00AAAA",
        '4' to "#AA0000",
        '5' to "#AA00AA",
        '6' to "#FFAA00",
        '7' to "#AAAAAA",
        '8' to "#555555",
        '9' to "#5555FF",
        'a' to "#55FF55",
        'b' to "#55FFFF",
        'c' to "#FF5555",
        'd' to "#FF55FF",
        'e' to "#FFFF55",
        'f' to "#FFFFFF"
    )
    private val styleMap = mapOf(
        'l' to "font-weight:bold;",
        'm' to "text-decoration:line-through;",
        'n' to "text-decoration:underline;",
        'o' to "font-style:italic;"
    )

    fun toHtml(text: String): String {
        val sb = StringBuilder()
        var color = "#FFFFFF"
        val styles = mutableListOf<String>()
        var i = 0
        val hexRegex = Regex("#[0-9A-Fa-f]{6}")
        while (i < text.length) {
            // support direct hex like #RRGGBB
            if (text[i] == '#' && i + 6 < text.length) {
                val maybe = text.substring(i, i + 7)
                if (hexRegex.matches(maybe)) {
                    color = maybe
                    styles.removeAll { it.startsWith("color:") }
                    styles.add("color:$color;")
                    i += 7
                    continue
                }
            }

            if (text[i] == '§' && i + 1 < text.length) {
                val code = text[i + 1].lowercaseChar()
                // support legacy hex format: §x§R§R§G§G§B§B
                if (code == 'x' && i + 13 < text.length) {
                    var ok = true
                    val hexSb = StringBuilder()
                    for (j in 0 until 6) {
                        val sigPos = i + 2 + j * 2
                        val hexPos = i + 3 + j * 2
                        if (sigPos >= text.length || hexPos >= text.length || text[sigPos] != '§') { ok = false; break }
                        val ch = text[hexPos]
                        if (!ch.isDigit() && ch.lowercaseChar() !in 'a'..'f') { ok = false; break }
                        hexSb.append(ch)
                    }
                    if (ok) {
                        color = "#${hexSb.toString()}"
                        styles.removeAll { it.startsWith("color:") }
                        styles.add("color:$color;")
                        i += 14
                        continue
                    }
                }

                when {
                    colorMap.containsKey(code) -> {
                        color = colorMap[code]!!
                        styles.removeAll { it.startsWith("color:") }
                        styles.add("color:$color;")
                    }

                    styleMap.containsKey(code) -> {
                        styles.add(styleMap[code]!!)
                    }

                    code == 'r' -> {
                        styles.clear()
                    }
                }
                i += 2
                continue
            }

            val styleAttr = if (styles.isNotEmpty()) " style=\"${styles.joinToString()}\"" else ""
            sb.append("<span$styleAttr>${text[i].toString().replace("<", "&lt;").replace(">", "&gt;")}</span>")
            i++
        }
        // simple container that mimics in-game look a bit
        return "<html><body style='background:#111;color:#fff;font-family:monospace;padding:6px;'><div style='background:#000;padding:6px;border-radius:4px;'>$sb</div></body></html>"
    }

    // expose helper to retrieve hex for a legacy code
    fun hexForCode(code: Char): String? {
        return colorMap[code]
    }
}