package io.nimbly.tzatziki.util

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import java.awt.datatransfer.DataFlavor
import kotlin.math.max

fun Editor.smartPaste(dataContext: DataContext): Boolean {

    val offset = CommonDataKeys.CARET.getData(dataContext)?.offset ?: return true
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return true
    val text = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor) ?: return false
    if (text.indexOf('\t') <0 && text.indexOf('\n') <0) return false

    val table = findTableAt(offset) ?:
        return editor.pasteNewTable(text, offset)

    if (editor.selectionModel.hasSelection()) {
        if (editor.selectionModel.selectionStart < table.startOffset
            || editor.selectionModel.selectionStart > table.endOffset)
            return false
    }

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
            where <0 -> -1
            where >0 -> table.allRows().first().psiCells.size
            else -> return false
        }
    }

    var y = table.rowNumberAt(offset)
    if (y == null) {
        y = when {
                offset > table.endOffset -> table.allRows().size
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
        caretModel.moveToOffset(targetCell.previousPipe().startOffset + 1)

        // Format table
        newTable.format()

        // Highlight modified cells
        val startHighlight = targetCell.previousPipe().startOffset
        val endHighlight = newTable
            .row(y + addedCells.size -1)
            .cell((if (x<0) 0 else x) + addedCells[0].size -1)
            .nextPipe().endOffset

        highlight(startHighlight, endHighlight)
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
    table.allRows().forEach { row ->
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