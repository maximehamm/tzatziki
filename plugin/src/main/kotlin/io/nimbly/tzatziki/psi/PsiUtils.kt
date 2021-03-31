/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.psi

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.cucumber.psi.GherkinPsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

fun VirtualFile.getFile(project: Project): PsiFile? {
    return PsiManager.getInstance(project).findFile(this)
}

fun PsiFile.getModule()
    = ModuleUtilCore.findModuleForPsiElement(this)

fun PsiFile.cellAt(offset: Int): GherkinTableCell? {
    var l = findElementAt(offset) ?: return null
    if (l is GherkinTableCell)
        return l

    if (l is LeafPsiElement && l.parent is GherkinTableCell)
        return l.parent as GherkinTableCell
    if (l is LeafPsiElement && l.prevSibling is GherkinTableCell)
        return l.prevSibling as GherkinTableCell
    if (l is LeafPsiElement && l.nextSibling is GherkinTableCell)
        return l.nextSibling as GherkinTableCell

    if (l is LeafPsiElement && l.parent is PsiWhiteSpace)
        l = l.parent
    if (l is LeafPsiElement && l.prevSibling is PsiWhiteSpace)
        l = l.prevSibling
    if (l is LeafPsiElement && l.nextSibling is PsiWhiteSpace)
        l = l.nextSibling

    if (l is LeafPsiElement && l.nextSibling == null && l.parent is GherkinTableRow)
        return (l.parent as GherkinTableRow).psiCells.last()

    if (l is PsiWhiteSpace && l.parent is GherkinTableCell)
        return l.parent as GherkinTableCell
    if (l is PsiWhiteSpace && l.prevSibling is GherkinTableCell)
        return l.prevSibling as GherkinTableCell
    if (l is PsiWhiteSpace && l.nextSibling is GherkinTableCell)
        return l.nextSibling as GherkinTableCell

    return null
}

fun PsiFile.isColumnSeletionModeZone(offset: Int): Boolean {
    val line = PsiTreeUtil.findElementOfClassAtOffset(
        this, offset,
        GherkinTableRow::class.java, false
    ) ?: return false
    return !(offset < line.startOffset || offset > line.endOffset)
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

val PsiElement.previousPipe: PsiElement
    get() {
        var el = prevSibling
        while (el != null) {
            if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
                return el
            }
            el = el.prevSibling
        }
        throw Exception("Psi structure corrupted !")
    }
val PsiElement.nextPipe: PsiElement
    get() {
        var el = nextSibling
        while (el != null) {
            if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
                return el
            }
            el = el.nextSibling
        }
        throw Exception("Psi structure corrupted !")
    }

fun PsiElement.getDocumentLine()
    = getDocument()?.getLineNumber(textOffset)

fun GherkinPsiElement.bestRange(): TextRange {

    var i = text.indexOf(" ")
    if (i<0)
        return textRange

    val t = text.substring(i)
    val j = t.indexOfFirst { it != ' ' }
    if (j>0)
        i += j

    return TextRange(textRange.startOffset + i, textRange.endOffset)
}