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

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import io.nimbly.tzatziki.psi.*
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import java.awt.Dimension
import kotlin.math.max

const val FEATURE_HEAD =
    "Feature: x\n" +
        "Scenario Outline: xx\n" +
        "Examples: xxx\n"

fun Editor.where(table: GherkinTable) : Int {

    val offset = caretModel.offset
    val origineColumn = document.getColumnAt(offset)

    val firstCell = table.firstRow.firstCell ?: return 0

    val tableColumnStart = document.getColumnAt(firstCell.startOffset)
    val tableColumnEnd = document.getColumnAt(table.firstRow.lastCell!!.endOffset)
    return when {
        origineColumn<tableColumnStart -> -1 // Left
        origineColumn>tableColumnEnd -> 1    // Right
        else -> 0
    }
}

fun Editor.addNewColum(c: Char, project: Project, fileType: FileType): Boolean {

    if (c != '|') return false
    if (fileType != GherkinFileType.INSTANCE) return false
    val offset = this.caretModel.offset
    val table = this.findTableAt(offset) ?: return false
    var currentCol = table.columnNumberAt(offset)
    val currentRow = table.rowNumberAt(offset) ?: return false

    // Where I am ? In table ? At its left ? At its right ?
    var where = this.where(table)

    // adjust left of right of current column
    if (where == 0 && currentCol != null) {
        val startOfCell = this.cellAt(offset)?.previousPipe?.startOffset
        if (startOfCell != null) {
            val margin = offset - startOfCell
            if (margin in 1..2) {
                currentCol -= 1
                if (currentCol < 0)
                    where = -1
            }
        }
    }

    // Build new table as string
    val s = StringBuilder()
    table.allRows.forEachIndexed { y, row ->
        if (where < 0)
            s.append("|   ")
        row.psiCells.forEachIndexed { x, cell ->
            s.append('|').append(cell.text)
            if (x == currentCol)
                s.append("|   ")
        }
        if (where > 0)
            s.append("|   ")

        s.append('|').append('\n')
    }

    // Commit document
    PsiDocumentManager.getInstance(project).let {
        it.commitDocument(this.document)
        it.doPostponedOperationsAndUnblockDocument(this.document)
    }

    // Apply modifications
    ApplicationManager.getApplication().runWriteAction {

        // replace table
        val tempTable = CucumberElementFactory
            .createTempPsiFile(project, FEATURE_HEAD + s)
            .children[0].children[0].children[0].children[0]

        val newTable = table.replace(tempTable) as GherkinTable
        val newRow = newTable.row(currentRow)

        // Find caret target
        val targetColumn =
            when {
                where < 0 -> 0
                where > 0 -> newRow.psiCells.size - 1
                else -> currentCol!! + 1
            }

        // Move caret
        this.caretModel.moveToOffset(newRow.cell(targetColumn).previousPipe.textOffset + 2)

        // Format table
        newTable.format()
    }

    return true
}

fun Editor.addTableRow(offset: Int = caretModel.offset): Boolean {

    val colIdx = getTableColumnIndexAt(offset) ?: return false
    val table = findTableAt(offset) ?: return false
    val row = getTableRowAt(offset) ?: return false

    val insert = offset == getLineEndOffsetFromOffset(offset)
    if (insert && row.isLastRow)
        return false

    if (offset < row.startOffset)
        return false

    ApplicationManager.getApplication().runWriteAction {

        val newRow = row.createRowAfter()

        var newCaret = newRow.textOffset + 1
        if (!insert)
            newCaret += colIdx * 2
        caretModel.moveToOffset(newCaret)

        CodeStyleManager.getInstance(project!!).reformatText(
            table.containingFile,
            table.textRange.startOffset, table.textRange.endOffset
        )

        caretModel.moveToOffset(caretModel.offset + 1)
    }

    return true
}

fun Editor.stopBeforeDeletion(cleanCells: Boolean, cleanHeader: Boolean): Boolean {
    if (!selectionModel.hasSelection(true))
        return false
    val table = findTableAt(selectionModel.selectionStart)
    if (table != null) {

        if (selectionModel.selectionStart >= table.allRows.last().endOffset)
            return false

        val text = selectionModel.getSelectedText(true)
        if (text != null && text.contains(Regex("[\\n|]"))) {
            if (!cleanCells && !cleanHeader)
                return true

            this.cleanSelection(
                table,
                cleanHeader,
                selectionModel.blockSelectionStarts,
                selectionModel.blockSelectionEnds
            )
            return true
        }
    }
    return false
}

