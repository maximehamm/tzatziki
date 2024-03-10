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

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import javazoom.jl.player.Player
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import java.io.BufferedInputStream
import java.net.URL
import javax.swing.text.JTextComponent

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

fun Editor.getLeafAtSelection(): LeafPsiElement? {
    val file = this.file ?: return null
    val element = file.findElementAt(selectionModel.selectionStart)
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

fun EditorFactory.clearInlays(delay: Int = -1) {
    this.allEditors.forEach {
        it.clearInlays(delay)
    }
}

fun Editor.clearInlays(delay: Int = -1) {
    getTranslationInlays(delay)
        .forEach { Disposer.dispose(it) }
}

fun Editor.getTranslationInlays(delay: Int = -1): List<Inlay<EditorHint>> {
    val inlays = (inlayModel.getBlockElementsInRange(0, document.textLength)
        .filter { it.renderer is EditorHint }
        .filter { delay < 0 || (it.renderer as EditorHint).sinceSeconds() > 5 }
        +
        inlayModel.getInlineElementsInRange(0, document.textLength)
            .filter { it.renderer is EditorHint }
            .filter { delay < 0 || (it.renderer as EditorHint).sinceSeconds() > 5 })
        @Suppress("UNCHECKED_CAST")
        return inlays as List<Inlay<EditorHint>>
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

var JTextComponent.textAndSelect
    get() = this.text
    set(s) {
        this.text = s
        if (s.isNotEmpty()) {

            this.selectAll()

            // try {
            //     this.highlighter.addHighlight(0, this.selectionEnd,
            //         DefaultHighlighter.DefaultHighlightPainter(selectionColor)
            //     )
            // } catch (e: BadLocationException) {
            //     e.printStackTrace()
            // }
        }
    }

class CustomTraversalPolicy(private vararg val order: Component) : FocusTraversalPolicy() {

    override fun getComponentAfter(focusCycleRoot: Container, aComponent: Component): Component {
        val index = (order.indexOf(aComponent) + 1) % order.size
        return order[index]
    }

    override fun getComponentBefore(focusCycleRoot: Container, aComponent: Component): Component {
        var index = order.indexOf(aComponent) - 1
        if (index < 0) {
            index = order.size - 1
        }
        return order[index]
    }

    override fun getFirstComponent(focusCycleRoot: Container): Component = order[0]

    override fun getLastComponent(focusCycleRoot: Container): Component = order.last()

    override fun getDefaultComponent(focusCycleRoot: Container): Component = order[0]
}

fun PsiElement?.findRenamable(): PsiElement? {
    var elt = this
    if (elt !is PsiNamedElement) {
        elt = elt?.parent
    }
    if (elt is PsiReference) {
        elt = elt.resolve()
    }
    return elt
}
