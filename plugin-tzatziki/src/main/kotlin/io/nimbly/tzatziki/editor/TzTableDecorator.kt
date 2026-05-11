package io.nimbly.tzatziki.editor

import com.intellij.icons.AllIcons
import icons.ActionIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.util.TableEditOps
import io.nimbly.tzatziki.util.findTableAt
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinHighlighter
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent

/**
 * Draws a rounded-corner grid around Gherkin tables and handles hover + popup interactions.
 *
 * Header detection:
 *   - Examples: blocks  → always a header (PSI)
 *   - DataTables        → add "# @header: row" or "# @header: column" above the table
 */
class TzTableDecorator : EditorFactoryListener {

    private val editorHighlighters = mutableMapOf<Editor, MutableList<RangeHighlighter>>()
    private val editorDisposables  = mutableMapOf<Editor, com.intellij.openapi.Disposable>()
    private val editorGeometries   = mutableMapOf<Editor, List<TableGeometry>>()
    private val editorHover        = mutableMapOf<Editor, HoverState?>()
    private val editorDrag         = mutableMapOf<Editor, DragState?>()
    private val editorPostDropFlash = mutableMapOf<Editor, PostDropFlash?>()
    /** One-shot flag: when set, the next mouseClicked is swallowed (avoids the trailing popup
     *  after a drag-release on the same point that fired the press). */
    private val editorSkipNextClick = mutableSetOf<Editor>()

    companion object {
        private var instance: TzTableDecorator? = null
        fun refreshAll() {
            val dec = instance ?: return
            dec.editorHighlighters.keys.toList().forEach { dec.scheduleRefresh(it) }
        }
        /** Schedule the 1-second orange flash over [columnIndex] of the table starting at
         *  [tableFirstLine]. Called by shift-left / shift-right to mirror the drag-and-drop UX. */
        fun flashColumn(editor: Editor, tableFirstLine: Int, columnIndex: Int) {
            val dec = instance ?: return
            dec.postFlash(editor, PostDropFlash(tableFirstLine, columnIndex = columnIndex))
        }
        /** Schedule the 1-second orange flash over the row at editor line [rowLine] inside the
         *  table starting at [tableFirstLine]. Called by shift-up / shift-down. */
        fun flashRow(editor: Editor, tableFirstLine: Int, rowLine: Int) {
            val dec = instance ?: return
            dec.postFlash(editor, PostDropFlash(tableFirstLine, rowLine = rowLine))
        }
        private const val DRAG_THRESHOLD = 4
    }

    init { instance = this }

    // ---- Lifecycle ----

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val vfile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (vfile.extension != "feature") return

        scheduleRefresh(editor)

