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

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.util.cellAt
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

val GherkinTableRow.table: GherkinTable
    get() = parent as GherkinTable

val GherkinTableRow.isHeader
    get() = table.allRows.first() == this

val GherkinTableRow.isLastRow
    get() = table.allRows.last() == this

val GherkinTableRow.rowNumber: Int
    get() = table.allRows.indexOf(this)

val GherkinTableRow.next: GherkinTableRow?
    get() {
        val table = PsiTreeUtil.getParentOfType(this, GherkinTable::class.java) ?: return null
        return table.nextRow(this)
    }

val GherkinTableRow.previous: GherkinTableRow?
    get() {
        val table = PsiTreeUtil.getParentOfType(this, GherkinTable::class.java) ?: return null
        return table.previousRow(this)
    }

val GherkinTableRow.firstCell
    get() = psiCells.firstOrNull()

val GherkinTableRow.lastCell
    get() = psiCells.lastOrNull()

fun GherkinTableRow.cellAt(offset: Int): GherkinTableCell?
    = containingFile.cellAt(offset)

fun GherkinTableRow.columnNumberAt(offset: Int): Int? {
    val cell = cellAt(offset) ?: return null
    return psiCells.indexOf(cell)
}

fun GherkinTableRow.cell(columnNumber: Int): GherkinTableCell
    = psiCells.let {
        when {
            columnNumber<0 -> it[0]
            columnNumber<it.size -> it[columnNumber]
            else -> it.last()
        }
}

fun GherkinTableRow.intersects(range: TextRange)
    = textRange.intersects(range)

fun GherkinTableRow.createRowAfter(): GherkinTableRow {

    val cellCount = psiCells.size
    val header =
        "Feature: x\n" +
            "Scenario Outline: xx\n" +
            "Examples: xxx\n"

    var rowString = "|"
    for (int in 1..cellCount)
        rowString += " |"

    val tempTable = CucumberElementFactory.createTempPsiFile(project, header + rowString + '\n' + rowString)
        .children[0].children[0].children[0].children[0]

    val tempRow = tempTable.children[1]
    val returnn = tempRow.prevSibling

    val newRow = this.parent.addAfter(tempRow, this)
    this.parent.addAfter(returnn, this)

    return newRow as GherkinTableRow
}

fun GherkinTableCell.createCellAfter(): GherkinTableCell {

    val gfile = "Feature: x\n" +
        "Scenario Outline: xx\n" +
        "Examples: xxx\n" +
        row.text +
        "  |"

    val tempTable = CucumberElementFactory.createTempPsiFile(project, gfile)
        .children[0].children[0].children[0].children[0]

    val tempRow = tempTable.children[0]
    val replace = row.replace(tempRow) as GherkinTableRow

    return replace.lastCell!!
}