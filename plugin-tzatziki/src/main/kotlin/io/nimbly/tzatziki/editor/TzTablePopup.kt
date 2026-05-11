/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.editor

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import icons.ActionIcons
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.util.TableEditOps
import java.awt.event.MouseEvent

/**
 * Popup menu surfaced when the user clicks the frame of a Gherkin table — shift / insert /
 * delete row & column actions, header toggles, and the global Cucumber+ on/off.
 *
 * Extracted from TzTableDecorator. Keeps the decorator focused on lifecycle + routing.
 */
internal fun showTablePopup(editor: Editor, geometry: TableGeometry, zone: HoverZone, mouseEvent: MouseEvent) {
    val am  = ActionManager.getInstance()
    val doc = editor.document
    val group = DefaultActionGroup()

    // Pipe X centres (used for column detection)
    val fm = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
    val half = fm.charWidth('|') / 2
    val pipeXs = geometry.pipeOffsets.map {
        editor.logicalPositionToXY(editor.offsetToLogicalPosition(it)).x + half
    }

    // Table rows (lines that start with |)
    val tableLines = (geometry.firstLine..geometry.lastLine).filter { line ->
        doc.charsSequence.subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
            .toString().trim().startsWith("|")
    }

    // Which column is the mouse over (by X)?
    val colIdx = pipeXs.zipWithNext()
        .indexOfFirst { (a, b) -> mouseEvent.x >= a && mouseEvent.x <= b }
        .coerceAtLeast(0)
    val isFirstColumn = colIdx == 0
    val isLastColumn  = colIdx >= pipeXs.size - 2

    // Which row is nearest to the mouse (by Y centre distance)?
    val rowIdx = tableLines.indices.minByOrNull { i ->
        val lineY = editor.logicalPositionToXY(editor.offsetToLogicalPosition(doc.getLineStartOffset(tableLines[i]))).y
        Math.abs(mouseEvent.y - (lineY + editor.lineHeight / 2))
    } ?: 0
    val isFirstRow = rowIdx == 0
    val isLastRow  = rowIdx >= tableLines.size - 1

    // Move caret into the target cell so Shift actions pass their enabled check
    moveCaretToTableCell(editor, doc, tableLines,
        when (zone) {
            HoverZone.LEFT_BORDER, HoverZone.RIGHT_BORDER, HoverZone.HEADER_SEPARATOR -> rowIdx to 0
            HoverZone.TOP_BORDER, HoverZone.BOTTOM_BORDER                             -> 0 to colIdx
        }
    )

    // 1. Shift actions (zone-specific, position-filtered)
    fun safeAdd(id: String) { am.getAction(id)?.let { group.add(it) } }
    val shiftAdded = when (zone) {
        HoverZone.LEFT_BORDER, HoverZone.RIGHT_BORDER, HoverZone.HEADER_SEPARATOR -> {
            var added = false
            if (!isFirstRow) { safeAdd("io.nimbly.tzatziki.ShiftUp");   added = true }
            if (!isLastRow)  { safeAdd("io.nimbly.tzatziki.ShiftDown"); added = true }
            added
        }
        HoverZone.TOP_BORDER, HoverZone.BOTTOM_BORDER -> {
            var added = false
            if (!isFirstColumn) { safeAdd("io.nimbly.tzatziki.ShiftLeft");  added = true }
            if (!isLastColumn)  { safeAdd("io.nimbly.tzatziki.ShiftRight"); added = true }
            added
        }
    }
    if (shiftAdded) group.addSeparator()

    // 1b. Add / delete row & column (zone-specific)
    val popupRef = arrayOfNulls<JBPopup>(1)
    val popupCloser: () -> Unit = { popupRef[0]?.cancel() }
    val rowCount = tableLines.size
    val columnCount = (pipeXs.size - 1).coerceAtLeast(1)
    var editAdded = false
    when (zone) {
        HoverZone.LEFT_BORDER, HoverZone.RIGHT_BORDER, HoverZone.HEADER_SEPARATOR -> {
            group.add(TableEditAction(editor, tableLines, TableEditOps.Op.InsertRow(rowIdx, above = true),
                ActionIcons.ROW_ADD, popupCloser))
            group.add(TableEditAction(editor, tableLines, TableEditOps.Op.InsertRow(rowIdx, above = false),
                ActionIcons.ROW_ADD, popupCloser))
            if (rowCount > 1) {
                group.add(TableEditAction(editor, tableLines, TableEditOps.Op.DeleteRow(rowIdx),
                    ActionIcons.ROW_DELETE, popupCloser))
            }
            editAdded = true
        }
        HoverZone.TOP_BORDER, HoverZone.BOTTOM_BORDER -> {
            group.add(TableEditAction(editor, tableLines, TableEditOps.Op.InsertColumn(colIdx, before = true),
                ActionIcons.COLUMN_ADD, popupCloser))
            group.add(TableEditAction(editor, tableLines, TableEditOps.Op.InsertColumn(colIdx, before = false),
                ActionIcons.COLUMN_ADD, popupCloser))
            if (columnCount > 1) {
                group.add(TableEditAction(editor, tableLines, TableEditOps.Op.DeleteColumn(colIdx),
                    ActionIcons.COLUMN_DELETE, popupCloser))
            }
            editAdded = true
        }
    }
    if (editAdded) group.addSeparator()

    // 2. Header type — row / column (click selected = toggle off; closes the popup)
    group.add(SetHeaderAction(editor, geometry, "row",    "Header: row",    popupCloser))
    group.add(SetHeaderAction(editor, geometry, "column", "Header: column", popupCloser))
    group.addSeparator()

    // 3. Toggle Cucumber+ — wrapped to close the popup after toggling
    group.add(object : ToggleAction("Toggle Cucumber+") {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun isSelected(e: AnActionEvent) = TOGGLE_CUCUMBER_PL
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            // Do not call AnAction.actionPerformed(e) directly: it is @ApiStatus.OverrideOnly.
            // Route through ActionUtil so listeners + telemetry get notified properly.
            am.getAction("io.nimbly.tzatziki.ToggleTzatziki")?.let { toggle ->
                ActionUtil.performAction(toggle, e)
            }
            popupCloser()
        }
    })

    val dataContext = DataManager.getInstance().getDataContext(editor.contentComponent)
    val popup = JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
    popupRef[0] = popup

    // Persistent orange target indicator on the affected row / column while the popup is
    // open — dashed for columns, solid for rows (mirrors the DnD source visual). Cleared
    // when the popup closes through any path (action chosen, Escape, click outside).
    val targetFirstLine = tableLines.firstOrNull() ?: geometry.firstLine
    when (zone) {
        HoverZone.TOP_BORDER, HoverZone.BOTTOM_BORDER -> {
            io.nimbly.tzatziki.editor.TzTableDecorator
                .showMenuTargetColumn(editor, targetFirstLine, colIdx)
        }
        HoverZone.LEFT_BORDER, HoverZone.RIGHT_BORDER, HoverZone.HEADER_SEPARATOR -> {
            tableLines.getOrNull(rowIdx)?.let { rowLine ->
                io.nimbly.tzatziki.editor.TzTableDecorator
                    .showMenuTargetRow(editor, targetFirstLine, rowLine)
            }
        }
    }
    popup.addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
            io.nimbly.tzatziki.editor.TzTableDecorator.clearMenuTarget(editor)
        }
    })

    // Offset the popup a few pixels away from the click so the freshly-painted orange
    // target indicator stays visible underneath / next to the menu.
    val (dx, dy) = when (zone) {
        HoverZone.TOP_BORDER                              -> 0 to 10        // push down
        HoverZone.BOTTOM_BORDER                           -> 0 to -10       // push up
        HoverZone.LEFT_BORDER, HoverZone.HEADER_SEPARATOR -> 10 to 0        // push right
        HoverZone.RIGHT_BORDER                            -> -10 to 0       // push left
    }
    val showPoint = java.awt.Point(mouseEvent.x + dx, mouseEvent.y + dy)
    popup.show(RelativePoint(editor.contentComponent, showPoint))
}

