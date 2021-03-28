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

package io.nimbly.tzatziki.clipboard

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import io.nimbly.tzatziki.psi.*
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import java.awt.datatransfer.DataFlavor
import kotlin.math.max

fun Editor.smartPaste(dataContext: DataContext): Boolean {

    val offset = CommonDataKeys.CARET.getData(dataContext)?.offset ?: return true
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return true
    val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return true
    val text = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor) ?: return false
    if (text.indexOf('\t') <0 && text.indexOf('\n') <0) return false

    var table = findTableAt(offset)
    if (table == null) {
        val element = file.findElementAt(offset)
        if (element is PsiWhiteSpace
            && element.textOffset>0
            && file.findElementAt(element.textOffset-1)?.elementType == GherkinTokenTypes.PIPE)
        table = findTableAt(element.textOffset-1)
    }

    if (table == null)
        return editor.pasteNewTable(text, offset)

    if (editor.selectionModel.hasSelection()) {
        if (editor.selectionModel.selectionStart < table.startOffset
            || editor.selectionModel.selectionStart > table.endOffset)
            return false
    }

    val tableLastLine = table.lastRow.getDocumentLine()
    if (tableLastLine!=null
            && editor.getLineNumber(offset) > tableLastLine
            && !text.contains('\t'))
        return false

    copyPasteUsed(file)

    return editor.pasteToTable(table, offset, text)
}

private fun Editor.pasteNewTable(text: String, offset: Int) : Boolean {
    return false
}

private fun Editor.pasteToTable(table: GherkinTable, offset: Int, text: String) : Boolean {

    // Load added cells
    val addedCells = loadCells(text)

    // Load actual cells
    val actualCells = loadCells(table)

    // Find target coordinates
    var x = table.columnNumberAt(offset)
    if (x == null) {
        val where = where(table)
        x = when {
            table.textLength == 1 -> 0 // Empty table, just a pipe
            where <0 -> -1
            where >0 -> table.allRows.first().psiCells.size
            else -> return false
        }
    }

    var y = table.rowNumberAt(offset)
    if (y == null) {
        y = when {
            table.textLength == 1 -> 0 // Empty table, just a pipe
            offset > table.endOffset -> table.allRows.size
            else -> return false
        }
        x = 0
    }

    // Merge cells
    val merged = merge(actualCells, addedCells, x, y)

    // Build table as text
    val tableText = buildTableText(merged)

    // Apply modifications
    ApplicationManager.getApplication().runWriteAction {

        // replace table
        val tempTable = CucumberElementFactory
            .createTempPsiFile(project!!, FEATURE_HEAD + tableText)
            .children[0].children[0].children[0].children[0]
        val newTable = table.replace(tempTable) as GherkinTable

        // Move caret
        val targetCell = newTable.row(y).cell(if (x<0) 0 else x)
        val smartCell = SmartPointerManager.getInstance(newTable.project).createSmartPsiElementPointer(targetCell, getFile()!!)

        // Format table
        newTable.format()

        // Highlight modified cells
        smartCell.element?.let {

            caretModel.moveToOffset(it.previousPipe.startOffset + 2)

            val startHighlight = it.previousPipe.startOffset
            val endHighlight = newTable
                .row(y + addedCells.size -1)
                .cell((if (x<0) 0 else x) + addedCells[0].size -1)
                .nextPipe.endOffset
            highlight(startHighlight, endHighlight)
        }
    }

    return true
}

fun buildTableText(merged: Array<Array<String?>>): String {
    val s = StringBuilder()
    merged.forEachIndexed { y, row ->
        row.forEachIndexed { x, cell ->
            s.append('|').append(cell ?: "  ")
        }
        s.append('|').append('\n')
    }
    return s.toString()
}

private fun merge(actual: List<List<String>>, added: List<List<String>>, targetX: Int, targetY: Int): Array<Array<String?>> {

    val width = if (targetX == -1) added[0].size + actual[0].size
                else max(targetX+added[0].size, actual[0].size)
    val height = max(targetY+added.size, actual.size)

    val target: Array<Array<String?>> = Array(height) { Array(width) { null } }
    fun feed(cells: List<List<String?>>, targetX: Int, targetY: Int) {
        cells.forEachIndexed { y, line ->
            line.forEachIndexed { x, cell ->
                target[y + targetY][x + targetX] = cell
            }
        }
    }

    if (targetX == -1) {
        feed(actual, added[0].size, 0)
        feed(added, 0, targetY)
    }
    else {
        feed(actual, 0, 0)
        feed(added, targetX, targetY)
    }

    return target
}

fun loadCells(table: GherkinTable): List<List<String>> {
    val lines = mutableListOf<List<String>>()
    table.allRows.forEach { row ->
        lines.add(row.psiCells.map { it.text })
    }
    return lines
}

private fun loadCells(text: String):  List<List<String>> {
    val lines = mutableListOf<List<String>>()
    text.split("\n").forEach { line ->
       lines.add(
           line.split("\t")
               .map { it.trim() })
    }
    return lines
}
