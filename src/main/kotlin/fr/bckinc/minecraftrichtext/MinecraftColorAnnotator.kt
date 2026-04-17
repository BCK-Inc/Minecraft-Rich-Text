package fr.bckinc.minecraftrichtext

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText
import java.awt.Color
import com.intellij.openapi.editor.markup.EffectType

/**
 * Annotator qui applique des TextAttributes sur les séquences Minecraft (§x, §l, §n, etc.)
 * Déclaré pour le language TEXT dans plugin.xml.
 */
class MinecraftColorAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiPlainText) return
        val text = element.text
        var i = 0
        var color = Color.WHITE
        var bold = false
        var italic = false
        var underline = false
        var strike = false

        while (i < text.length) {
            if (text[i] == '§' && i + 1 < text.length) {
                val code = text[i + 1].lowercaseChar()
                when (code) {
                    '0' -> color = Color(0x00, 0x00, 0x00)
                    '1' -> color = Color(0x00, 0x00, 0xAA)
                    '2' -> color = Color(0x00, 0xAA, 0x00)
                    '3' -> color = Color(0x00, 0xAA, 0xAA)
                    '4' -> color = Color(0xAA, 0x00, 0x00)
                    '5' -> color = Color(0xAA, 0x00, 0xAA)
                    '6' -> color = Color(0xFF, 0xAA, 0x00)
                    '7' -> color = Color(0xAA, 0xAA, 0xAA)
                    '8' -> color = Color(0x55, 0x55, 0x55)
                    '9' -> color = Color(0x55, 0x55, 0xFF)
                    'a' -> color = Color(0x55, 0xFF, 0x55)
                    'b' -> color = Color(0x55, 0xFF, 0xFF)
                    'c' -> color = Color(0xFF, 0x55, 0x55)
                    'd' -> color = Color(0xFF, 0x55, 0xFF)
                    'e' -> color = Color(0xFF, 0xFF, 0x55)
                    'f' -> color = Color(0xFF, 0xFF, 0xFF)
                    'l' -> bold = true
                    'o' -> italic = true
                    'n' -> underline = true
                    'm' -> strike = true
                    'r' -> {
                        color = Color.WHITE
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
                val attr = TextAttributes(color, null,
                    if (underline) color else null,
                    if (underline) EffectType.LINE_UNDERSCORE else null,
                    (if (bold) java.awt.Font.BOLD else 0) or (if (italic) java.awt.Font.ITALIC else 0)
                )
                if (strike) attr.strikeout = true
                holder.newSilentAnnotation(com.intellij.lang.annotation.HighlightSeverity.INFORMATION)
                    .range(range)
                    .enforcedTextAttributes(attr)
                    .create()
            }
        }
    }
}

