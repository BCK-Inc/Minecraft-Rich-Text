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
import javax.swing.SwingUtilities

class MyToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(project)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), "Minecraft Rich Text", false)
        toolWindow.contentManager.addContent(content)
    }

    class MyToolWindow(private val project: Project) {
        companion object {
            private val highlightersByDoc = mutableMapOf<com.intellij.openapi.editor.Document, MutableList<com.intellij.openapi.editor.markup.RangeHighlighter>>()
        }
        // listeners for documents so we can update highlights in realtime
        private val docListeners = mutableMapOf<com.intellij.openapi.editor.Document, com.intellij.openapi.editor.event.DocumentListener>()
        private val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            // Input et rendu HTML
            val inputField = JTextField("§4caca §lgras §nunderline §rnormal §aVert")
            inputField.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 30)
            val renderButton = JButton("Afficher le rendu")
            val applyEditorBtn = JButton("Appliquer dans l'éditeur")
            val htmlPane = JEditorPane("text/html", "")
            htmlPane.isEditable = false
            htmlPane.border = BorderFactory.createLineBorder(JBColor.LIGHT_GRAY)
            htmlPane.preferredSize = Dimension(300, 100)

            // Palette de couleurs Minecraft
            val palettePanel = JPanel(GridLayout(2, 8, 4, 4))
            val codes = listOf('0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f')
            codes.forEach { code ->
                val btn = JButton(code.toString())
                btn.toolTipText = "§$code"
                val codeHex = MinecraftColorParser.hexForCode(code) ?: "#FFFFFF"
                btn.background = javax.swing.plaf.ColorUIResource(java.awt.Color.decode(codeHex))
                btn.addActionListener {
                    // Insère le code couleur dans le champ d'entrée
                    inputField.text += "§$code"
                    safeSetHtml(htmlPane, MinecraftColorParser.toHtml(inputField.text))
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
                val chosen = JColorChooser.showDialog(this@apply, "Choisir une couleur", JBColor.WHITE)
                if (chosen != null) {
                    val hex = String.format("#%02x%02x%02x", chosen.red, chosen.green, chosen.blue)
                    hexField.text = hex
                    // affiche un aperçu du rendu dans le jeu (simple CSS)
                    safeSetHtml(previewPane, "<html><body style='background:#222;padding:4px;'><span style='color:$hex;font-family:monospace;'>Aperçu: $hex</span></body></html>")
                }
            }

            val insertHexBtn = JButton("Insérer #hex")
            insertHexBtn.addActionListener {
                inputField.text += hexField.text
                val rendered = MinecraftColorParser.toHtml(inputField.text)
                safeSetHtml(htmlPane, rendered)
                safeSetHtml(previewPane, extractInnerPreview(rendered))
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
                    val rendered2 = MinecraftColorParser.toHtml(inputField.text)
                    safeSetHtml(htmlPane, rendered2)
                    safeSetHtml(previewPane, extractInnerPreview(rendered2))
                }
            }

            add(inputField)
            add(renderButton)
            add(applyEditorBtn)
            add(JScrollPane(htmlPane))
            add(JLabel("Palette Minecraft:"))
            add(palettePanel)
            val hexPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(hexField)
                add(pickColorBtn)
                add(insertHexBtn)
                add(insertLegacyBtn)
                add(previewPane)
            }
            add(hexPanel)

            renderButton.addActionListener {
                val input = inputField.text
                val rendered = MinecraftColorParser.toHtml(input)
                safeSetHtml(htmlPane, rendered)
                safeSetHtml(previewPane, extractInnerPreview(rendered)) // small preview
            }

            applyEditorBtn.addActionListener {
                // ensure we have a document listener for realtime updates and apply immediately
                try {
                    val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor
                    if (editor != null) {
                        ensureListener(editor)
                        applyHighlightsToEditor(editor)
                    }
                } catch (t: Throwable) {
                    System.err.println("applyEditorBtn error: ${t.message}")
                }
            }
        }

        // Ensure a document listener is attached to update highlights on change
        private fun ensureListener(editor: com.intellij.openapi.editor.Editor) {
            val doc = editor.document
            if (docListeners.containsKey(doc)) return
            val listener = object : com.intellij.openapi.editor.event.DocumentListener {
                override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                    // recompute highlights on EDT
                    javax.swing.SwingUtilities.invokeLater {
                        try {
                            // find the editor for this document (may be different instance)
                            val ed = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor
                            if (ed != null && ed.document === doc) {
                                applyHighlightsToEditor(ed)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
            doc.addDocumentListener(listener)
            docListeners[doc] = listener
        }

        // Apply highlights to the given editor based on computeRanges
        private fun applyHighlightsToEditor(editor: com.intellij.openapi.editor.Editor) {
            val doc = editor.document
            val text = doc.text
            val ranges = computeRanges(text)
            // remove previous
            val prev = highlightersByDoc.remove(doc)
            if (prev != null) prev.forEach { try { it.dispose() } catch (_: Throwable) {} }
            val added = mutableListOf<com.intellij.openapi.editor.markup.RangeHighlighter>()
            for ((s, e, ta) in ranges) {
                try {
                    val h = editor.markupModel.addRangeHighlighter(s, e, com.intellij.openapi.editor.markup.HighlighterLayer.LAST, ta, com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE)
                    added.add(h)
                } catch (_: Throwable) {}
            }
            if (added.isNotEmpty()) highlightersByDoc[doc] = added
        }

        init {
            // listen to editor selection changes so we attach listeners / apply highlights automatically
            try {
                val fem = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                fem.addFileEditorManagerListener(object : com.intellij.openapi.fileEditor.FileEditorManagerListener {
                    override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                        try {
                            val ed = fem.selectedTextEditor
                            if (ed != null) {
                                ensureListener(ed)
                                applyHighlightsToEditor(ed)
                            }
                        } catch (_: Throwable) {}
                    }
                })
                // also initialize for currently selected editor
                val cur = fem.selectedTextEditor
                if (cur != null) { ensureListener(cur); applyHighlightsToEditor(cur) }
            } catch (_: Throwable) {}
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
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var i = 0
        val hexRegex = Regex("#[0-9A-Fa-f]{6}")
        while (i < text.length) {
            // support direct hex like #RRGGBB
            if (text[i] == '#' && i + 6 < text.length) {
                val maybe = text.substring(i, i + 7)
                    if (hexRegex.matches(maybe)) {
                        color = maybe
                        // color code resets formatting in this parser (like MC behaviour of color codes resetting formatting)
                        bold = false; italic = false; underline = false; strike = false
                        i += 7
                        continue
                    }
            }

            if ((text[i] == '§' || text[i] == '&') && i + 1 < text.length) {
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
                        bold = false; italic = false; underline = false; strike = false
                        i += 14
                        continue
                    }
                }

                when {
                    colorMap.containsKey(code) -> {
                            color = colorMap[code]!!
                            bold = false; italic = false; underline = false; strike = false
                    }

                    styleMap.containsKey(code) -> {
                        // apply formatting code
                        when (code) {
                            'l' -> bold = true
                            'o' -> italic = true
                            'n' -> underline = true
                            'm' -> strike = true
                        }
                    }

                    code == 'r' -> {
                        color = "#FFFFFF"
                        bold = false; italic = false; underline = false; strike = false
                    }
                }
                i += 2
                continue
            }
            // build CSS style from current state
            val css = StringBuilder()
            css.append("color:$color;")
            if (bold) css.append("font-weight:bold;")
            if (italic) css.append("font-style:italic;")
            if (underline && strike) css.append("text-decoration: underline line-through;")
            else if (underline) css.append("text-decoration: underline;")
            else if (strike) css.append("text-decoration: line-through;")
            val styleAttr = if (css.isNotEmpty()) " style=\"${css.toString()}\"" else ""
            sb.append("<span$styleAttr>${text[i].toString().replace("<", "&lt;").replace(">", "&gt;")}</span>")
            i++
        }
        // simple container that mimics in-game look in a JEditorPane-friendly way
        return "<html><body style='background:#111;color:#fff;font-family:monospace;padding:6px;'><div style='background:#000;padding:6px;'>$sb</div></body></html>"
    }

    // expose helper to retrieve hex for a legacy code
    fun hexForCode(code: Char): String? {
        return colorMap[code]
    }
}


