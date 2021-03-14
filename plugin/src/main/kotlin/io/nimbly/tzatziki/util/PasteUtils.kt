package io.nimbly.tzatziki.util

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinFile
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

    return editor.pasteToTable(table, offset, text)
}

private fun Editor.pasteNewTable(text: String, offset: Int) : Boolean {
    return false
}

private fun Editor.pasteToTable(table: GherkinTable, offset: Int, text: String) : Boolean {
    val addedCells = loadCells(text)
    val actualCells = loadCells(table)

    val x = table.columnNumberAt(offset)!!
    val y = table.rowNumberAt(offset)!!

    val merged = merge(actualCells, addedCells, x, y)

    val tableText = buildTableText(merged)

    // Apply modifications
    ApplicationManager.getApplication().runWriteAction {

        // replace table
        val tempTable = CucumberElementFactory
            .createTempPsiFile(project!!, FEATURE_HEAD + tableText)
            .children[0].children[0].children[0].children[0]
        val newTable = table.replace(tempTable) as GherkinTable

        // Move caret
        val targetCell = newTable.row(y).cell(x)
        caretModel.moveToOffset(targetCell.previousPipe().textOffset + 2)

        // Format table
        newTable.format()
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

    val width = max(targetX+added[0].size, actual[0].size)
    val height = max(targetY+added.size, actual.size)

    val target: Array<Array<String?>> = Array(height) { Array(width) { null } }
    fun feed(cells: List<List<String?>>, targetX: Int, targetY: Int) {
        cells.forEachIndexed { y, line ->
            line.forEachIndexed { x, cell ->
                target[y + targetY][x + targetX] = cell
            }
        }
    }
    feed(actual, 0, 0)
    feed(added, targetX, targetY)

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
       lines.add(line.split("\t"))
    }
    return lines
}
