package io.nimbly.tzatziki

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

fun Editor.findTable(offset: Int): GherkinTable? {
    val file = getFile() ?: return null
    val element = file.findElementAt(offset) ?: return null
    return PsiTreeUtil.getContextOfType(element, GherkinTable::class.java) ?: return null
}

fun Editor.getFile(): PsiFile? {
    val project = project ?: return null
    return PsiDocumentManager.getInstance(project).getPsiFile(document)
}

fun GherkinTable.format() {
    val range = textRange
    WriteCommandAction.runWriteCommandAction(project) {
        CodeStyleManager.getInstance(project).reformatText(
            containingFile,
            range.startOffset, range.endOffset
        )
    }
}

fun Editor.getTableColumnIndexAt(offset: Int): Int? {
    val file = getFile() ?: return null
    val element = file.findElementAt(offset) ?: return null

    var col = -1
    var el: PsiElement? = element
    while (el != null) {
        if (el is GherkinTableCell)
            col++
        el = el.prevSibling
    }

    if (col<0 && element.prevSibling is GherkinTableRow) {
        col = element.prevSibling.children.count { it is GherkinTableCell }
    }

    return col
}

fun Editor.getTableRowAt(offset: Int): GherkinTableRow? {

    val file = getFile() ?: return null
    val element = file.findElementAt(offset) ?: return null

    val parent = element.parent
    if (parent is GherkinTableRow)
        return parent

    return null
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
