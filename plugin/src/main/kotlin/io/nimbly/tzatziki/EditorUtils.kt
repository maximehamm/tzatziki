package io.nimbly.tzatziki

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

fun Editor.findTable(offset: Int): GherkinTable? {
    val file = getFile() ?: return null
    val element = file.findElementAt(offset) ?: return null
    return PsiTreeUtil.getContextOfType(element, GherkinTable::class.java)
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

fun Editor.navigateInTable(way: Boolean, editor: Editor, offset: Int = editor.caretModel.offset): Boolean {

    val table = findTable(offset) ?: return false
    val row = getTableRowAt(offset) ?: return false
    val file = getFile() ?: return false
    val element = file.findElementAt(offset) ?: return false

    fun goRight() : Boolean {
        var el: PsiElement? =
            if (element is GherkinTableCell)
                element
            else if (element is LeafPsiElement && element.parent is GherkinTableCell)
                element.parent
            else if (element is LeafPsiElement && element.parent is GherkinTableRow)
                element.nextSibling
            else if (element is LeafPsiElement && element.parent is GherkinTable)
                element.parent.firstChild
            else
                element.nextSibling

        var pipe: PsiElement? = null
        while (el != null) {
            if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
                pipe = el
                break
            }
            el = el.nextSibling
        }

        if (pipe == null)
            return false

        val target =
            run {
                val off = pipe!!.textOffset + 2
                if (off > editor.document.textLength)
                    return true
                if (editor.document.getLineNumber(offset) != editor.document.getLineNumber(off)) {
                    val nextRow = row.next()
                        ?: table.allRows().firstOrNull()!!
                    nextRow.psiCells.first().textOffset
                } else {
                    off
                }
            }

        editor.caretModel.moveToOffset(target)
        return true
    }

    fun goLeft() : Boolean {

        var el: PsiElement? =
            if (element is GherkinTableCell)
                element
            else if (element is LeafPsiElement && element.parent is GherkinTableCell)
                element.parent
            else if (element is LeafPsiElement && element.parent is GherkinTableRow)
                element.prevSibling
            else if (element is LeafPsiElement && element.parent is GherkinTable)
                element.parent.lastChild
            else
                element.prevSibling

        var pipe: PsiElement? = null
        while (el != null) {
            if (el is LeafPsiElement && (el as LeafPsiElement).elementType == GherkinTokenTypes.PIPE) {
                el = el!!.prevSibling
                break
            }
            el = el!!.prevSibling
        }
        while (el != null) {
            if (el is LeafPsiElement && (el as LeafPsiElement).elementType == GherkinTokenTypes.PIPE) {
                pipe = el
                break
            }
            el = el!!.prevSibling
        }

        val target =
            if (pipe == null) {
                val nextRow = row.previous()
                    ?: table.allRows().lastOrNull() !!
                nextRow.psiCells.last().textOffset
            }
            else {
                val off = pipe!!.textOffset + 2
                if (off > editor.document.textLength)
                    return true
                if (editor.document.getLineNumber(offset) != editor.document.getLineNumber(off)) {
                    val nextRow = row.next()
                        ?: table.allRows().firstOrNull()!!
                    nextRow.psiCells.first().textOffset
                } else {
                    off
                }
            }

        editor.caretModel.moveToOffset(target)
        return true
    }

    return if (way) goRight() else goLeft()
}

private fun GherkinTableRow.next() : GherkinTableRow? {
    val table = PsiTreeUtil.getParentOfType(this, GherkinTable::class.java) ?: return null
    return table.nextRow(this)
}

private fun GherkinTable.nextRow(row: GherkinTableRow): GherkinTableRow? {
    val allRows = allRows()
    val i = allRows.indexOf(row) + 1
    return if (i < allRows.size)
        allRows[i] else null
}

private fun GherkinTableRow.previous() : GherkinTableRow? {
    val table = PsiTreeUtil.getParentOfType(this, GherkinTable::class.java) ?: return null
    return table.previousRow(this)
}

private fun GherkinTable.previousRow(row: GherkinTableRow): GherkinTableRow? {
    val allRows = allRows()
    val i = allRows.indexOf(row) -1
    return if (i >=0)
        allRows[i] else null
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
    return PsiTreeUtil.getContextOfType(element, GherkinTableRow::class.java)
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