/**
 * Moves the caret into the (rowIdx, colIdx) cell of [tableLines] so that the shift
 * actions pass their "caret is in a cell" enabled check.
 */
private fun moveCaretToTableCell(
    editor: Editor, doc: Document,
    tableLines: List<Int>, cell: Pair<Int, Int>
) {
    val (rowIdx, colIdx) = cell
    val line = tableLines.getOrElse(rowIdx) { tableLines.firstOrNull() ?: return }
    val ls = doc.getLineStartOffset(line)
    val lineText = doc.charsSequence.subSequence(ls, doc.getLineEndOffset(line)).toString()
    var pipeCount = -1
    var offset = ls
    var idx = 0
    while (idx < lineText.length) {
        val p = lineText.indexOf('|', idx)
        if (p < 0) break
        pipeCount++
        if (pipeCount == colIdx) { offset = ls + p + 1; break }
        idx = p + 1
    }
    editor.caretModel.moveToOffset(offset.coerceAtMost(doc.textLength - 1))
}

/** Toggle action that sets / clears the `# @header: row|column` annotation above a table. */
internal class SetHeaderAction(
    private val editor: Editor,
    private val geometry: TableGeometry,
    private val headerType: String,  // "row" or "column"
    text: String,
    private val closePopup: () -> Unit
) : ToggleAction(text) {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean =
        tableAnnotation(editor.document, editor.document.charsSequence, geometry.firstLine) == headerType

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = editor.project ?: return
        WriteCommandAction.runWriteCommandAction(project, "Set Table Header", null, {
            val doc         = editor.document
            val commentLine = findHeaderCommentLine(doc, geometry.firstLine)
            val current     = tableAnnotation(doc, doc.charsSequence, geometry.firstLine)
            if (!state || current == headerType) {
                // Toggle off: remove the comment
                if (commentLine >= 0) {
                    val start = doc.getLineStartOffset(commentLine)
                    val end   = if (commentLine + 1 < doc.lineCount)
                        doc.getLineStartOffset(commentLine + 1)
                    else
                        doc.getLineEndOffset(commentLine)
                    doc.deleteString(start, end)
                }
            } else {
                val firstRowStart = doc.getLineStartOffset(geometry.firstLine)
                val indent = doc.charsSequence
                    .subSequence(firstRowStart, doc.getLineEndOffset(geometry.firstLine))
                    .toString().takeWhile { it == ' ' || it == '\t' }
                val comment = "$indent# @header: $headerType"
                if (commentLine >= 0) {
                    doc.replaceString(doc.getLineStartOffset(commentLine), doc.getLineEndOffset(commentLine), comment)
                } else {
                    doc.insertString(firstRowStart, "$comment\n")
                }
            }
        })
        closePopup()
    }
}

