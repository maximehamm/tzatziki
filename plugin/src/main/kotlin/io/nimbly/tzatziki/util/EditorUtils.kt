/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.util

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.highlighting.HighlightManager.HIDE_BY_ANY_KEY
import com.intellij.ide.DataManager
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorColors.SEARCH_RESULT_ATTRIBUTES
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.DocumentUtil
import com.intellij.util.containers.ContainerUtil
import io.nimbly.tzatziki.psi.*
import org.jetbrains.plugins.cucumber.psi.*
import java.awt.Dimension
import java.awt.Point
import java.util.function.Consumer

fun Editor.findTableAt(offset: Int): GherkinTable? {
    val file = file ?: return null

    val adjustedOffset =
        when (offset) {
            getLineStartOffsetFromOffset() -> offset + 2
            getLineStartOffsetFromOffset() + 1 -> offset + 1
            getLineEndOffsetFromOffset() -> offset - 1
            else -> offset
        }

    val element = file.findElementAt(adjustedOffset) ?: return null
    if (element.nextSibling is GherkinTable)
        return element.nextSibling as GherkinTable

    if (element.prevSibling is GherkinExamplesBlock)
        return (element.prevSibling as GherkinExamplesBlock).table

    return PsiTreeUtil.getContextOfType(element, GherkinTable::class.java)
}

fun Editor.cellAt(offset: Int): GherkinTableCell? = file?.cellAt(offset)

val Editor.file: PsiFile?
    get() {
        val project = project ?: return null
        return PsiDocumentManager.getInstance(project).getPsiFile(document)
    }

fun Editor.getTableColumnIndexAt(offset: Int): Int? {
    val file = file ?: return null
    var element = file.findElementAt(offset) ?: return null
    if (element.parent is GherkinTableCell)
        element = element.parent

    var col = -1
    var el: PsiElement? = element
    while (el != null) {
        if (el.elementType == GherkinTokenTypes.PIPE)
            col++
        el = el.prevSibling
    }

    if (col < 0 && element.prevSibling is GherkinTableRow) {
        col = element.prevSibling.children.count { it is GherkinTableCell }
    }

    return col
}

fun Editor.getTableRowAt(offset: Int): GherkinTableRow? {

    val file = file ?: return null
    val element = file.findElementAt(
        if (getLineEndOffsetFromOffset() == offset) offset - 1 else offset
    )

    var row = PsiTreeUtil.getContextOfType(element, GherkinTableRow::class.java)
    if (row == null && element?.nextSibling is GherkinTableRow)
        row = element.nextSibling as GherkinTableRow?
    if (row == null && element?.nextSibling is GherkinTable)
        row = element.nextSibling.firstChild as GherkinTableRow?

    return row
}

fun Editor.getLineEndOffsetFromOffset(offset: Int = caretModel.offset): Int {
    return DocumentUtil.getLineEndOffset(offset, document)
}

fun Editor.getLineStartOffsetFromOffset(offset: Int = caretModel.offset): Int {
    return DocumentUtil.getLineStartOffset(offset, document)
}

fun Editor.getLineNumber(offset: Int = caretModel.offset): Int {
    return document.getLineNumber(offset)
}

val HIGHLIGHTERS_RANGE = mutableListOf<TextRange>()

fun Editor.highlight(start: Int, end: Int, columnMode: Boolean = true) {

    HIGHLIGHTERS_RANGE.clear()

    var end = end
    if (document.getText(TextRange(end - 1, end)).trim { it <= ' ' }.isEmpty())
        end--

    if (columnMode) {
        val colStart = document.getColumnAt(start)
        val width = document.getColumnAt(end) - colStart
        val lineStart = document.getLineNumber(start)
        val lineEnd = document.getLineNumber(end)
        for (line in lineStart..lineEnd) {
            val start1 = document.getLineStartOffset(line) + colStart
            val end1 = start1 + width
            highlight(start1, end1, HIGHLIGHTERS_RANGE)
        }
    } else {
        highlight(start, end, HIGHLIGHTERS_RANGE)
    }
}

fun Editor.highlight(start: Int, end: Int, outHighlightersRanges: MutableList<TextRange>?) {

    val p = project ?: return

    HighlightManager.getInstance(p)
        .addOccurrenceHighlight(this, start, end, SEARCH_RESULT_ATTRIBUTES, HIDE_BY_ANY_KEY, null)

    outHighlightersRanges?.add(TextRange(start, end))
}

fun Editor.clearHighlights(
    outHighlighters: MutableCollection<RangeHighlighter?>,
    outHighlightersRanges: MutableCollection<TextRange?>?
) {

    val p = project ?: return

    val highlightManager = HighlightManager.getInstance(p)
    outHighlighters.forEach(Consumer { r: RangeHighlighter? ->
        highlightManager.removeSegmentHighlighter(
            this,
            r!!
        )
    })
    outHighlighters.clear()
    outHighlightersRanges?.clear()
}

