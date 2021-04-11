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

package io.nimbly.tzatziki.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.nimbly.tzatziki.psi.*
import org.jetbrains.plugins.cucumber.psi.*
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl

fun Editor.navigateInTableWithEnter(offset: Int = caretModel.offset): Boolean {

    val row = getTableRowAt(offset) ?: return false
    val colIdx = getTableColumnIndexAt(offset) ?: return false
    if (colIdx<0) return false
    val next = row.next ?: return false
    if (next.psiCells.size <= colIdx) return false

    val cell = next.psiCells[colIdx]
    val pipe = cell.previousPipe ?: return false

    caretModel.moveToOffset(pipe.textOffset +2)
    return true
}

fun Editor.navigateInTableWithTab(way: Boolean, editor: Editor, offset: Int = editor.caretModel.offset): Boolean {

    val table = findTableAt(offset) ?: return false
    val row = getTableRowAt(offset) ?: return false
    val file = file ?: return false
    val element = file.findElementAt(offset) ?: return false

    fun goRight() : Boolean {
        var el: PsiElement? =
            if (element is GherkinTableCell)
                element
            else if (element is LeafPsiElement && element.elementType == GherkinTokenTypes.PIPE)
                element
            else if (element is LeafPsiElement && element.parent is GherkinTableCell)
                element.parent
            else if (element is LeafPsiElement && element.parent is GherkinTableRow)
                element.nextSibling
            else if (element is LeafPsiElement && element.parent is GherkinTable)
                element.nextSibling.firstChild
            else if (element is LeafPsiElement && element.nextSibling is GherkinTable)
                element.nextSibling.firstChild.firstChild
            else
                element.nextSibling

        var pipe: PsiElement? = null
        while (el != null) {
            if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
                pipe = el
                break
            }
            el = el.nextSibling
        }

        if (pipe == null)
            return false

        val target =
            run {
                val off = pipe!!.textOffset + 2
                if (off > editor.document.textLength)
                    return true
                if (editor.document.getLineNumber(offset) != editor.document.getLineNumber(off)) {
                    val nextRow = row.next
                        ?: table.allRows.firstOrNull()!!
                    nextRow.psiCells.firstOrNull()?.textOffset
                } else {
                    off
                }
            }

        if (target !=null)
            editor.caretModel.moveToOffset(target)

        return true
    }

    fun goLeft() : Boolean {

        var el: PsiElement? =
            if (element is GherkinTableCell)
                element
            else if (element is LeafPsiElement && element.parent is GherkinTableCell)
                element.parent
            else if (element is LeafPsiElement && element.parent is GherkinTableRow)
                element.prevSibling
            else if (element is LeafPsiElement && element.prevSibling is GherkinTableRow)
                row.lastChild ?: return false
            else if (element is LeafPsiElement && element.prevSibling is GherkinFeature)
                row.lastChild
            else if (element is LeafPsiElement && element.parent is GherkinTable)
                element.parent.lastChild
            else
                element.prevSibling

        var pipe: PsiElement? = null
        while (el != null) {
            if (el is LeafPsiElement && (el as LeafPsiElement).elementType == GherkinTokenTypes.PIPE) {
                el = el!!.prevSibling
                break
            }
            el = el!!.prevSibling
        }
        while (el != null) {
            if (el is LeafPsiElement && (el as LeafPsiElement).elementType == GherkinTokenTypes.PIPE) {
                pipe = el
                break
            }
            el = el!!.prevSibling
        }

        val target =
            if (pipe == null) {
                val nextRow = row.previous
                    ?: table.allRows.lastOrNull() !!
                nextRow.psiCells.last().textOffset
            }
            else {
                val off = pipe!!.textOffset + 2
                if (off > editor.document.textLength)
                    return true
                if (editor.document.getLineNumber(offset) != editor.document.getLineNumber(off)) {
                    val nextRow = row.next
                        ?: table.allRows.firstOrNull()!!
                    nextRow.psiCells.first().textOffset
                } else {
                    off
                }
            }

        editor.caretModel.moveToOffset(target)
        return true
    }

    // If single line table, add cell automatically
    if (way
        && table.dataRows.isEmpty()
        && row is GherkinTableHeaderRowImpl
        && (row.lastCell == element.parent || row.lastCell == element.prevSibling)) {

        WriteCommandAction.runWriteCommandAction(project!!) {
            val newCell = row.lastCell!!.createCellAfter()
            editor.caretModel.moveToOffset(newCell.nextPipe.textOffset - 1)
        }

        return true
    }

    return if (way) goRight() else goLeft()
}