/** Wrapper around a [TableEditOps.Op] that closes the popup after applying. */
internal class TableEditAction(
    private val editor: Editor,
    private val tableLines: List<Int>,
    private val op: TableEditOps.Op,
    icon: javax.swing.Icon?,
    private val closePopup: () -> Unit
) : AnAction(op.title, null, icon) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun actionPerformed(e: AnActionEvent) {
        TableEditOps.apply(editor, tableLines, op)
        closePopup()
        // Flash the freshly-inserted row / column so the user sees where it landed —
        // mirrors the drag-and-drop and shift action landing cue. Delete ops don't get
        // a flash (nothing to highlight after a removal).
        when (val o = op) {
            is TableEditOps.Op.InsertColumn -> {
                val newColIdx = if (o.before) o.atIndex else o.atIndex + 1
                io.nimbly.tzatziki.editor.TzTableDecorator
                    .flashColumn(editor, tableLines.first(), newColIdx)
            }
            is TableEditOps.Op.InsertRow -> {
                val refIdx = o.atIndex.coerceIn(0, tableLines.size - 1)
                val newRowLine = if (o.above) tableLines[refIdx] else tableLines[refIdx] + 1
                io.nimbly.tzatziki.editor.TzTableDecorator
                    .flashRow(editor, tableLines.first(), newRowLine)
            }
            else -> { /* DeleteRow / DeleteColumn / Move / Shift → no insert flash */ }
        }
    }
}

/** Line index of the existing `# @header: ...` comment above the table, or -1. */
private fun findHeaderCommentLine(doc: Document, tableLine: Int): Int {
    var line = tableLine - 1
    while (line >= 0) {
        val t = doc.charsSequence.subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line)).toString().trim()
        when {
            t.isEmpty() -> line--
            t.matches(Regex("#\\s*@header:.*")) -> return line
            else -> return -1
        }
    }
    return -1
}

/**
 * Reads the `# @header: row|column` annotation that may sit immediately above [tableLine].
 * Returns "row", "column", or null when no recognised annotation is present. Shared by
 * the popup (toggle) and the header detection used by the renderer.
 */
internal fun tableAnnotation(doc: Document, text: CharSequence, tableLine: Int): String? {
    var line = tableLine - 1
    while (line >= 0) {
        val s = doc.getLineStartOffset(line); val e = doc.getLineEndOffset(line)
        val t = text.subSequence(s, e).toString().trim()
        when {
            t.isEmpty() -> line--
            t.matches(Regex("#\\s*@header:\\s*row\\s*"))    -> return "row"
            t.matches(Regex("#\\s*@header:\\s*column\\s*")) -> return "column"
            else -> return null
        }
    }
    return null
}
