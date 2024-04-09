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

package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.psi.SmartPointerManager
import com.intellij.refactoring.suggested.startOffset
import io.nimbly.tzatziki.actions.Direction.*
import io.nimbly.tzatziki.gherkin
import io.nimbly.tzatziki.psi.*
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

class TableShiftLeftAction : TableShiftAction(LEFT)
class TableShiftRightAction : TableShiftAction(RIGHT)
class TableShiftUpAction : TableShiftAction(UP)
class TableShiftDownAction : TableShiftAction(DOWN)

open class TableShiftAction(private val direction: Direction) : TzAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {

        if (!event.dataContext.gherkin)
            return

        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val offset = CommonDataKeys.CARET.getData(event.dataContext)?.offset ?: return
        val editor =  CommonDataKeys.EDITOR.getData(event.dataContext) ?: return

        val cell = file.cellAt(offset) ?: return

        val table = cell.row.table
        editor.shift(table, cell, direction)
    }

    override fun update(event: AnActionEvent) {

        super.update(event)

        if (event.presentation.isVisible) {

            val editor = event.getData(CommonDataKeys.EDITOR)
            if (editor == null)
                event.presentation.isVisible = false
            else {
                if (event.dataContext.gherkin) {
                    val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
                    val offset = CommonDataKeys.CARET.getData(event.dataContext)?.offset ?: return
                    event.presentation.isEnabled = file.cellAt(offset) != null
                }
                else {
                    event.presentation.isEnabled = false
                }
            }
        }
    }
}

private fun Editor.shift(table: GherkinTable, cell: GherkinTableCell, direction: Direction) {

    // build columns and rows indexes
    val columnsIdx = (0 until table.columnCount).map { it }.toMutableList()
    val rowsIdx = (0 until table.rowCount).map { it }.toMutableList()

    // Remember position
    val coordinate = cell.coordinate
    val caret = offsetToLogicalPosition(caretModel.offset)

    // Move to direction
    when (direction) {
        LEFT -> columnsIdx.move(coordinate.x, -1)
        RIGHT -> columnsIdx.move(coordinate.x, +1)
        UP -> rowsIdx.move(coordinate.y, -1)
        DOWN -> rowsIdx.move(coordinate.y, +1)
    }

    // Build table as string
    val s = StringBuilder()
    rowsIdx.forEach { y ->
        columnsIdx.forEach { x ->
            s.append("| ").append(table.row(y).cell(x).text)
        }
        s.append('|').append('\n')
    }

    // Replace table
    WriteCommandAction.runWriteCommandAction(project!!) {

        // Replace Psi
        val tempTable = CucumberElementFactory
            .createTempPsiFile(table.project, FEATURE_HEAD + s)
            .children[0].children[0].children[0].children[0]
        val newTable = table.replace(tempTable) as GherkinTable
        val smarTable = SmartPointerManager.getInstance(newTable.project).createSmartPsiElementPointer(newTable, file!!)

        // Format table
        newTable.format()

        // Highlist moved column or row
        smarTable.element?.let { table ->

            // Move cursor
            val refCell = table.cellAt(coordinate)!!
            val newCaret = when (direction) {
                LEFT, RIGHT -> refCell.row.cell(coordinate.x + direction.toInt()).previousPipe.startOffset +2
                UP, DOWN -> logicalPositionToOffset(caret.go(direction))
            }
            caretModel.moveToOffset(newCaret)

            // Highlight section
            val ref = when (direction) {
                LEFT, RIGHT -> refCell.row.cell(coordinate.x + direction.toInt())
                UP, DOWN -> table.row(coordinate.y + direction.toInt())
            }
            val startHighlight =
                if (ref is GherkinTableCell) table.firstRow.cell(ref.columnNumber).previousPipe.startOffset +1
                else table.row((ref as GherkinTableRow).rowNumber).firstCell!!.previousPipe.startOffset

            val endHighlight =
                if (ref is GherkinTableCell) table.lastRow.cell(ref.columnNumber).nextPipe.startOffset+1
                else table.row((ref as GherkinTableRow).rowNumber).lastCell!!.nextPipe.startOffset+1

            highlight(startHighlight, endHighlight)
        }
    }


}

enum class Direction { UP, DOWN, LEFT, RIGHT;
    fun toInt() = if (this == UP || this == LEFT) -1 else +1
}

private fun LogicalPosition.go(direction: Direction)
    = when (direction) {
        LEFT -> goLeft()
        RIGHT -> goRight()
        UP -> goUp()
        DOWN -> goDown()
    }

private fun LogicalPosition.goLeft() = LogicalPosition(line, column-1)
private fun LogicalPosition.goRight() = LogicalPosition(line, column+1)
private fun LogicalPosition.goUp() = LogicalPosition(line-1, column)
private fun LogicalPosition.goDown() = LogicalPosition(line+1, column)