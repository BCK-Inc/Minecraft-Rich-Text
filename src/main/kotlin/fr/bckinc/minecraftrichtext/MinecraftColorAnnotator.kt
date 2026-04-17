package fr.bckinc.minecraftrichtext

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText
import com.intellij.ui.JBColor
import java.awt.Color
import com.intellij.openapi.editor.markup.EffectType

/**
 * Annotator qui applique des TextAttributes sur les séquences Minecraft (§x, §l, §n, etc.)
 * Déclaré pour le language TEXT dans plugin.xml.
 */
class MinecraftColorAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiPlainText) return
        // Respecter le réglage d'activation
        if (!MinecraftSettings.getInstance().state.enabled) return
        val text = element.text
        var i = 0
        var color = JBColor.WHITE
        var bold = false
        var italic = false
        var underline = false
        var strike = false

        while (i < text.length) {
            if (text[i] == '§' && i + 1 < text.length) {
                val code = text[i + 1].lowercaseChar()
                when (code) {
                    // Minecraft legacy color palette (approximate RGB values)
                    '0' -> {
                        color = mcColor(0x00,0x00,0x00); bold = false; italic = false; underline = false
                    }

                    '1' -> {
                        color = mcColor(0x00,0x00,0xAA); bold = false; italic = false; underline = false
                    }

                    '2' -> {
                        color = mcColor(0x00,0xAA,0x00); bold = false; italic = false; underline = false
                    }

                    '3' -> {
                        color = mcColor(0x00,0xAA,0xAA); bold = false; italic = false; underline = false
                    }

                    '4' -> {
                        color = mcColor(0xAA,0x00,0x00); bold = false; italic = false; underline = false
                    }

                    '5' -> {
                        color = mcColor(0xAA,0x00,0xAA); bold = false; italic = false; underline = false
                    }

                    '6' -> {
                        color = mcColor(0xFF,0xAA,0x00); bold = false; italic = false; underline = false
                    }

                    '7' -> {
                        color = mcColor(0xAA,0xAA,0xAA); bold = false; italic = false; underline = false
                    }

                    '8' -> {
                        color = mcColor(0x55,0x55,0x55); bold = false; italic = false; underline = false
                    }

                    '9' -> {
                        color = mcColor(0x55,0x55,0xFF); bold = false; italic = false; underline = false
                    }

                    'a' -> {
                        color = mcColor(0x55,0xFF,0x55); bold = false; italic = false; underline = false
                    }

                    'b' -> {
                        color = mcColor(0x55,0xFF,0xFF); bold = false; italic = false; underline = false
                    }

                    'c' -> {
                        color = mcColor(0xFF,0x55,0x55); bold = false; italic = false; underline = false
                    }

                    'd' -> {
                        color = mcColor(0xFF,0x55,0xFF); bold = false; italic = false; underline = false
                    }

                    'e' -> {
                        color = mcColor(0xFF,0xFF,0x55); bold = false; italic = false; underline = false
                    }

                    'f' -> {
                        color = mcColor(0xFF,0xFF,0xFF); bold = false; italic = false; underline = false
                    }
                    // formatting codes
                    'l' -> bold = true
                    'o' -> italic = true
                    'n' -> underline = true
                    'm' -> strike = true
                    'r' -> {
                        color = mcColor(0xFF,0xFF,0xFF)
                        bold = false
                        italic = false
                        underline = false
                        strike = false
                    }
                }
                i += 2
                continue
            }

            val start = i
            while (i < text.length && text[i] != '§') i++
            if (start < i) {
                val range = TextRange(element.textRange.startOffset + start, element.textRange.startOffset + i)
                val effectColor = if (underline || strike) color else null
                val effectType = when {
                    strike -> EffectType.STRIKEOUT
                    underline -> EffectType.LINE_UNDERSCORE
                    else -> null
                }
                val attr = TextAttributes(
                    color,
                    null,
                    effectColor,
                    effectType,
                    (if (bold) java.awt.Font.BOLD else 0) or (if (italic) java.awt.Font.ITALIC else 0)
                )
                holder.newSilentAnnotation(com.intellij.lang.annotation.HighlightSeverity.INFORMATION).range(range)
                    .enforcedTextAttributes(attr).create()
            }
        }
    }
}


private fun mcColor(r: Int, g: Int, b: Int): JBColor {
    return JBColor(Color(r, g, b), Color(r, g, b))
}