// helper to safely set HTML in a JEditorPane (avoids crashing on unsupported CSS)
fun safeSetHtml(pane: JEditorPane, html: String) {
    SwingUtilities.invokeLater {
        try {
            pane.text = html
        } catch (t: Throwable) {
            // fallback: insert minimal plain text to avoid parser crash
            pane.text = "<html><body><pre>${html.replace("<", "&lt;").replace(">", "&gt;")}</pre></body></html>"
            // log minimal info
            try { System.err.println("safeSetHtml fallback: ${t.message}") } catch (_: Throwable) {}
        }
    }
}

// Extract the inner content (inside the main div) to show a compact preview
fun extractInnerPreview(fullHtml: String): String {
    val start = fullHtml.indexOf("<div")
    if (start < 0) return fullHtml
    val divStart = fullHtml.indexOf('>', start)
    if (divStart < 0) return fullHtml
    val divEnd = fullHtml.indexOf("</div>", divStart)
    if (divEnd < 0) return fullHtml
    val inner = fullHtml.substring(divStart + 1, divEnd)
    return "<html><body style='background:#111;color:#fff;font-family:monospace;padding:2px;'>$inner</body></html>"
}

// compute ranges and TextAttributes for the given text (same logic as annotator)
fun computeRanges(text: String): List<Triple<Int, Int, com.intellij.openapi.editor.markup.TextAttributes>> {
    val out = mutableListOf<Triple<Int, Int, com.intellij.openapi.editor.markup.TextAttributes>>()
    var i = 0
    var colorHex = "#FFFFFF"
    var bold = false
    var italic = false
    var underline = false
    var strike = false
    val hexRegex = Regex("#[0-9A-Fa-f]{6}")
    while (i < text.length) {
        // direct hex like #RRGGBB: highlight the whole token as the color and reset styles
        if (text[i] == '#' && i + 6 < text.length) {
            val maybe = text.substring(i, i + 7)
            if (hexRegex.matches(maybe)) {
                // apply new color and reset formatting
                colorHex = maybe
                bold = false; italic = false; underline = false; strike = false
                // create attributes for the hex token itself
                try {
                    val colToken = java.awt.Color.decode(colorHex)
                    val effectColorToken = if (underline || strike) colToken else null
                    val effectTypeToken = if (strike) com.intellij.openapi.editor.markup.EffectType.STRIKEOUT else if (underline) com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE else null
                    val taToken = com.intellij.openapi.editor.markup.TextAttributes(colToken, null, effectColorToken, effectTypeToken, (if (bold) java.awt.Font.BOLD else 0) or (if (italic) java.awt.Font.ITALIC else 0))
                    out.add(Triple(i, i + 7, taToken))
                } catch (_: Throwable) {}
                i += 7
                continue
            }
        }

        // legacy hex §x§R§R§G§G§B§B or standard two-char codes (§1, §l, etc.) - highlight the control sequences themselves
        if ((text[i] == '§' || text[i] == '&') && i + 1 < text.length) {
            val code = text[i + 1].lowercaseChar()
            // legacy rgb
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
                    colorHex = "#${hexSb.toString()}"
                    bold = false; italic = false; underline = false; strike = false
                    // highlight the whole legacy block
                    try {
                        val colToken = java.awt.Color.decode(colorHex)
                        val effectColorToken = if (underline || strike) colToken else null
                        val effectTypeToken = if (strike) com.intellij.openapi.editor.markup.EffectType.STRIKEOUT else if (underline) com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE else null
                        val taToken = com.intellij.openapi.editor.markup.TextAttributes(colToken, null, effectColorToken, effectTypeToken, (if (bold) java.awt.Font.BOLD else 0) or (if (italic) java.awt.Font.ITALIC else 0))
                        out.add(Triple(i, i + 14, taToken))
                    } catch (_: Throwable) {}
                    i += 14
                    continue
                }
            }

            // handle single two-char codes
            when {
                MinecraftColorParser.hexForCode(code) != null -> {
                    colorHex = MinecraftColorParser.hexForCode(code)!!
                    bold = false; italic = false; underline = false; strike = false
                }
                code == 'l' -> bold = true
                code == 'o' -> italic = true
                code == 'n' -> underline = true
                code == 'm' -> strike = true
                code == 'r' -> { colorHex = "#FFFFFF"; bold = false; italic = false; underline = false; strike = false }
                else -> {
                    // unknown code: treat as literal text
                    i++
                    continue
                }
            }

            // create TextAttributes for the control sequence itself using the state AFTER applying the code
            try {
                val colToken = java.awt.Color.decode(colorHex)
                val effectColorToken = if (underline || strike) colToken else null
                val effectTypeToken = if (strike) com.intellij.openapi.editor.markup.EffectType.STRIKEOUT else if (underline) com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE else null
                val taToken = com.intellij.openapi.editor.markup.TextAttributes(colToken, null, effectColorToken, effectTypeToken, (if (bold) java.awt.Font.BOLD else 0) or (if (italic) java.awt.Font.ITALIC else 0))
                out.add(Triple(i, i + 2, taToken))
            } catch (_: Throwable) {}
            i += 2
            continue
        }

        // normal text chunk
        val start = i
        while (i < text.length && text[i] != '§' && text[i] != '&' && text[i] != '#') i++
        if (start < i) {
            // create TextAttributes from current state
            try {
                val col = java.awt.Color.decode(colorHex)
                val effectColor = if (underline || strike) col else null
                val effectType = if (strike) com.intellij.openapi.editor.markup.EffectType.STRIKEOUT else if (underline) com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE else null
                val ta = com.intellij.openapi.editor.markup.TextAttributes(col, null, effectColor, effectType, (if (bold) java.awt.Font.BOLD else 0) or (if (italic) java.awt.Font.ITALIC else 0))
                out.add(Triple(start, i, ta))
            } catch (_: Throwable) {}
        }
    }
    return out
}