fun EditorEx.toggleColumnMode() {
    val selectionModel = this.selectionModel
    val caretModel = this.caretModel
    if (this.isColumnMode) {
        var hasSelection = false
        var selStart = 0
        var selEnd = 0
        if (caretModel.supportsMultipleCarets()) {
            hasSelection = true
            val allCarets = caretModel.allCarets
            var fromCaret = allCarets[0]
            var toCaret = allCarets[allCarets.size - 1]
            if (fromCaret === caretModel.primaryCaret) {
                val tmp = fromCaret
                fromCaret = toCaret
                toCaret = tmp
            }
            selStart = fromCaret.leadSelectionOffset
            selEnd = if (toCaret.selectionStart == toCaret.leadSelectionOffset) toCaret.selectionEnd else toCaret.selectionStart
        }
        this.isColumnMode = false
        caretModel.removeSecondaryCarets()
        if (hasSelection) {
            selectionModel.setSelection(selStart, selEnd)
        } else {
            selectionModel.removeSelection()
        }
    } else {
        caretModel.removeSecondaryCarets()
        val hasSelection = selectionModel.hasSelection()
        val selStart = selectionModel.selectionStart
        val selEnd = selectionModel.selectionEnd
        val blockStart: LogicalPosition
        val blockEnd: LogicalPosition
        if (caretModel.supportsMultipleCarets()) {
            val logicalSelStart = this.offsetToLogicalPosition(selStart)
            val logicalSelEnd = this.offsetToLogicalPosition(selEnd)
            val caretOffset = caretModel.offset
            blockStart = if (selStart == caretOffset) logicalSelEnd else logicalSelStart
            blockEnd = if (selStart == caretOffset) logicalSelStart else logicalSelEnd
        } else {
            blockStart = if (selStart == caretModel.offset) caretModel.logicalPosition else this.offsetToLogicalPosition(selStart)
            blockEnd = if (selEnd == caretModel.offset) caretModel.logicalPosition else this.offsetToLogicalPosition(selEnd)
        }
        this.isColumnMode = true
        if (hasSelection) {
            selectionModel.setBlockSelection(blockStart, blockEnd)
        } else {
            selectionModel.removeSelection()
        }
    }
}

fun Editor.createEditorContext(): DataContext {

    /**
     * Since IDEA 2021.1 EAP
    fun Editor.createEditorContext(): DataContext {
    return SimpleDataContext.builder()
    .setParent(DataManager.getInstance().getDataContext(contentComponent))
    .add(CommonDataKeys.HOST_EDITOR, if (this is EditorWindow) delegate else this)
    .add(CommonDataKeys.EDITOR, this)
    .build()
    }
     */

    val hostEditor: Any = if (this is EditorWindow) this.delegate else this
    val map: Map<String, Any> = ContainerUtil.newHashMap(
        Pair.create(CommonDataKeys.HOST_EDITOR.name, hostEditor),
        Pair.createNonNull(CommonDataKeys.EDITOR.name, this),
        Pair.createNonNull(CommonDataKeys.CARET.name, this.caretModel.currentCaret)
    )
    val parent = DataManager.getInstance().getDataContext(this.contentComponent)
    return SimpleDataContext.getSimpleContext(map, parent)
}

fun Editor.setColumnMode(columnnMode: Boolean) {
    if (isColumnMode != columnnMode && this is EditorEx) {
        toggleColumnMode()
    }
}

fun Editor.selectTableColumn(table: GherkinTable, columnNumber: Int) {

    val cellStart = table.firstRow.cell(columnNumber)
    val cellEnd = table.lastRow.cell(columnNumber)

    val start = offsetToLogicalPosition(cellStart.previousPipe.textOffset + 1)
    val end = offsetToLogicalPosition(cellEnd.nextPipe.textRange.startOffset + 1)

    val caretStates = EditorModificationUtil.calcBlockSelectionState(this, start, end)
    caretModel.setCaretsAndSelections(caretStates, true)
}

fun Editor.selectTableRow(table: GherkinTable, rowNumber: Int) {

    val row = table.row(rowNumber)

    val start = offsetToLogicalPosition(row.firstCell!!.previousPipe.textOffset)
    val end = offsetToLogicalPosition(row.lastCell!!.nextPipe.textRange.startOffset + 1)

    val caretStates = EditorModificationUtil.calcBlockSelectionState(this, start, end)
    caretModel.setCaretsAndSelections(caretStates, true)
}

fun Editor.selectTableCells(table: GherkinTable, coordinates: Point, dimension: Dimension) {

    val fromCell = table.cellAt(coordinates) ?: return
    val toCell = table.cellAt(coordinates.shift(dimension)) ?: return

    val margin = if (table.columnCount == dimension.width) 0 else 1
    val start = offsetToLogicalPosition(fromCell.previousPipe.textOffset + margin)
    val end = offsetToLogicalPosition(toCell.nextPipe.textRange.startOffset + 1)

    val caretStates = EditorModificationUtil.calcBlockSelectionState(this, start, end)
    caretModel.setCaretsAndSelections(caretStates, true)
}

fun Editor.tableColumn(
    table: GherkinTable,
    columnNumber: Int,
    shift: Int = 0
): kotlin.Pair<LogicalPosition, LogicalPosition> {

    var start = offsetToLogicalPosition(table.firstRow.cell(columnNumber).previousPipe.textOffset + 1)
    var end = offsetToLogicalPosition(table.lastRow.cell(columnNumber).nextPipe.textOffset + 1)

    start = LogicalPosition(start.line, start.column - shift)
    end = LogicalPosition(end.line, end.column - shift)

    return start to end
}

fun Editor.select(position: kotlin.Pair<LogicalPosition, LogicalPosition>) {
    val caretStates = EditorModificationUtil.calcBlockSelectionState(this, position.first, position.second)
    caretModel.setCaretsAndSelections(caretStates, true)
}

fun Editor.isSelectionOfBlankCells(): Boolean {

    if (!selectionModel.hasSelection(true))
        return false

    val cell = cellAt(selectionModel.selectionStart)
        ?: return false

    selectionModel.blockSelectionStarts.forEachIndexed { index, start ->
        val end = selectionModel.blockSelectionEnds[index]
        val text = document.getText(TextRange(start, end))
        if (!text.matches(Regex("^[ |]+\$")))
            return false
    }

    return true
}

fun Point.shift(dimension: Dimension) = Point(x + dimension.width - 1, y + dimension.height - 1)