/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import java.awt.Dimension
import java.awt.Point

val GherkinTable.allRows: List<GherkinTableRow>
    get() {
        if (headerRow == null)
            return dataRows

        val rows = mutableListOf<GherkinTableRow>()
        rows.add(headerRow!!)
        rows.addAll(dataRows)
        return rows
    }

val GherkinTable.firstRow
    get() = allRows.first()

val GherkinTable.lastRow
    get() = allRows.last()

val GherkinTable.columnCount: Int
    get() = firstRow.psiCells.size

val GherkinTable.rowCount: Int
    get() = allRows.size

fun GherkinTable.format(startWriteAction: Boolean = true) {

    fun format() {
        val range = textRange
        CodeStyleManager.getInstance(project).reformatText(
            containingFile,
            range.startOffset, range.endOffset
        )
    }

    if (startWriteAction) {
        WriteCommandAction.runWriteCommandAction(project) {
            format()
        }
    }
    else {
        format()
    }
}

fun GherkinTable.previousRow(row: GherkinTableRow): GherkinTableRow? {
    val allRows = allRows
    val i = allRows.indexOf(row) - 1
    return if (i >= 0)
        allRows[i] else null
}

fun GherkinTable.cellAt(offset: Int): GherkinTableCell?
    = containingFile.cellAt(offset)

fun GherkinTable.cellAt(coordinates: Point): GherkinTableCell? {
    val row = row(coordinates.y)
    return row.cell(coordinates.x)
}

fun GherkinTable.columnNumberAt(offset: Int): Int? {
    return cellAt(offset)?.row?.columnNumberAt(offset)
}

fun GherkinTable.rowNumberAt(offset: Int): Int? {
    val row = rowAt(offset) ?: return null
    return allRows.indexOf(row)
}

fun GherkinTable.rowAt(offset: Int): GherkinTableRow? {

    if (textLength == 1)
        return null // Table is just a single pipe !

    var row = cellAt(offset)?.row
    if (row != null)
        return row

    // Try from end of line
    val document = getDocument() ?: return null
    val line = document.getLineNumber(offset)
    row = cellAt(document.getLineEndOffset(line)-1)?.row
    if (row != null)
        return row

    return null
}

fun GherkinTable.rowAtLine(line: Int): GherkinTableRow?
    = allRows.find { it.getDocumentLine() == line }

fun GherkinTable.row(rowNumber: Int): GherkinTableRow
    = allRows.let {
        when {
            rowNumber<0 -> it[0]
            rowNumber<it.size -> it[rowNumber]
            else -> it.last()
        }
    }

fun GherkinTable.cellsInRange(range: TextRange): List<GherkinTableCell> {

    val found = mutableListOf<GherkinTableCell>()
    for (row in allRows) {
        if (!row.intersects(range)) continue
        row.psiCells.forEach { cell ->
            if (cell.textRange.intersects(range))
                found.add(cell)
        }
        val lastCell = row.psiCells[row.psiCells.size - 1]
        if (range.startOffset > lastCell.textOffset + lastCell.textLength)
            found.add(lastCell)
    }
    return found
}

fun GherkinTable.nextRow(row: GherkinTableRow): GherkinTableRow? {
    val allRows = allRows
    val i = allRows.indexOf(row) + 1
    return if (i < allRows.size)
        allRows[i] else null
}

fun GherkinTable.isCorrupted() : Boolean {
    var columns = -1
    allRows.forEach { row ->
        val c = row.psiCells.size
        if (columns<0)
            columns = c
        if (columns != c)
            return true
    }
    return false
}

fun GherkinTable.offsetIsOnLeft(offset: Int): Boolean {

    val document = getDocument() ?: return false

    val col1 = document.getColumnAt(offset)
    val col2 = document.getColumnAt(allRows.first().startOffset)

    return col1<col2
}

fun GherkinTable.offsetIsOnAnyLine(offset: Int): Boolean {

    val document = getDocument() ?: return false

    val from = document.getLineStart(allRows.first().startOffset)
    val to = document.getLineEnd(allRows.last().endOffset)

    return offset in from..to
}

fun GherkinTable.findCellsInRange(range: TextRange, withHeader: Boolean): Pair<Dimension, List<GherkinTableCell>> {
    val found = mutableListOf<GherkinTableCell>()
    val dimension = Dimension(0, 0)

    val rows = if (withHeader) allRows else dataRows
    rows.forEach { row ->

        if (!row.textRange.intersects(range)) return@forEach
        val cells = row.psiCells ?: return@forEach
        cells.forEach { cell ->
            if (cell.textRange.intersects(range)) {
                found.add(cell)
            }
        }
        if (dimension.width==0)
            dimension.width = found.size

        val lastCell = cells[cells.size - 1]
        if (range.startOffset > lastCell.textOffset + lastCell.textLength && !cells.contains(lastCell)) {
            found.add(lastCell)
            cells.add(lastCell)
            dimension.width = cells.size +1
        }

        if (cells.isNotEmpty()) {
            dimension.height++
        }
    }

    return dimension to found
}

fun GherkinTable.findColumnByName(name: String)
    = headerRow
        ?.psiCells
        ?.indexOfFirst { it.text.trim() == name }