        val disposable = Disposer.newDisposable()
        editorHighlighters.getOrPut(editor) { mutableListOf() }
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) { scheduleRefresh(editor) }
        }, disposable)

        val mouseHandler = object : EditorMouseListener, EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent)   = handleMouseMove(editor, e)
            override fun mouseClicked(e: EditorMouseEvent) = handleMouseClick(editor, e)
            override fun mousePressed(e: EditorMouseEvent) = handleMousePressed(editor, e)
            override fun mouseDragged(e: EditorMouseEvent) = handleMouseDragged(editor, e)
            override fun mouseReleased(e: EditorMouseEvent) = handleMouseReleased(editor, e)
        }
        editor.addEditorMouseListener(mouseHandler, disposable)
        editor.addEditorMouseMotionListener(mouseHandler, disposable)

        editorDisposables[editor] = disposable
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        editorDisposables.remove(editor)?.let { Disposer.dispose(it) }
        editorHighlighters.remove(editor)
        editorGeometries.remove(editor)
        editorHover.remove(editor)
        editorDrag.remove(editor)
        editorPostDropFlash.remove(editor)
        editorSkipNextClick.remove(editor)
    }

    private fun scheduleRefresh(editor: Editor) {
        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater
            refresh(editor)
        }
    }

    // ---- Rendering ----

    private fun refresh(editor: Editor) {
        val markupModel = editor.markupModel
        editorHighlighters[editor]?.forEach { markupModel.removeHighlighter(it) }
        editorHighlighters.remove(editor)

        // When Cucumber+ is disabled, clear decorations and stop.
        // Keep the editor in the map (empty list) so refreshAll() can find it on re-enable.
        if (!TOGGLE_CUCUMBER_PL) {
            editorHighlighters[editor] = mutableListOf()
            editorGeometries[editor] = emptyList()
            return
        }

        val document = editor.document
        // Commit PSI before reading header lines: WriteCommandAction updates the document
        // immediately but PSI offsets are stale until committed, causing line-number mismatches.
        editor.project?.let { PsiDocumentManager.getInstance(it).commitDocument(document) }

        val text = document.charsSequence
        val newHighlighters = mutableListOf<RangeHighlighter>()
        val newGeometries   = mutableListOf<TableGeometry>()

        val headerRowLines    = findHeaderRowLines(editor)
        val headerColumnLines = findHeaderColumnLines(editor)

        var tableFirstLine: Int? = null

        fun processTable(firstLine: Int, lastLine: Int) {
            val firstLineStart = document.getLineStartOffset(firstLine)
            val firstLineEnd   = document.getLineEndOffset(firstLine)
            val lastLineEnd    = document.getLineEndOffset(lastLine)

            val firstLineText = text.subSequence(firstLineStart, firstLineEnd).toString()
            val pipeOffsets = mutableListOf<Int>()
            var idx = 0
            while (idx < firstLineText.length) {
                val p = firstLineText.indexOf('|', idx)
                if (p < 0) break
                pipeOffsets += firstLineStart + p
                idx = p + 1
            }
            if (pipeOffsets.size < 2) return

            val headerLine      = (firstLine..lastLine).firstOrNull { it in headerRowLines }
            val headerLineStart = headerLine?.let { document.getLineStartOffset(it) }
            val geometry        = TableGeometry(firstLine, lastLine, pipeOffsets, headerLine)
            newGeometries += geometry

            val h = markupModel.addRangeHighlighter(
                null, firstLineStart, lastLineEnd,
                HighlighterLayer.SYNTAX - 1,
                HighlighterTargetArea.LINES_IN_RANGE
            )
            h.setCustomRenderer(TableFrameRenderer(editor, geometry, firstLineStart, lastLineEnd, pipeOffsets, headerLineStart, renderState))
            newHighlighters += h

            for (line in firstLine..lastLine) {
                val lineStart = document.getLineStartOffset(line)
                val lineEnd   = document.getLineEndOffset(line)
                if (line in headerRowLines)
                    applyHeaderAttrs(markupModel, lineStart, lineEnd, editor)?.let { newHighlighters += it }
                if (line in headerColumnLines) {
                    val lineText   = text.subSequence(lineStart, lineEnd).toString()
                    val firstPipe  = lineText.indexOf('|')
                    val secondPipe = lineText.indexOf('|', firstPipe + 1)
                    if (firstPipe >= 0 && secondPipe >= 0)
                        applyHeaderAttrs(markupModel, lineStart + firstPipe, lineStart + secondPipe + 1, editor)
                            ?.let { newHighlighters += it }
                }
            }
        }

        for (line in 0 until document.lineCount) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd   = document.getLineEndOffset(line)
            val lineText  = text.subSequence(lineStart, lineEnd).toString().trim()
            if (lineText.startsWith("|")) {
                if (tableFirstLine == null) tableFirstLine = line
            } else {
                tableFirstLine?.let { processTable(it, line - 1) }
                tableFirstLine = null
            }
        }
        tableFirstLine?.let { processTable(it, document.lineCount - 1) }

        editorGeometries[editor] = newGeometries
        editorHighlighters[editor] = newHighlighters
    }

    // ---- Mouse handling ----

    private fun handleMouseMove(editor: Editor, e: EditorMouseEvent) {
        val hover = findHover(editor, e.mouseEvent.point)
        if (hover != editorHover[editor]) {
            editorHover[editor] = hover
            // The hand cursor signals "draggable" — restrict it to zones where a drag
            // gesture makes sense (column on TOP_BORDER, row on LEFT/RIGHT_BORDER).
            // Header separator is click-only (toggle header), so keep the default cursor.
            val draggable = hover?.zone in setOf(
                HoverZone.TOP_BORDER, HoverZone.LEFT_BORDER, HoverZone.RIGHT_BORDER
            )
            editor.contentComponent.cursor =
                if (draggable) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                else Cursor.getDefaultCursor()
            // Hint the user about both gestures available on the frame. TOP_BORDER is only
            // exposed when click is meaningful (sub-tables preceded by a comment never get
            // a TOP_BORDER hover), so we no longer need to branch the message.
            editor.contentComponent.toolTipText = when (hover?.zone) {
                HoverZone.TOP_BORDER ->
                    "Click to open the table menu, or drag to reorder this column"
                HoverZone.LEFT_BORDER, HoverZone.RIGHT_BORDER ->
                    "Click to open the table menu, or drag to reorder this row"
                else -> null
            }
            editor.contentComponent.repaint()
        }
    }

    private fun handleMouseClick(editor: Editor, e: EditorMouseEvent) {
        if (e.mouseEvent.button != MouseEvent.BUTTON1) return
        // Skip the popup if a drag has just completed (mousePressed → mouseDragged → mouseReleased
        // doesn't fire mouseClicked unless the press point and release point coincide; some
        // platforms still fire mouseClicked anyway, so we double-check the drag state).
        if (editorSkipNextClick.remove(editor)) {
            return
        }
        val hover = findHover(editor, e.mouseEvent.point) ?: return
        showTablePopup(editor, hover.geometry, hover.zone, e.mouseEvent)
    }

    // ---- Drag-and-drop: row/column reorder ----

    private fun handleMousePressed(editor: Editor, e: EditorMouseEvent) {
        if (e.mouseEvent.button != MouseEvent.BUTTON1) return
        val hover = findHover(editor, e.mouseEvent.point) ?: return
        when (hover.zone) {
            HoverZone.LEFT_BORDER -> {
                val rowIdx = computeRowIdxAt(editor, hover.geometry, e.mouseEvent.y)
                editorDrag[editor] = DragState(
                    zone = HoverZone.LEFT_BORDER,
                    geom = hover.geometry,
                    sourceIndex = rowIdx,
                    startX = e.mouseEvent.x,
                    startY = e.mouseEvent.y
                )
                // Note: we deliberately do not consume the press event here. Consuming it
                // (even just the IntelliJ EditorMouseEvent) suppresses the trailing
                // mouseClicked notification — which we need to open the table popup on a
                // pure click. The editor's selection that may start on press is cleared
                // continuously in handleMouseDragged() once the drag becomes active.
            }
            HoverZone.TOP_BORDER -> {
                val colIdx = computeColIdxAt(editor, hover.geometry, e.mouseEvent.x)
                editorDrag[editor] = DragState(
                    zone = HoverZone.TOP_BORDER,
                    geom = hover.geometry,
                    sourceIndex = colIdx,
                    startX = e.mouseEvent.x,
                    startY = e.mouseEvent.y
                )
                // Note: we deliberately do not consume the press event here. Consuming it
                // (even just the IntelliJ EditorMouseEvent) suppresses the trailing
                // mouseClicked notification — which we need to open the table popup on a
                // pure click. The editor's selection that may start on press is cleared
                // continuously in handleMouseDragged() once the drag becomes active.
            }
            else -> editorDrag.remove(editor)
        }
    }

    private fun handleMouseDragged(editor: Editor, e: EditorMouseEvent) {
        val state = editorDrag[editor] ?: return
        state.currentX = e.mouseEvent.x
        state.currentY = e.mouseEvent.y
        val dx = Math.abs(state.currentX - state.startX)
        val dy = Math.abs(state.currentY - state.startY)
        if (!state.active && (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD)) {
            state.active = true
            editor.contentComponent.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        if (state.active) {
            // The editor's selection handler extends a selection on drag — clear it on
            // every tick so the user only sees our drag visuals.
            if (editor.selectionModel.hasSelection()) editor.selectionModel.removeSelection()
            // Repaint the dragged table's frame area so the renderer overlays follow the cursor.
            editor.contentComponent.repaint()
        }
    }

    private fun handleMouseReleased(editor: Editor, e: EditorMouseEvent) {
        val state = editorDrag[editor] ?: return
        if (!state.active) {
            // Pure click → leave the state in place; handleMouseClick will pop up the menu.
            return
        }
        editor.contentComponent.cursor = Cursor.getDefaultCursor()
        when (state.zone) {
            HoverZone.LEFT_BORDER -> {
                val targetRowIdx = computeRowIdxAt(editor, state.geom, e.mouseEvent.y)
                if (targetRowIdx != state.sourceIndex) {
                    applyDragOp(editor, state.geom, TableEditOps.Op.MoveRow(state.sourceIndex, targetRowIdx))
                }
            }
            HoverZone.TOP_BORDER -> {
                val targetColIdx = computeColIdxAt(editor, state.geom, e.mouseEvent.x)
                // No-op drops:
                //   - target == source           (drop on itself)
                //   - target == source + 1       (insert BEFORE the right neighbor → same place)
                if (!isNoopColumnDrop(state.sourceIndex, targetColIdx)) {
                    applyDragOp(editor, state.geom, TableEditOps.Op.MoveColumn(state.sourceIndex, targetColIdx))
                    // Flash the dropped column's top segment for 1s so the user sees where it landed.
                    // Removing source[i] before inserting before target[j] shifts the landing
                    // index left by 1 when i < j (matches TableEditOps.computeEffectiveTo).
                    val landedIdx = if (state.sourceIndex < targetColIdx) targetColIdx - 1 else targetColIdx
                    triggerPostDropFlash(editor, state.geom, landedIdx)
                }
            }
            else -> Unit
        }
        // Clear the drag state immediately so the renderer stops drawing source/target/cursor
        // overlays on subsequent repaints. The PostDropFlash now drives the landing highlight.
        editorDrag.remove(editor)
        // Swallow the trailing mouseClicked event (some platforms fire it after a drag).
        editorSkipNextClick += editor
        editor.contentComponent.repaint()
    }

    /**
     * Column drop is a no-op when:
     *   - target == source           (drop on itself)
     *   - target == source + 1       (insert BEFORE the right neighbor → same effective position)
     */
    private fun isNoopColumnDrop(source: Int, target: Int): Boolean =
        target == source || target == source + 1

    private fun applyDragOp(editor: Editor, geom: TableGeometry, op: TableEditOps.Op) {
        val doc = editor.document
        val tableLines = (geom.firstLine..geom.lastLine).filter { line ->
            doc.charsSequence
                .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
                .toString().trim().startsWith("|")
        }
        if (tableLines.isEmpty()) return
        TableEditOps.apply(editor, tableLines, op)
    }

    /** Map a Y pixel to a row index within [geom]'s pipe rows. */
    private fun computeRowIdxAt(editor: Editor, geom: TableGeometry, y: Int): Int {
        val doc = editor.document
        val tableLines = (geom.firstLine..geom.lastLine).filter { line ->
            doc.charsSequence
                .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
                .toString().trim().startsWith("|")
        }
        if (tableLines.isEmpty()) return 0
        return tableLines.indices.minBy { i ->
            val lineY = editor.logicalPositionToXY(
                editor.offsetToLogicalPosition(doc.getLineStartOffset(tableLines[i]))
            ).y
            Math.abs(y - (lineY + editor.lineHeight / 2))
        }
    }

    /**
     * True when [firstLine] is the second (or later) half of a logical Gherkin table that
     * was split by an interleaved comment / blank line. In that case the top edge must
     * stay inert: the visual top border of the sub-table sits under a `# comment` line
     * and any drag / menu affordance there would clash with the comment text.
     *
     * Detected by [TableEditOps.collectTableLinesAround]: if the logical table reaches
     * further up than [firstLine], we're in a continuation. A comment placed ABOVE a
     * standalone table (documentation comment) reads as a single contiguous logical
     * table starting at [firstLine] — top border stays active.
     */
    private fun isContinuationOfLargerTable(editor: Editor, firstLine: Int): Boolean {
        val tableLines = TableEditOps.collectTableLinesAround(editor, firstLine)
        return tableLines.isNotEmpty() && tableLines.first() < firstLine
    }

    /**
     * True when the editor line under pixel [y] (within [geom]'s span) is a real table row
     * (starts with `|`) — i.e. NOT an interleaved comment / blank line. Used to suppress
     * the hand cursor over comment lines that may live between table rows.
     */
    private fun isPipeLineAtY(editor: Editor, geom: TableGeometry, y: Int): Boolean {
        val doc = editor.document
        val logicalLine = editor.xyToLogicalPosition(Point(0, y)).line
        if (logicalLine !in geom.firstLine..geom.lastLine) return false
        val start = doc.getLineStartOffset(logicalLine)
        val end   = doc.getLineEndOffset(logicalLine)
        return doc.charsSequence.subSequence(start, end).toString().trim().startsWith("|")
    }

    /** Map an X pixel to a column index inside [geom]. */
    private fun computeColIdxAt(editor: Editor, geom: TableGeometry, x: Int): Int {
        if (geom.pipeOffsets.size < 2) return 0
        val pipeXs = geom.pipeOffsets.map {
            editor.logicalPositionToXY(editor.offsetToLogicalPosition(it)).x
        }
        // Each cell is between pipe[i] and pipe[i+1]. Find the first cell whose mid-X is closest to x.
        return pipeXs.zipWithNext().indices.minBy { i ->
            val mid = (pipeXs[i] + pipeXs[i + 1]) / 2
            Math.abs(x - mid)
        }
    }

    private fun postFlash(editor: Editor, flash: PostDropFlash) {
        editorPostDropFlash[editor] = flash
        val timer = javax.swing.Timer(1000) {
            editorPostDropFlash.remove(editor)
            if (!editor.isDisposed) editor.contentComponent.repaint()
        }
        timer.isRepeats = false
        timer.start()
        editor.contentComponent.repaint()
    }

    private fun triggerPostDropFlash(editor: Editor, geom: TableGeometry, columnIndex: Int) =
        postFlash(editor, PostDropFlash(geom.firstLine, columnIndex = columnIndex))

    private fun findHover(editor: Editor, point: Point): HoverState? {
        val geometries = editorGeometries[editor] ?: return null
        // Tolerances tuned per zone:
        //  - TOP    : generous upward — but contained within ~12px so the activation zone
        //             never bleeds onto a comment / blank line preceding the table. Those
        //             lines must keep their normal editor behaviour (no hand cursor,
        //             no tooltip, no popup, no drop target).
        //  - EDGE   : a touch larger than before to give the left/right borders some room.
        val tTopUp     = 12
        val tTopDown   = 4
        val tEdge      = 8
        val tRange     = tEdge  // used for the inXRange / inYRange bounding box
        val fm   = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
        val half = fm.charWidth('|') / 2

        for (geom in geometries) {
            if (geom.pipeOffsets.size < 2) continue
            val doc  = editor.document
            val firstX = editor.logicalPositionToXY(editor.offsetToLogicalPosition(geom.pipeOffsets.first())).x + half
            val lastX  = editor.logicalPositionToXY(editor.offsetToLogicalPosition(geom.pipeOffsets.last())).x + half
            val topY   = editor.logicalPositionToXY(editor.offsetToLogicalPosition(doc.getLineStartOffset(geom.firstLine))).y
            val bottomY = editor.logicalPositionToXY(editor.offsetToLogicalPosition(doc.getLineStartOffset(geom.lastLine))).y + editor.lineHeight
            val mx = point.x; val my = point.y
            val inXRange = mx >= firstX - tRange && mx <= lastX + tRange
            val inYRange = my >= topY - tRange && my <= bottomY + tRange

            // Outer frame borders.
            // - BOTTOM_BORDER is intentionally not exposed as a hover zone: the top border is
            //   the single entry point for column actions; the bottom would just duplicate it.
            // - LEFT/RIGHT only activate when the cursor sits on a real pipe-row line — never
            //   on an interleaved comment / blank line that may appear between rows.
            // - TOP_BORDER is suppressed when this geometry is the continuation of a logical
            //   Gherkin table split by an interleaved comment / blank line — never on a
            //   standalone table that merely has a doc-comment above it.
            if (inXRange && my in topY - tTopUp .. topY + tTopDown &&
                !isContinuationOfLargerTable(editor, geom.firstLine)) {
                return HoverState(geom, HoverZone.TOP_BORDER)
            }
            if (inYRange && (mx in firstX - tEdge .. firstX + tEdge || mx in lastX - tEdge .. lastX + tEdge)) {
                if (isPipeLineAtY(editor, geom, my)) {
                    return HoverState(
                        geom,
                        if (mx in firstX - tEdge .. firstX + tEdge) HoverZone.LEFT_BORDER else HoverZone.RIGHT_BORDER
                    )
                }
            }

            // Header separator (thin line under the first row) is intentionally NOT a hover
            // zone: it must remain a plain editor area — no hand cursor, no tooltip, no
            // popup, no thicker stroke on hover.
        }
        return null
    }

    // ---- Popup ----

    private fun showTablePopup(editor: Editor, geometry: TableGeometry, zone: HoverZone, mouseEvent: MouseEvent) {
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
        moveCaretToTableCell(editor, doc, tableLines, pipeXs,
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
        popup.show(RelativePoint(mouseEvent))
    }

    // Moves the caret into a specific cell (rowIdx, colIdx) of the table
    // so that Shift actions pass their "caret is in a cell" enabled check.
    private fun moveCaretToTableCell(
        editor: Editor, doc: com.intellij.openapi.editor.Document,
        tableLines: List<Int>, pipeXs: List<Int>, cell: Pair<Int, Int>
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

    // ---- @header document actions ----

    private inner class SetHeaderAction(
        private val editor: Editor,
        private val geometry: TableGeometry,
        private val headerType: String,  // "row" or "column"
        text: String,
        private val closePopup: () -> Unit
    ) : ToggleAction(text) {

        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean {
            val current = tableAnnotation(editor.document, editor.document.charsSequence, geometry.firstLine)
            return current == headerType
        }

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

    // Returns the line index of the existing "# @header: ..." comment above the table, or -1.
    private fun findHeaderCommentLine(doc: com.intellij.openapi.editor.Document, tableLine: Int): Int {
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

    // ---- Add / delete row & column ----

    private inner class TableEditAction(
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
        }
    }

    // ---- Header cell coloring ----

    private fun applyHeaderAttrs(
        markupModel: com.intellij.openapi.editor.markup.MarkupModel,
        start: Int, end: Int, editor: Editor
    ): RangeHighlighter? {
        val src = editor.colorsScheme.getAttributes(GherkinHighlighter.TABLE_HEADER_CELL) ?: return null
        val attrs = TextAttributes().apply {
            backgroundColor = src.backgroundColor
            foregroundColor = src.foregroundColor
            fontType        = src.fontType
        }
        return markupModel.addRangeHighlighter(start, end, HighlighterLayer.SYNTAX + 1, attrs, HighlighterTargetArea.EXACT_RANGE)
    }

    // ---- Header line detection (PSI) ----

    private fun findHeaderRowLines(editor: Editor): Set<Int> {
        val project = editor.project ?: return emptySet()
        val document = editor.document
        val text = document.charsSequence
        val result = mutableSetOf<Int>()
        ApplicationManager.getApplication().runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            if (psiFile !is GherkinFile) return@runReadAction
            PsiTreeUtil.findChildrenOfType(psiFile, GherkinExamplesBlock::class.java).forEach { examples ->
                val headerRow = examples.table?.headerRow ?: return@forEach
                result += document.getLineNumber(headerRow.textRange.startOffset)
            }
            PsiTreeUtil.findChildrenOfType(psiFile, GherkinTable::class.java).forEach { table ->
                if (PsiTreeUtil.getParentOfType(table, GherkinExamplesBlock::class.java) != null) return@forEach
                val firstRow = table.headerRow ?: return@forEach
                val firstRowLine = document.getLineNumber(firstRow.textRange.startOffset)
                if (tableAnnotation(document, text, firstRowLine) == "row") result += firstRowLine
            }
        }
        return result
    }

    private fun findHeaderColumnLines(editor: Editor): Set<Int> {
        val project = editor.project ?: return emptySet()
        val document = editor.document
        val text = document.charsSequence
        val result = mutableSetOf<Int>()
        ApplicationManager.getApplication().runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            if (psiFile !is GherkinFile) return@runReadAction
            PsiTreeUtil.findChildrenOfType(psiFile, GherkinTable::class.java).forEach { table ->
                if (PsiTreeUtil.getParentOfType(table, GherkinExamplesBlock::class.java) != null) return@forEach
                val firstRow = table.headerRow ?: return@forEach
                val firstRowLine = document.getLineNumber(firstRow.textRange.startOffset)
                if (tableAnnotation(document, text, firstRowLine) == "column") {
                    result += firstRowLine
                    table.dataRows.forEach { row -> result += document.getLineNumber(row.textRange.startOffset) }
                }
            }
        }
        return result
    }

    private fun tableAnnotation(doc: com.intellij.openapi.editor.Document, text: CharSequence, tableLine: Int): String? {
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

    // ---- Frame renderer state accessor (consumed by TableFrameRenderer) ----

    /** Implementation of TableRenderState — exposes the mutable per-editor maps as a
     *  narrow read-only view so the renderer (in TzTableFrameRenderer.kt) stays decoupled. */
    private val renderState = object : TableRenderState {
        override fun hover(editor: Editor) = editorHover[editor]
        override fun drag(editor: Editor)  = editorDrag[editor]
        override fun flash(editor: Editor) = editorPostDropFlash[editor]
        override fun isNoopColumnDrop(source: Int, target: Int) =
            this@TzTableDecorator.isNoopColumnDrop(source, target)
    }

}
