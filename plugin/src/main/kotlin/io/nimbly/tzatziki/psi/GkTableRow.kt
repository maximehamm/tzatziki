package io.nimbly.tzatziki.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
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
    = psiCells[columnNumber]

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