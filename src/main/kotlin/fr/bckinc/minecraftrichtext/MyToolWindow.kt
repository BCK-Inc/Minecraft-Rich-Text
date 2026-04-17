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
            val inputField = JTextField("§4caca §lgras §nunderline §rnormal §aVert")
            inputField.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 30)
            val renderButton = JButton("Afficher le rendu")
            val htmlPane = JEditorPane("text/html", "")
            htmlPane.isEditable = false
            htmlPane.border = BorderFactory.createLineBorder(JBColor.LIGHT_GRAY)
            htmlPane.preferredSize = Dimension(300, 100)
            add(inputField)
            add(renderButton)
            add(JScrollPane(htmlPane))
            renderButton.addActionListener {
                val input = inputField.text
                htmlPane.text = MinecraftColorParser.toHtml(input)
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
        while (i < text.length) {
            if (text[i] == '§' && i + 1 < text.length) {
                val code = text[i + 1].lowercaseChar()
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
        return "<html><body style='background:#222;color:#fff;font-family:monospace;'>$sb</body></html>"
    }
}