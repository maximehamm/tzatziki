package io.nimbly.tzatziki.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

fun GherkinTable.format() {
    val range = textRange
    WriteCommandAction.runWriteCommandAction(project) {
        CodeStyleManager.getInstance(project).reformatText(
            containingFile,
            range.startOffset, range.endOffset
        )
    }
}

fun GherkinTableRow.addRowAfter(): GherkinTableRow {

    val cellCount = psiCells.size
    val header =
        "Feature: x\n" +
            "Scenario Outline: xx\n" +
            "Examples: xxx\n"

    var rowString = "|"
    for (int in 1..cellCount)
        rowString += " |"

    val tempTable = CucumberElementFactory
        .createTempPsiFile(project, header + rowString + '\n' + rowString)
        .children[0].children[0].children[0].children[0]

    val tempRow = tempTable.children[1]
    val returnn = tempRow.prevSibling

    val newRow = this.parent.addAfter(tempRow, this)
    this.parent.addAfter(returnn, this)

    return newRow as GherkinTableRow
}

fun PsiElement.previousPipe(): PsiElement {
    var el = prevSibling
    while (el != null) {
        if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
            return el
        }
        el = el.prevSibling
    }
    throw Exception("Psi structure corrupted !")
}

fun PsiElement.nextPipe(): PsiElement {
    var el = nextSibling
    while (el != null) {
        if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
            return el
        }
        el = el.nextSibling
    }
    throw Exception("Psi structure corrupted !")
}

fun GherkinTableRow.next(): GherkinTableRow? {
    val table = PsiTreeUtil.getParentOfType(this, GherkinTable::class.java) ?: return null
    return table.nextRow(this)
}

fun GherkinTable.nextRow(row: GherkinTableRow): GherkinTableRow? {
    val allRows = allRows()
    val i = allRows.indexOf(row) + 1
    return if (i < allRows.size)
        allRows[i] else null
}

fun GherkinTableRow.previous(): GherkinTableRow? {
    val table = PsiTreeUtil.getParentOfType(this, GherkinTable::class.java) ?: return null
    return table.previousRow(this)
}

fun GherkinTable.previousRow(row: GherkinTableRow): GherkinTableRow? {
    val allRows = allRows()
    val i = allRows.indexOf(row) - 1
    return if (i >= 0)
        allRows[i] else null
}

fun GherkinTable.allRows(): List<GherkinTableRow> {
    if (headerRow == null)
        return dataRows

    val rows = mutableListOf<GherkinTableRow>()
    rows.add(headerRow!!)
    rows.addAll(dataRows)
    return rows
}

fun GherkinTable.cellAt(offset: Int): GherkinTableCell?
    = containingFile.cellAt(offset)

fun GherkinTableRow.cellAt(offset: Int): GherkinTableCell?
    = containingFile.cellAt(offset)

fun GherkinTableRow.columnNumberAt(offset: Int): Int? {
    val cell = cellAt(offset) ?: return null
    return psiCells.indexOf(cell)
}

fun GherkinTableRow.table(): GherkinTable = parent as GherkinTable

fun GherkinTableCell.row(): GherkinTableRow = parent as GherkinTableRow

fun GherkinTable.columnNumberAt(offset: Int): Int? {
    return cellAt(offset)?.row()?.columnNumberAt(offset)
}

fun GherkinTable.rowNumberAt(offset: Int): Int? {
    val row = rowAt(offset) ?: return null
    return allRows().indexOf(row)
}

fun GherkinTable.rowAt(offset: Int): GherkinTableRow? {
    var row = cellAt(offset)?.row()
    if (row != null)
        return row

    // Try from end of line
    val document = getDocument() ?: return null
    val line = document.getLineNumber(offset)
    row = cellAt(document.getLineEndOffset(line)-1)?.row()
    if (row != null)
        return row

    return null
}

fun GherkinTable.rowAtLine(line: Int): GherkinTableRow?
    = allRows().find { it.getDocumentLine() == line }

fun GherkinTableRow.cell(columnNumber: Int): GherkinTableCell = psiCells[columnNumber]

fun GherkinTableRow.isHeader()
    = table().allRows().first() == this

fun GherkinTable.row(rowNumber: Int): GherkinTableRow = allRows()[rowNumber]

fun GherkinTable.cellsInRange(range: TextRange): List<GherkinTableCell> {

    val found = mutableListOf<GherkinTableCell>()
    for (row in allRows()) {
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

private fun GherkinTableRow.intersects(range: TextRange)
    = textRange.intersects(range)

fun GherkinTableCell.coordinate(): Pair<Int, Int> {
    val row = row()
    val y = row.table().allRows().indexOf(row)
    val x = row.psiCells.indexOf(this)
    return Pair(x, y)
}