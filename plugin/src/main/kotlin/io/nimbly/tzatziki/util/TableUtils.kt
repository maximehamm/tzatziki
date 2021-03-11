package io.nimbly.tzatziki.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinTable
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

fun GherkinTableRow.addRowAfter() : GherkinTableRow {

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

fun PsiElement.previousPipe(): PsiElement? {
    var el = prevSibling
    while (el != null) {
        if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
            return el
        }
        el = el.prevSibling
    }
    return null
}

fun GherkinTableRow.next() : GherkinTableRow? {
    val table = PsiTreeUtil.getParentOfType(this, GherkinTable::class.java) ?: return null
    return table.nextRow(this)
}

fun GherkinTable.nextRow(row: GherkinTableRow): GherkinTableRow? {
    val allRows = allRows()
    val i = allRows.indexOf(row) + 1
    return if (i < allRows.size)
        allRows[i] else null
}

fun GherkinTableRow.previous() : GherkinTableRow? {
    val table = PsiTreeUtil.getParentOfType(this, GherkinTable::class.java) ?: return null
    return table.previousRow(this)
}

fun GherkinTable.previousRow(row: GherkinTableRow): GherkinTableRow? {
    val allRows = allRows()
    val i = allRows.indexOf(row) -1
    return if (i >=0)
        allRows[i] else null
}

fun GherkinTable.adaptColumnWidths(colIdx: Int, width: Int) {

    allRows().forEach {
        if (width > it.getColumnFullWidth(colIdx)) {

            val cell = it.psiCells[colIdx]
            if (cell.nextSibling is PsiWhiteSpace) {
                val blank = CucumberElementFactory.createTempPsiFile(project, "| |").children[1];
                cell.nextSibling.replace(blank)
            }

            CucumberElementFactory.createTempPsiFile(project, "| |").getChildren();
        }
    }
}

fun GherkinTable.allRows() : List<GherkinTableRow> {
    if (headerRow == null)
        return dataRows

    val rows = mutableListOf<GherkinTableRow>()
    rows.add(headerRow!!)
    rows.addAll(dataRows)
    return rows
}

fun GherkinTable.getColumnFullWidth(columnIndex: Int): Int {

    var result = 0
    val headerRow = this.headerRow
    if (headerRow != null) {
        result = headerRow.getColumnFullWidth(columnIndex)
    }

    val iter = this.dataRows.iterator()
    while (iter.hasNext()) {
        var row = iter.next()
        result = Math.max(result, row.getColumnFullWidth(columnIndex))
    }
    return result
}

fun GherkinTableRow.getColumnFullWidth(columnIndex: Int): Int {
    var result = getColumnWidth(columnIndex)
    if (prevSibling is PsiWhiteSpace)
        result += prevSibling.textLength
    if (nextSibling is PsiWhiteSpace)
        result += nextSibling.textLength
    return result
}

