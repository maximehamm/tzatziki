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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement

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

    val maxIndent = indents.max()
    val lines = this.split("\n")

    var indented = modelLines
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

