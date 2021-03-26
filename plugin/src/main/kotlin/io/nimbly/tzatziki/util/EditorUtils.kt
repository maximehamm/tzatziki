/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorColors.SEARCH_RESULT_ATTRIBUTES
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.DocumentUtil
import com.intellij.util.containers.ContainerUtil
import io.nimbly.tzatziki.psi.*
import org.jetbrains.plugins.cucumber.psi.*
import org.junit.Assert
import java.awt.Dimension
import java.awt.Point
import java.awt.event.InputEvent
import java.util.function.Consumer

fun Editor.findTableAt(offset: Int): GherkinTable? {
    val file = getFile() ?: return null

    val adjustedOffset =
        when (offset) {
            getLineStartOffsetFromOffset() -> offset+2
            getLineStartOffsetFromOffset()+1 -> offset+1
            getLineEndOffsetFromOffset() -> offset-1
            else -> offset
        }

    val element = file.findElementAt(adjustedOffset) ?: return null
    if (element.nextSibling is GherkinTable)
        return element.nextSibling as GherkinTable

    if (element.prevSibling is GherkinExamplesBlock)
        return (element.prevSibling as GherkinExamplesBlock).table

    return PsiTreeUtil.getContextOfType(element, GherkinTable::class.java)
}

fun Editor.cellAt(offset: Int): GherkinTableCell?
    = getFile()?.cellAt(offset)

fun Editor.getFile(): PsiFile? {
    val project = project ?: return null
    return PsiDocumentManager.getInstance(project).getPsiFile(document)
}

fun Editor.navigateInTableWithEnter(offset: Int = caretModel.offset): Boolean {

    val row = getTableRowAt(offset) ?: return false
    val colIdx = getTableColumnIndexAt(offset) ?: return false
    if (colIdx<0) return false
    val next = row.next ?: return false
    if (next.psiCells.size <= colIdx) return false

    val cell = next.psiCells[colIdx]
    val pipe = cell.previousPipe ?: return false

    caretModel.moveToOffset(pipe.textOffset +2)
    return true
}

fun Editor.navigateInTableWithTab(way: Boolean, editor: Editor, offset: Int = editor.caretModel.offset): Boolean {

    val table = findTableAt(offset) ?: return false
    val row = getTableRowAt(offset) ?: return false
    val file = getFile() ?: return false
    val element = file.findElementAt(offset) ?: return false

    fun goRight() : Boolean {
        var el: PsiElement? =
            if (element is GherkinTableCell)
                element
            else if (element is LeafPsiElement && element.elementType == GherkinTokenTypes.PIPE)
                element
            else if (element is LeafPsiElement && element.parent is GherkinTableCell)
                element.parent
            else if (element is LeafPsiElement && element.parent is GherkinTableRow)
                element.nextSibling
            else if (element is LeafPsiElement && element.parent is GherkinTable)
                element.nextSibling.firstChild
            else if (element is LeafPsiElement && element.nextSibling is GherkinTable)
                element.nextSibling.firstChild.firstChild
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
                    val nextRow = row.next
                        ?: table.allRows.firstOrNull()!!
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
            else if (element is LeafPsiElement && element.prevSibling is GherkinTableRow)
                row.lastChild ?: return false
            else if (element is LeafPsiElement && element.prevSibling is GherkinFeature)
                row.lastChild
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
                val nextRow = row.previous
                    ?: table.allRows.lastOrNull() !!
                nextRow.psiCells.last().textOffset
            }
            else {
                val off = pipe!!.textOffset + 2
                if (off > editor.document.textLength)
                    return true
                if (editor.document.getLineNumber(offset) != editor.document.getLineNumber(off)) {
                    val nextRow = row.next
                        ?: table.allRows.firstOrNull()!!
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

fun Editor.getTableColumnIndexAt(offset: Int): Int? {
    val file = getFile() ?: return null
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

    if (col<0 && element.prevSibling is GherkinTableRow) {
        col = element.prevSibling.children.count { it is GherkinTableCell }
    }

    return col
}

fun Editor.getTableRowAt(offset: Int): GherkinTableRow? {

    val file = getFile() ?: return null
    val element = file.findElementAt(
        if (getLineEndOffsetFromOffset() == offset) offset-1 else offset)

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
    }
    else {
        highlight(start, end, HIGHLIGHTERS_RANGE)
    }
}

fun Editor.highlight(start: Int, end: Int, outHighlightersRanges: MutableList<TextRange>?) {

    HighlightManager.getInstance(project)
        .addOccurrenceHighlight(this, start, end, SEARCH_RESULT_ATTRIBUTES, HIDE_BY_ANY_KEY, null)

    outHighlightersRanges?.add(TextRange(start, end))
}

fun Editor.clearHighlights(
    outHighlighters: MutableCollection<RangeHighlighter?>,
    outHighlightersRanges: MutableCollection<TextRange?>?) {
    val highlightManager = HighlightManager.getInstance(project)
    outHighlighters.forEach(Consumer { r: RangeHighlighter? ->
        highlightManager.removeSegmentHighlighter(
            this,
            r!!
        )
    })
    outHighlighters.clear()
    outHighlightersRanges?.clear()
}

fun Editor.executeAction(actionId: String) {
    executeAction(actionId, false)
}

fun Editor.executeAction(actionId: String, assertActionIsEnabled: Boolean) {
    val actionManager = ActionManagerEx.getInstanceEx()
    val action = actionManager.getAction(actionId)
    Assert.assertNotNull(action)
    val event = AnActionEvent.createFromAnAction(
        action,
        null as InputEvent?,
        "",
        createEditorContext()
    )
    action.beforeActionPerformedUpdate(event)
    if (!event.presentation.isEnabled) {
        Assert.assertFalse("Action $actionId is disabled", assertActionIsEnabled)
    } else {
        actionManager.fireBeforeActionPerformed(action, event.dataContext, event)
        action.actionPerformed(event)
        actionManager.fireAfterActionPerformed(action, event.dataContext, event)
    }
}


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
fun Editor.createEditorContext(): DataContext {
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
    if (isColumnMode != columnnMode)
        executeAction( "EditorToggleColumnMode")
}

fun Editor.selectTableColumn(table: GherkinTable, columnNumber: Int) {

    val cellStart = table.firstRow.cell(columnNumber)
    val cellEnd = table.lastRow.cell(columnNumber)

    val start = offsetToLogicalPosition(cellStart.previousPipe.textOffset+1)
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
    val toCell = table.cellAt(coordinates.shift(dimension)) ?:return

    val margin = if (table.columnCount == dimension.width) 0 else 1
    val start = offsetToLogicalPosition(fromCell.previousPipe.textOffset+ margin)
    val end = offsetToLogicalPosition(toCell.nextPipe.textRange.startOffset + 1)

    val caretStates = EditorModificationUtil.calcBlockSelectionState(this, start, end)
    caretModel.setCaretsAndSelections(caretStates, true)
}

fun Editor.tableColumn(table: GherkinTable, columnNumber: Int, shift: Int = 0): kotlin.Pair<LogicalPosition, LogicalPosition> {

    var start = offsetToLogicalPosition(table.firstRow.cell(columnNumber).previousPipe.textOffset+1)
    var end = offsetToLogicalPosition(table.lastRow.cell(columnNumber).nextPipe.textOffset+1)

    start = LogicalPosition(start.line, start.column -shift)
    end = LogicalPosition(end.line, end.column -shift)

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

fun Point.shift(dimension: Dimension)
    = Point(x + dimension.width -1, y + dimension.height -1)