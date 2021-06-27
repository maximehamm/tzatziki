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

package io.nimbly.tzatziki.mouse

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.psi.allRows
import io.nimbly.tzatziki.psi.cellAt
import io.nimbly.tzatziki.psi.isHeader
import io.nimbly.tzatziki.psi.rowAt
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import java.awt.event.MouseEvent

fun manageTripleClicTableSelection(table: GherkinTable, editor: Editor, offset: Int): Boolean {

    if (!TOGGLE_CUCUMBER_PL)
        return false

    val cell = table.cellAt(offset) ?: return false
    val selectionModel = editor.selectionModel

    val row = table.rowAt(offset)!!
    if (row.isHeader) {

        val index: Int = row.psiCells.indexOf(cell)
        val rows = table.allRows
        val first = rows[0]
        val last = rows[rows.size - 1]
        if (first !== last) {
            var cells= first.psiCells

            val firstCell = if (cells.size > index) cells[index] else null
            cells = last.psiCells

            val lastCell = if (cells.size > index) cells[index] else null
            if (firstCell != null && lastCell != null) {

                val start = editor.offsetToLogicalPosition(firstCell.previousPipe.textOffset+1)
                val end = editor.offsetToLogicalPosition(lastCell.nextPipe.textRange.startOffset+1)
                val caretStates = EditorModificationUtil.calcBlockSelectionState(editor, start, end)

                editor.caretModel.setCaretsAndSelections(caretStates, true)
                return true
            }
        }
    }

    // Select line
    editor.setColumnMode(true)

    val margin = 0
    selectionModel.setSelection(row.startOffset, row.endOffset - margin)
    return true
}

object TZMouseAdapter : EditorMouseListener {
    override fun mouseReleased(e: EditorMouseEvent) {

        if (!e.gherkin)
            return

        TzSelectionModeManager.releaseSelectionSwitch()

        val me = e.mouseEvent
        if (me.button == MouseEvent.BUTTON1 && me.clickCount >= 2) {
            val editor = e.editor
            val logicalPosition = editor.xyToLogicalPosition(e.mouseEvent.point)
            val offset = editor.logicalPositionToOffset(logicalPosition)
            val table = editor.findTableAt(offset)
            if (table != null) {
                if (me.clickCount == 3)
                    manageTripleClicTableSelection(table, editor, offset)
                else if (me.clickCount == 2)
                    editor.selectionModel.selectWordAtCaret(false)
            }
        }
    }

    override fun mousePressed(e: EditorMouseEvent) {

        if (!e.gherkin)
            return

        val editor = e.editor
        val logicalPosition = editor.xyToLogicalPosition(e.mouseEvent.point)
        val offset = editor.logicalPositionToOffset(logicalPosition)

        // swith mode if needed
        TzSelectionModeManager.switchEditorSelectionModeIfNeeded(editor, offset)
        TzSelectionModeManager.blockSelectionSwitch()

        // TRICKY : avoid Intellij to manage double clic !
        // Because  e.getMouseEvent().consume() is not manage by default implementation !
        val me = e.mouseEvent
        if (me.button == MouseEvent.BUTTON1 && me.clickCount >= 2) {
            val table = editor.findTableAt(offset)
            if (table != null) {
                e.consume()
                e.mouseEvent.consume()
                JavaUtil.updateField(e.mouseEvent, "popupTrigger", true)
                JavaUtil.updateField(e.mouseEvent, "button", 0)
            }
        }
    }
}

object TzSelectionModeManager {

    var isSelectionSwitchBlocked = false
        private set

    fun switchEditorSelectionModeIfNeeded(editor: Editor, offset: Int) {

        if (isSelectionSwitchBlocked) return
        val f = editor.file ?: return
        if (f !is GherkinFile) return

        val needColumnMode = f.isColumnSeletionModeZone(offset)
        if (needColumnMode && !editor.isColumnMode
            || !needColumnMode && editor.isColumnMode) {
            editor.setColumnMode(!editor.isColumnMode)
        }
    }

    fun Editor.disableColumnSelectionMode() {
        if (isColumnMode) {
            setColumnMode(false)
        }
    }

    fun blockSelectionSwitch() {
        isSelectionSwitchBlocked = true
    }

    fun releaseSelectionSwitch() {
        isSelectionSwitchBlocked = false
    }
}

private val EditorMouseEvent.gherkin: Boolean
    get() = TOGGLE_CUCUMBER_PL
        && GherkinFileType.INSTANCE == this.editor.file?.fileType