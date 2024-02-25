/*
 * I18N +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package io.nimbly.i18n.util

import ai.grazie.utils.isUppercase
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javazoom.jl.player.Player
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.*
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JLabel

fun <T, C : Collection<T>> C.nullIfEmpty(): C?
        = this.ifEmpty { null }

fun String?.nullIfEmpty(): String? =
    if (isNullOrEmpty()) null else this

fun executeWriteCommand(project: Project, text: String, runnable: Runnable) {
    CommandProcessor.getInstance().executeCommand(
        project, {
            val application =
                ApplicationManager.getApplication()
            application.runWriteAction {
                runnable.run()
            }
        },
        text, "Translation+"
    )
}

fun Editor.getLeafAtCursor(): LeafPsiElement? {
    val file = this.file ?: return null
    val element = file.findElementAt(caretModel.offset)
    return element as? LeafPsiElement
}

val Editor.file: PsiFile?
    get() {
        val project = project ?: return null
        return PsiDocumentManager.getInstance(project).getPsiFile(document)
    }

fun PsiElement.getDocument(): Document? {
    val containingFile = containingFile ?: return null
    var file = containingFile.virtualFile
    if (file == null) {
        file = containingFile.originalFile.virtualFile
    }
    return if (file == null) null else
        FileDocumentManager.getInstance().getDocument(file)
}

fun String.trimIndentLenght(): Int {
    return this.substringBefore("\n").length - this.trimIndent().substringBefore("\n").length
}

fun String.indentAs(model: String): String {

    val modelLines = model.split("\n")
    val indents = modelLines
        .map { it.length - it.trimStart().length }

    val firstNonBlank = modelLines.indexOfFirst { it.isNotBlank() }
    val lastNonBlank = modelLines.indexOfLast { it.isNotBlank() }

    val lines = this.split("\n")

    val indented = modelLines
        .mapIndexed { index, ml ->

            val i = ml.length - ml.trimStart().length
            var line = " ".repeat(i)

            if (index in firstNonBlank..lastNonBlank) {
                line += lines[index - firstNonBlank].trimStart()
            }

            line
        }
        .joinToString("\n")

    return indented
}

fun SelectionModel.getSelectedTextWithLeadingSpaces(): String? {

    var text = this.getSelectedText(false)
        ?: return null

    var offset = this.selectionStart
    val line = this.editor.document.getLineNumber(offset)
    while (offset > 0 && this.editor.document.getLineNumber(offset) == line) {
        offset --;
        val c = this.editor.document.getText(TextRange(offset, offset+1))[0]
        if (c == ' ')
            text = " " + text
        else
            offset = -1
    }

    return text
}

fun SelectionModel.findOffsetFirstNotNull(): Int {
    var o = editor.selectionModel.selectionStart
    while (o < this.editor.document.textLength) {
        val c = this.editor.document.getText(TextRange(o, o + 1))
        if (c.isNotBlank())
            return o
        o++
    }
    return editor.selectionModel.selectionStart // Not found
}

val languagesMap = mapOf(
    "af" to "Afrikaans",
    "ga" to "Irish",
    "sq" to "Albanian",
    "it" to "Italian",
    "ar" to "Arabic",
    "ja" to "Japanese",
    "az" to "Azerbaijani",
    "kn" to "Kannada",
    "eu" to "Basque",
    "ko" to "Korean",
    "bn" to "Bengali",
    "la" to "Latin",
    "be" to "Belarusian",
    "lv" to "Latvian",
    "bg" to "Bulgarian",
    "lt" to "Lithuanian",
    "ca" to "Catalan",
    "mk" to "Macedonian",
    "zh-cn" to "Chinese CN",
    "ms" to "Malay",
    "zh-tw" to "Chinese TW",
    "mt" to "Maltese",
    "hr" to "Croatian",
    "no" to "Norwegian",
    "cs" to "Czech",
    "fa" to "Persian",
    "da" to "Danish",
    "pl" to "Polish",
    "nl" to "Dutch",
    "pt" to "Portuguese",
    "en" to "English",
    "ro" to "Romanian",
    "eo" to "Esperanto",
    "ru" to "Russian",
    "et" to "Estonian",
    "sr" to "Serbian",
    "tl" to "Filipino",
    "sk" to "Slovak",
    "fi" to "Finnish",
    "sl" to "Slovenian",
    "fr" to "French",
    "es" to "Spanish",
    "gl" to "Galician",
    "sw" to "Swahili",
    "ka" to "Georgian",
    "sv" to "Swedish",
    "de" to "German",
    "ta" to "Tamil",
    "el" to "Greek",
    "te" to "Telugu",
    "gu" to "Gujarati",
    "th" to "Thai",
    "ht" to "Haitian",
    "tr" to "Turkish",
    "iw" to "Hebrew",
    "uk" to "Ukrainian",
    "hi" to "Hindi",
    "ur" to "Urdu",
    "hu" to "Hungarian",
    "vi" to "Vietnamese",
    "is" to "Icelandic",
    "cy" to "Welsh",
    "id" to "Indonesian",
    "yi" to "Yiddish"
)

val String.safeText
    get() = this.replace(CompletionUtilCore.DUMMY_IDENTIFIER, "", true)
        .replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "", true)

fun textToIcon(text: String, size: Float, position: Int, foreground: Color): Icon {
    val icon = LayeredIcon(2)
    icon.setIcon(textToIcon(text, JLabel(), JBUIScale.scale(size), foreground, position), 1, 0)
    return icon
}

fun textToIcon(text: String, component: Component, fontSize: Float, foreground: Color, position: Int): Icon {
    val font: Font = JBFont.create(JBUI.Fonts.label().deriveFont(fontSize))
    val metrics = component.getFontMetrics(font)
    val width = metrics.stringWidth(text) + JBUI.scale(4)
    val height = metrics.height
    return object : Icon {
        override fun paintIcon(c: Component?, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create()
            try {
                GraphicsUtil.setupAntialiasing(g)
                g.font = font
                UIUtil.drawStringWithHighlighting(
                    g,
                    text,
                    x + JBUI.scale(2),
                    y + height - JBUI.scale(1) + position,
                    foreground,
                    JBColor.background()
                )
            } finally {
                g.dispose()
            }
        }

        override fun getIconWidth(): Int {
            return width
        }

        override fun getIconHeight(): Int {
            return height
        }
    }
}

fun EditorFactory.clearInlays(delay: Int = -1) {
    this.allEditors.forEach {
        it.clearInlays(delay)
    }
}

private fun Editor.clearInlays(delay: Int = -1) {

    inlayModel.getBlockElementsInRange(0, document.textLength)
        .filter { it.renderer is EditorHint }
        .filter { delay < 0 || (it.renderer as EditorHint).sinceSeconds() > 5 }
        .forEach { Disposer.dispose(it) }
    inlayModel.getInlineElementsInRange(0, document.textLength)
        .filter { it.renderer is EditorHint }
        .filter { delay < 0 || (it.renderer as EditorHint).sinceSeconds() > 5 }
        .forEach { Disposer.dispose(it) }
}

fun playAudio(audioUrl: URL) {
    val inputStream = BufferedInputStream(audioUrl.openStream())
    val player = Player(inputStream)
    player.play()
}

fun Document.getLineTextStartOffset(offset: Int): Int {
    val ls = this.getLineStartOffset(getLineNumber(offset))
    val t = this.getText(TextRange(ls, offset))
    return t.length - t.trimStart().length
}

val AnActionEvent.editor
    get() = CommonDataKeys.EDITOR.getData(dataContext)
        ?: DataManager.getInstance().dataContext.getData(PlatformDataKeys.EDITOR)

fun Icon.toBase64(): String {
    val bufferedImage = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)

    // Create a graphics context and paint the icon on the buffered image
    val graphics = bufferedImage.createGraphics()
    paintIcon(null, graphics, 0, 0)
    graphics.dispose()

    // Write the buffered image to a byte array
    val byteArrayOutputStream = ByteArrayOutputStream()

    // Determine the image format based on the icon data type
    val imageFormat = if (this is ImageIcon) "jpg" else "png"

    // Write the image data to the byte array
    ImageIO.write(bufferedImage, imageFormat, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()

    // Encode the byte array to a base64 string
    val base64String = Base64.getEncoder().encodeToString(byteArray)

    return base64String
}

fun Icon.xxxx(): String {
    val bufferedImage = BufferedImage(this.iconWidth, this.iconHeight, BufferedImage.TYPE_INT_RGB)

    // Create a graphics context and paint the icon on the buffered image
    val graphics = bufferedImage.createGraphics()
    this.paintIcon(null, graphics, 0, 0)
    graphics.dispose()

    // Write the buffered image to a byte array
    val byteArrayOutputStream = ByteArrayOutputStream()
    ImageIO.write(bufferedImage, "png", byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()

    // Encode the byte array to a base64 string
    val base64String = Base64.getEncoder().encodeToString(byteArray)

    return base64String
}