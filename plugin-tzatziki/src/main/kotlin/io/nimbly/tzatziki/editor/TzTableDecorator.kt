package io.nimbly.tzatziki.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
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

/** FQN + method of the internal `com.intellij.idea.AppMode` used (reflectively) to detect a
 *  JetBrains Remote Development backend. Kept as constants so ReflectionApiTest can guard them. */
const val APP_MODE_CLASS = "com.intellij.idea.AppMode"
const val APP_MODE_REMOTE_DEV_HOST_METHOD = "isRemoteDevHost"

/**
 * True on a JetBrains Remote Development backend (the headless host whose editor is projected to a
 * thin client). Such a host relays neither arbitrary Graphics painting (our [TableFrameRenderer])
 * nor mouse motion/drag events to the client — only serialized markup and discrete clicks. So on a
 * Remote Dev host we skip the painted frame and the hover/drag gestures (header text-attribute
 * highlighting still works, as does the table menu on click).
 *
 * Detected reflectively on purpose: a compile-time reference to the `@ApiStatus.Internal` AppMode
 * would make `verifyPlugin` report INTERNAL_API_USAGES and block publishing. Falls back to false.
 */
internal val IS_REMOTE_DEV_HOST: Boolean by lazy {
    runCatching {
        Class.forName(APP_MODE_CLASS).getMethod(APP_MODE_REMOTE_DEV_HOST_METHOD).invoke(null) as Boolean
    }.getOrDefault(false)
}

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
    /** Indicator painted while the table popup menu is open. Cleared on popup close. */
    private val editorMenuTarget    = mutableMapOf<Editor, MenuTargetIndicator?>()
    /** One-shot flag: when set, the next mouseClicked is swallowed (avoids the trailing popup
     *  after a drag-release on the same point that fired the press). */
    private val editorSkipNextClick = mutableSetOf<Editor>()
    /** Caret offset captured before a drag gesture — restored on release so the user never
     *  sees the caret jump to the press position (which would be visually distracting,
     *  especially right at the pipe boundary of the frame click). */
    private val editorPreDragCaret  = mutableMapOf<Editor, Int>()

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
        /** Show the persistent menu-target indicator on a column (dashed orange on top border). */
        fun showMenuTargetColumn(editor: Editor, tableFirstLine: Int, columnIndex: Int) {
            val dec = instance ?: return
            dec.editorMenuTarget[editor] = MenuTargetIndicator(tableFirstLine, columnIndex = columnIndex)
            editor.contentComponent.repaint()
        }
        /** Show the persistent menu-target indicator on a row (solid orange on left border). */
        fun showMenuTargetRow(editor: Editor, tableFirstLine: Int, rowLine: Int) {
            val dec = instance ?: return
            dec.editorMenuTarget[editor] = MenuTargetIndicator(tableFirstLine, rowLine = rowLine)
            editor.contentComponent.repaint()
        }
        /** Clear the menu-target indicator (call on popup close). */
        fun clearMenuTarget(editor: Editor) {
            val dec = instance ?: return
            if (dec.editorMenuTarget.remove(editor) != null) {
                editor.contentComponent.repaint()
            }
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
        editorMenuTarget.remove(editor)
        editorSkipNextClick.remove(editor)
        editorPreDragCaret.remove(editor)
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
        if (!TOGGLE_CUCUMBER_PL || !io.nimbly.tzatziki.config.TzSettings.getInstance().isTableFrameEnabled()) {
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

            // The painted frame is a CustomHighlighterRenderer (arbitrary Graphics) — not relayed to
            // a Remote Dev thin client, so skip it there. Header highlighting below still applies.
            if (!IS_REMOTE_DEV_HOST) {
                val h = markupModel.addRangeHighlighter(
                    null, firstLineStart, lastLineEnd,
                    HighlighterLayer.SYNTAX - 1,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                h.setCustomRenderer(TableFrameRenderer(editor, geometry, firstLineStart, lastLineEnd, pipeOffsets, headerLineStart, renderState))
                newHighlighters += h
            }

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
        if (IS_REMOTE_DEV_HOST) return   // no painted frame / hand cursor on a Remote Dev backend
        var hover = findHover(editor, e.mouseEvent.point)
        // Drag-and-drop disabled → don't change the look of the draggable frame bars on hover (no
        // highlight, no hand cursor). The frame still opens the table menu on click (fresh findHover).
        if (hover != null &&
            hover.zone in setOf(HoverZone.TOP_BORDER, HoverZone.LEFT_BORDER, HoverZone.RIGHT_BORDER) &&
            !io.nimbly.tzatziki.config.TzSettings.getInstance().isDragAndDropEnabled()
        ) {
            hover = null
        }
        if (hover != editorHover[editor]) {
            val wasDraggable = editorHover[editor]?.zone in setOf(
                HoverZone.TOP_BORDER, HoverZone.LEFT_BORDER, HoverZone.RIGHT_BORDER
            )
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
            // Hide the caret PROACTIVELY as soon as the cursor enters a draggable border —
            // this beats IntelliJ's mouse-press handler that would otherwise paint the caret
            // at the press position before our handleMousePressed had a chance to hide it.
            // Restored when the cursor leaves the draggable zone.
            if (draggable != wasDraggable) {
                (editor as? EditorEx)?.setCaretVisible(!draggable)
            }
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
        if (IS_REMOTE_DEV_HOST) return   // drag gestures aren't relayed to a Remote Dev thin client
        if (e.mouseEvent.button != MouseEvent.BUTTON1) return
        if (!io.nimbly.tzatziki.config.TzSettings.getInstance().isDragAndDropEnabled()) return   // Settings → Tools → Cucumber+ (click-to-menu still works)
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
                onDragGestureStart(editor)
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
                onDragGestureStart(editor)
            }
            else -> editorDrag.remove(editor)
        }
    }

    /**
     * Called as soon as the user presses on a draggable border. We
     *  - hide the caret right away (it would otherwise jump to wherever the press
     *    landed inside the cell next to the pipe — visually distracting),
     *  - force the hand cursor (the hover-based update via mouseMoved can be stale just
     *    after a previous drop, especially the moment scheduleRefresh has rebuilt the
     *    geometries but no mouseMoved has fired yet to re-evaluate findHover).
     *
     * Note: we deliberately do not consume the press event. Consuming the
     * EditorMouseEvent suppresses the trailing mouseClicked notification, which we still
     * need so a pure click on the frame opens the table popup.
     */
    private fun onDragGestureStart(editor: Editor) {
        // Remember where the caret was BEFORE the editor moves it on the press, so we can
        // restore the pre-gesture position on release. The user therefore never sees the
        // caret pin itself just after the pipe.
        val pre = editor.caretModel.offset
        editorPreDragCaret[editor] = pre
        (editor as? EditorEx)?.setCaretVisible(false)
        editor.contentComponent.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        // The editor's default mouse-press handler runs AFTER our listener and moves the
        // caret to the press position — even when the caret renders invisibly, the
        // selection model still drifts. Schedule a restore for the very next EDT tick so
        // the caret position never visibly drags toward the pipe.
        ApplicationManager.getApplication().invokeLater {
            if (!editor.isDisposed && editorPreDragCaret[editor] == pre) {
                editor.caretModel.moveToOffset(pre.coerceIn(0, editor.document.textLength))
            }
        }
    }

    private fun restoreCaretAfterGesture(editor: Editor) {
        editorPreDragCaret.remove(editor)?.let { offset ->
            editor.caretModel.moveToOffset(offset.coerceIn(0, editor.document.textLength))
        }
        (editor as? EditorEx)?.setCaretVisible(true)
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
            // Block the editor's selection handler from extending the selection on this
            // drag tick — consuming the EditorMouseEvent prevents downstream listeners
            // (including IntelliJ's text-selection handler) from running.
            e.consume()
            // Belt-and-braces: clear any stray selection that may have leaked through
            // (e.g. extended during the threshold movement before we marked active).
            if (editor.selectionModel.hasSelection()) editor.selectionModel.removeSelection()
            // Repaint the dragged table's frame area so the renderer overlays follow the cursor.
            editor.contentComponent.repaint()
        }
    }

    private fun handleMouseReleased(editor: Editor, e: EditorMouseEvent) {
        val state = editorDrag[editor] ?: return
        // Restore the caret to its pre-drag position regardless of pure-click vs drag.
        restoreCaretAfterGesture(editor)
        if (!state.active) {
            // Pure click → leave the state in place; handleMouseClick will pop up the menu.
            return
        }
        editor.contentComponent.cursor = Cursor.getDefaultCursor()
        when (state.zone) {
            HoverZone.LEFT_BORDER -> {
                val targetRowIdx = computeRowIdxAt(editor, state.geom, e.mouseEvent.y)
                // Same no-op semantics as columns: drop on self or next neighbour collapses
                // back (effectiveTo == source). Skip the write + skip the flash.
                if (!isNoopRowDrop(state.sourceIndex, targetRowIdx)) {
                    applyDragOp(editor, state.geom, TableEditOps.Op.MoveRow(state.sourceIndex, targetRowIdx))
                    // Flash the dropped row's left segment for 1s so the user sees where it landed.
                    val landedIdx = if (state.sourceIndex < targetRowIdx) targetRowIdx - 1 else targetRowIdx
                    val pipeLines = pipeRowLines(editor, state.geom)
                    pipeLines.getOrNull(landedIdx)?.let { rowLine ->
                        triggerPostRowFlash(editor, state.geom, rowLine)
                    }
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
        // Forget the previous hover so the next mouseMoved tick re-evaluates findHover and
        // restores the hand cursor.
        editorHover.remove(editor)
        // Re-evaluate hover at the release point so the user immediately sees the hand
        // cursor again if they're still over a draggable border — without this, we'd
        // have to wait for the next mouseMoved event before the cursor updates.
        handleMouseMove(editor, e)
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

    /** Row drop is a no-op for the exact same reason — drop on self or on the next row. */
    private fun isNoopRowDrop(source: Int, target: Int): Boolean =
        target == source || target == source + 1

    /** List of physical document line numbers that contain a pipe row inside [geom]. */
    private fun pipeRowLines(editor: Editor, geom: TableGeometry): List<Int> {
        val doc = editor.document
        return (geom.firstLine..geom.lastLine).filter { line ->
            doc.charsSequence
                .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
                .toString().trim().startsWith("|")
        }
    }

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

    private fun triggerPostRowFlash(editor: Editor, geom: TableGeometry, rowLine: Int) =
        postFlash(editor, PostDropFlash(geom.firstLine, rowLine = rowLine))

    /** Thin adapter: pull the list of geometries for [editor] and delegate to the
     *  stateless [io.nimbly.tzatziki.editor.findHover] in TzTableHover.kt. */
    private fun findHover(editor: Editor, point: Point): HoverState? {
        val geoms = editorGeometries[editor] ?: return null
        return findHover(editor, geoms, point)
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
        val result = mutableSetOf<Int>()
        // Examples blocks: PSI is dialect-aware and gives the canonical header row.
        ApplicationManager.getApplication().runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            if (psiFile is GherkinFile) {
                PsiTreeUtil.findChildrenOfType(psiFile, GherkinExamplesBlock::class.java).forEach { examples ->
                    val headerRow = examples.table?.headerRow ?: return@forEach
                    result += document.getLineNumber(headerRow.textRange.startOffset)
                }
            }
        }
        // `# @header: row` annotated tables — text scan, dialect-agnostic. The PSI route
        // used to fall through here, but in localised feature files (zh, fr…) the
        // cucumber-plugin can fail to attach a `GherkinTable.headerRow` to a step's data
        // table, so we'd silently drop the annotation. Scanning the document side-steps
        // that entirely.
        result += findAnnotatedHeaderLines(document, kind = "row")
        return result
    }

    private fun findHeaderColumnLines(editor: Editor): Set<Int> {
        val document = editor.project?.let { editor.document } ?: return emptySet()
        // For column mode we highlight every row of the annotated table — first cell is
        // the "header cell" — so we collect all consecutive pipe lines following the
        // annotation, not just the first one.
        return findAnnotatedHeaderLines(document, kind = "column", allRows = true)
    }

    /**
     * Locate every `# @header: <kind>` comment in [document] and return the line numbers of
     * the pipe rows it governs. When [allRows] is true (column mode) every consecutive
     * pipe row beneath the annotation is returned; otherwise just the first one (row mode).
     */
    private fun findAnnotatedHeaderLines(document: Document, kind: String, allRows: Boolean = false): Set<Int> {
        val chars = document.charsSequence
        val result = mutableSetOf<Int>()
        val annotation = Regex("^\\s*#\\s*@header:\\s*$kind\\s*$")
        val lineCount = document.lineCount
        for (i in 0 until lineCount) {
            val text = chars.subSequence(document.getLineStartOffset(i), document.getLineEndOffset(i)).toString()
            if (!annotation.matches(text)) continue
            // Find the first pipe row strictly below the annotation, skipping blanks and
            // chained comments. Anything else aborts (the annotation is orphaned).
            var j = i + 1
            while (j < lineCount) {
                val t = chars.subSequence(document.getLineStartOffset(j), document.getLineEndOffset(j)).toString().trimStart()
                when {
                    t.isBlank()        -> { j++; continue }
                    t.startsWith("#")  -> { j++; continue }
                    t.startsWith("|")  -> {
                        result += j
                        if (!allRows) break
                        // Collect remaining rows of the logical table — Gherkin treats it
                        // as one table even when comments / blank lines are interleaved
                        // (mirror of the hover/menu behaviour in TzTableHover). Stop only
                        // when we hit a real non-table line.
                        var k = j + 1
                        while (k < lineCount) {
                            val tk = chars.subSequence(document.getLineStartOffset(k), document.getLineEndOffset(k)).toString().trimStart()
                            when {
                                tk.startsWith("|") -> { result += k; k++ }
                                tk.isBlank() || tk.startsWith("#") -> k++
                                else -> break
                            }
                        }
                        break
                    }
                    else -> break
                }
            }
        }
        return result
    }


    // ---- Frame renderer state accessor (consumed by TableFrameRenderer) ----

    /** Implementation of TableRenderState — exposes the mutable per-editor maps as a
     *  narrow read-only view so the renderer (in TzTableFrameRenderer.kt) stays decoupled. */
    private val renderState = object : TableRenderState {
        override fun hover(editor: Editor) = editorHover[editor]
        override fun drag(editor: Editor)  = editorDrag[editor]
        override fun flash(editor: Editor) = editorPostDropFlash[editor]
        override fun menuTarget(editor: Editor) = editorMenuTarget[editor]
        override fun isNoopColumnDrop(source: Int, target: Int) =
            this@TzTableDecorator.isNoopColumnDrop(source, target)
        override fun isNoopRowDrop(source: Int, target: Int) =
            this@TzTableDecorator.isNoopRowDrop(source, target)
    }

}
