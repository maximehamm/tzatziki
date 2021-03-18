package io.nimbly.tzatziki.psi

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

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

fun GherkinTable.format() {
    val range = textRange
    WriteCommandAction.runWriteCommandAction(project) {
        CodeStyleManager.getInstance(project).reformatText(
            containingFile,
            range.startOffset, range.endOffset
        )
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
    = allRows[rowNumber]

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