fun Editor.stopBeforeDeletion(actionId: String, offset: Int = caretModel.offset): Boolean {

    if (selectionModel.hasSelection(true)) {
        val text = selectionModel.getSelectedText(true)
        if (text != null
            && text.isNotEmpty()
            && !text.startsWith("\n")
            && !text.contains("|"))
            return false
    }

    if (stopBeforeDeletion(true, true)) {
        return true
    }

    val table = findTableAt(offset) ?:
        return false

    if (table.textLength == 1)
        return false // Table is a single pipe !!

    if (selectionModel.hasSelection(true)) {

        if (table.offsetIsOnAnyLine(selectionModel.selectionStart)
            && table.offsetIsOnAnyLine(selectionModel.selectionEnd))
            return true
    }
    else {
        val o = if (actionId == IdeActions.ACTION_EDITOR_DELETE) offset else offset - 1
        if (table.offsetIsOnAnyLine(o)) {

            val eof = document.charAt(offset) == '\n'
            val oo = if (eof) o+1 else o
            if (table.offsetIsOnLeft(oo))
                return document.getTextLine(oo).isNotBlank()

            val c = document.charAt(o)
            if (c != null && (c == '|' || c == '\n'))
                return true
        }
    }

    return false
}

private fun Editor.cleanSelection(table: GherkinTable, cleanHeader: Boolean, starts: IntArray, ends: IntArray): Int {

    // Find cells to delete
    val toClean = mutableListOf<GherkinTableCell>()
    val toCleanRows = mutableSetOf<GherkinTableRow>()
    val cleanedDimension = Dimension(0,0)
    starts.indices.forEach { i ->
        val (dimension, cells) = table.findCellsInRange(TextRange(starts[i], ends[i]), cleanHeader)
        cells
            .forEach {
                toClean.add(it)
                toCleanRows.add(it.row)
            }
        if (dimension.height >0)
            cleanedDimension.height++
        if (dimension.width >0)
            cleanedDimension.width = max(cleanedDimension.width, dimension.width)
    }
    if (toClean.size < 1) return 0

    // Remember deletion coordinates
    val blankSelection = isSelectionOfBlankCells()
    val coordinates = toClean.first().coordinate

    // Define column abd rows to remove completely
    val excludedRows =
        if (blankSelection && cleanedDimension.width == table.columnCount)
            coordinates.y until coordinates.y + cleanedDimension.height
        else -1..-1
    val excludedColumns =
        if (blankSelection && cleanedDimension.height == table.rowCount)
           coordinates.x until coordinates.x + cleanedDimension.width
        else -1..-1

    // Build temp string
    val sb = StringBuilder()
    table.allRows.forEachIndexed{ y, row ->
        if (y !in excludedRows) {
            sb.append("| ")
            row.psiCells.forEachIndexed { x, cell ->
                if (x !in excludedColumns) {
                    sb.append(if (toClean.contains(cell)) " " else cell.text)
                    sb.append(" |")
                }
            }
            sb.append('\n')
        }
    }

    // Delete all !
    if (sb.isEmpty()) {
        ApplicationManager.getApplication().runWriteAction {
           table.delete()
            return@runWriteAction
        }
        return  toClean.size
    }

    // Replace table
    val tableSmart = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(table, getFile()!!)
    ApplicationManager.getApplication().runWriteAction {

        // replace table
        val tempTable = CucumberElementFactory
            .createTempPsiFile(table.project, FEATURE_HEAD + sb.toString())
            .children[0].children[0].children[0].children[0]
        val newTable = table.replace(tempTable) as GherkinTable

        // Move cursor
        val targetCell = newTable.row(coordinates.y).cell(coordinates.x)
        caretModel.removeSecondaryCarets()
        caretModel.moveToOffset(targetCell.previousPipe.startOffset+2)
        selectionModel.removeSelection()

        // Format table
        newTable.format()
    }

    // Select next column
    tableSmart.element?.let {
        selectTableCells(tableSmart.element!!, coordinates, cleanedDimension)
    }

    return toClean.size
}
