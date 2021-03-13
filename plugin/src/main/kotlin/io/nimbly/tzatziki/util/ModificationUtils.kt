package io.nimbly.tzatziki.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinTable

const val FEATURE_HEAD =
    "Feature: x\n" +
        "Scenario Outline: xx\n" +
        "Examples: xxx\n"

fun addNewColum(c: Char, editor: Editor, file: PsiFile, project: Project, fileType: FileType): Boolean {
    if (c != '|') return false
    if (fileType != GherkinFileType.INSTANCE) return false
    val offset = editor.caretModel.offset
    val table = editor.findTable(offset) ?: return false
    val document = file.getDocument() ?: return false
    val currentCol = table.columnNumberAt(offset)
    val currentRow = table.rowNumberAt(offset) ?: return false

    // Where I am ? In table ? At its left ? At its right ?
    val origineColumn = document.getColumnAt(offset)
    val tableColumnStart = document.getColumnAt(table.row(0).cell(0).startOffset)
    val tableColumnEnd = document.getColumnAt(table.row(0).psiCells.last().endOffset)
    val where = when {
        origineColumn<tableColumnStart -> -1 // Left
        origineColumn>tableColumnEnd -> 1    // Right
        else -> 0
    }

    // Build new table as string
    val s = StringBuilder()
    table.allRows().forEachIndexed { y, row ->
        if (where<0)
            s.append("|   ")
        row.psiCells.forEachIndexed { x, cell ->
            s.append('|').append(cell.text)
            if (x == currentCol)
                s.append("|   ")
        }
        if (where>0)
            s.append("|   ")

        s.append('|').append('\n')
    }

    // Commit document
    PsiDocumentManager.getInstance(project).let {
        it.commitDocument(editor.document)
        it.doPostponedOperationsAndUnblockDocument(editor.document)
    }

    // Apply modifications
    ApplicationManager.getApplication().runWriteAction {

        // replace table
        val tempTable = CucumberElementFactory
            .createTempPsiFile(project, FEATURE_HEAD + s)
            .children[0].children[0].children[0].children[0]

        val newTable = table.replace(tempTable) as GherkinTable
        val newRow = newTable.row(currentRow)

        // Find caret target
        val targetColumn =
            when {
                where <0 -> 0
                where >0 -> newRow.psiCells.size-1
                else -> currentCol!! +1
            }

        // Move caret
        editor.caretModel.moveToOffset(newRow.cell(targetColumn).previousPipe().textOffset + 2)

        // Format table
        newTable.format()
    }

    return true
}

fun Editor.addTableRow(offset: Int = caretModel.offset): Boolean {

    val colIdx = getTableColumnIndexAt(offset) ?: return false
    val table = findTable(offset) ?: return false
    val row = getTableRowAt(offset) ?: return false

    val insert = offset == getLineEndOffset(offset)

    ApplicationManager.getApplication().runWriteAction {

        val newRow = row.addRowAfter()

        var newCaret = newRow.textOffset + 1
        if (!insert)
            newCaret += colIdx * 2
        caretModel.moveToOffset(newCaret)

        CodeStyleManager.getInstance(project!!).reformatText(
            table.containingFile,
            table.textRange.startOffset, table.textRange.endOffset
        )

        caretModel.moveToOffset(caretModel.offset + 1)
    }

    return true
}
