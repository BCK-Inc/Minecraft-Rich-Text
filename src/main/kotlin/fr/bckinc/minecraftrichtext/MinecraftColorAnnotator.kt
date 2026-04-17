package fr.bckinc.minecraftrichtext

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import java.awt.Color
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.util.concurrent.ConcurrentHashMap

/**
 * Annotator qui applique des TextAttributes sur les séquences Minecraft (§x, §l, §n, etc.)
 * Déclaré pour le language TEXT dans plugin.xml.
 */
class MinecraftColorAnnotator : Annotator {
    private val LOG = Logger.getInstance(MinecraftColorAnnotator::class.java)
    companion object {
        private val highlightersByDoc: ConcurrentHashMap<Document, MutableList<RangeHighlighter>> = ConcurrentHashMap()
    }
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Respecter le réglage d'activation
        if (!MinecraftSettings.getInstance().state.enabled) return
        // Process the current element's text (works for PsiFile and leaf elements)
        val text = element.text
        if (text == null) return
        // Quick skip si pas de marqueurs (§ ou & pour variantes, ou # pour hex)
        if (!text.contains('§') && !text.contains('&') && !text.contains('#')) return
        // avoid processing extremely large elements repeatedly
        if (text.length > 10000) return
        var i = 0
        val baseOffset = element.textRange.startOffset
        var color = JBColor.WHITE
        var bold = false
        var italic = false
        var underline = false
        var strike = false

        var annotatedAny = false
        while (i < text.length) {
            if ((text[i] == '§' || text[i] == '&') && i + 1 < text.length) {
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
            while (i < text.length && text[i] != '§' && text[i] != '&' && text[i] != '#') i++
            if (start < i) {
                val range = TextRange(baseOffset + start, baseOffset + i)
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
                holder.newAnnotation(HighlightSeverity.INFORMATION, "").range(range)
                    .enforcedTextAttributes(attr).create()
                // Fallback: also add a range highlighter to any open editors for this document
                try {
                    val psiFile = element.containingFile
                    val document = PsiDocumentManager.getInstance(element.project).getDocument(psiFile)
                    if (document != null) {
                        // remove previous highlighters we added for this document
                        val prev = highlightersByDoc.remove(document)
                        if (prev != null) {
                            for (h in prev) {
                                try { h.dispose() } catch (_: Throwable) {}
                            }
                        }
                        val editors = EditorFactory.getInstance().getEditors(document, element.project)
                        val added = mutableListOf<RangeHighlighter>()
                        for (editor in editors) {
                            // create a fallback TextAttributes using plain java.awt.Color to ensure visibility
                            val fg = Color(color.red, color.green, color.blue)
                            val fallbackAttr = TextAttributes(
                                fg,
                                null,
                                if (underline || strike) fg else null,
                                if (strike) EffectType.STRIKEOUT else if (underline) EffectType.LINE_UNDERSCORE else null,
                                (if (bold) java.awt.Font.BOLD else 0) or (if (italic) java.awt.Font.ITALIC else 0)
                            )
                            val high = editor.markupModel.addRangeHighlighter(
                                range.startOffset,
                                range.endOffset,
                                HighlighterLayer.LAST,
                                fallbackAttr,
                                HighlighterTargetArea.EXACT_RANGE
                            )
                            added.add(high)
                        }
                        if (added.isNotEmpty()) highlightersByDoc[document] = added
                    }
                } catch (t: Throwable) {
                    LOG.debug("Could not add editor highlighter: ${t.message}")
                }
                annotatedAny = true
                LOG.debug("Annotated range ${range.startOffset}-${range.endOffset} on element ${element.node.elementType}")
            }
        }
        if (annotatedAny) {
            val fileName = element.containingFile?.name ?: "<unknown>"
            LOG.info("MinecraftColorAnnotator applied annotations to file: $fileName")
        }
    }
}


private fun mcColor(r: Int, g: Int, b: Int): JBColor {
    return JBColor(Color(r, g, b), Color(r, g, b))
}


