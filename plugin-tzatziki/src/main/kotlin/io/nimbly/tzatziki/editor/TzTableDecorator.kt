package io.nimbly.tzatziki.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
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
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinHighlighter
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D

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

    companion object {
        private var instance: TzTableDecorator? = null
        fun refreshAll() {
            val dec = instance ?: return
            dec.editorHighlighters.keys.toList().forEach { dec.scheduleRefresh(it) }
        }
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
            h.setCustomRenderer(TableFrameRenderer(editor, geometry, firstLineStart, lastLineEnd, pipeOffsets, headerLineStart))
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
            editor.contentComponent.cursor =
                if (hover != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                else Cursor.getDefaultCursor()
            editor.contentComponent.repaint()
        }
    }

    private fun handleMouseClick(editor: Editor, e: EditorMouseEvent) {
        if (e.mouseEvent.button != MouseEvent.BUTTON1) return
        val hover = findHover(editor, e.mouseEvent.point) ?: return
        showTablePopup(editor, hover.geometry, hover.zone, e.mouseEvent)
    }

    private fun findHover(editor: Editor, point: Point): HoverState? {
        val geometries = editorGeometries[editor] ?: return null
        val T = 5  // pixel tolerance
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
            val inXRange = mx >= firstX - T && mx <= lastX + T
            val inYRange = my >= topY - T && my <= bottomY + T

            // Outer frame borders
            if (inXRange && my in topY - T .. topY + T)    return HoverState(geom, HoverZone.TOP_BORDER)
            if (inXRange && my in bottomY - T .. bottomY + T) return HoverState(geom, HoverZone.BOTTOM_BORDER)
            if (inYRange && mx in firstX - T .. firstX + T)  return HoverState(geom, HoverZone.LEFT_BORDER)
            if (inYRange && mx in lastX - T .. lastX + T)    return HoverState(geom, HoverZone.RIGHT_BORDER)

            // Header separator
            geom.headerLine?.let { hl ->
                val hy = editor.logicalPositionToXY(
                    editor.offsetToLogicalPosition(doc.getLineStartOffset(hl))).y + editor.lineHeight - 1
                if (inXRange && my in hy - T .. hy + T)
                    return HoverState(geom, HoverZone.HEADER_SEPARATOR)
            }
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

        // 2. Header type — row / column (click selected = toggle off; closes the popup)
        val popupRef = arrayOfNulls<JBPopup>(1)
        group.add(SetHeaderAction(editor, geometry, "row",    "Header: row")    { popupRef[0]?.cancel() })
        group.add(SetHeaderAction(editor, geometry, "column", "Header: column") { popupRef[0]?.cancel() })
        group.addSeparator()

        // 3. Toggle Cucumber+ — wrapped to close the popup after toggling
        group.add(object : ToggleAction("Toggle Cucumber+") {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun isSelected(e: AnActionEvent) = TOGGLE_CUCUMBER_PL
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                am.getAction("io.nimbly.tzatziki.ToggleTzatziki")?.actionPerformed(e)
                popupRef[0]?.cancel()
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

    // ---- Data classes ----

    data class TableGeometry(
        val firstLine: Int,
        val lastLine: Int,
        val pipeOffsets: List<Int>,
        val headerLine: Int?
    )

    enum class HoverZone { LEFT_BORDER, RIGHT_BORDER, TOP_BORDER, BOTTOM_BORDER, HEADER_SEPARATOR }

    data class HoverState(val geometry: TableGeometry, val zone: HoverZone)

    // ---- Frame renderer (hover-aware) ----

    private inner class TableFrameRenderer(
        private val editor: Editor,
        private val geometry: TableGeometry,
        private val tableStart: Int,
        private val tableEnd: Int,
        private val pipeOffsets: List<Int>,
        private val headerLineStart: Int?
    ) : CustomHighlighterRenderer {

        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            if (pipeOffsets.size < 2) return

            val hover       = editorHover[editor]
            val isHovered   = hover?.geometry == geometry
            val hoverZone   = if (isHovered) hover?.zone else null

            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val fm        = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
            val pipeWidth = fm.charWidth('|')
            val half      = pipeWidth / 2
            val base      = pipeColor(editor)
            val thinColor = Color(base.red, base.green, base.blue, (base.alpha * 0.55).toInt().coerceAtLeast(40))
            val bg        = editor.colorsScheme.defaultBackground
            val borderColor = base.lightenTowards(bg, 0.35f)

            val pipeXs  = pipeOffsets.map { editor.logicalPositionToXY(editor.offsetToLogicalPosition(it)).x + half }
            val firstX  = pipeXs.first()
            val lastX   = pipeXs.last()
            val topY    = editor.logicalPositionToXY(editor.offsetToLogicalPosition(tableStart)).y
            val bottomY = editor.logicalPositionToXY(editor.offsetToLogicalPosition(tableEnd)).y + editor.lineHeight
            val arc = 5f

            // 1. Mask pipe characters
            g2.color = bg
            for (x in pipeXs) g2.fillRect(x - half, topY, pipeWidth, bottomY - topY)

            // 2. Inner vertical separators
            g2.stroke = BasicStroke(0.8f)
            g2.color  = thinColor
            for (x in pipeXs.drop(1).dropLast(1)) g2.drawLine(x, topY, x, bottomY)

            // 3. Header separator — thicker on hover
            if (headerLineStart != null) {
                val hy = editor.logicalPositionToXY(editor.offsetToLogicalPosition(headerLineStart)).y + editor.lineHeight - 1
                g2.stroke = if (hoverZone == HoverZone.HEADER_SEPARATOR) BasicStroke(2.0f) else BasicStroke(0.8f)
                g2.color  = if (hoverZone == HoverZone.HEADER_SEPARATOR) borderColor else thinColor
                g2.drawLine(firstX, hy, lastX, hy)
            }

            // 4. Outer rounded rectangle (thin always)
            g2.stroke = BasicStroke(1.0f)
            g2.color  = borderColor
            g2.draw(RoundRectangle2D.Float(
                firstX.toFloat(), topY.toFloat(),
                (lastX - firstX).toFloat(), (bottomY - topY).toFloat(),
                arc, arc
            ))

            // 5. Hovered segment overlay (thick, on top of the thin rect)
            val halfArc = (arc / 2).toInt()
            when (hoverZone) {
                HoverZone.TOP_BORDER    -> {
                    g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.drawLine(firstX + halfArc, topY, lastX - halfArc, topY)
                }
                HoverZone.BOTTOM_BORDER -> {
                    g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.drawLine(firstX + halfArc, bottomY, lastX - halfArc, bottomY)
                }
                HoverZone.LEFT_BORDER   -> {
                    g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.drawLine(firstX, topY + halfArc, firstX, bottomY - halfArc)
                }
                HoverZone.RIGHT_BORDER  -> {
                    g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.drawLine(lastX, topY + halfArc, lastX, bottomY - halfArc)
                }
                else -> {}
            }
        }

        private fun pipeColor(editor: Editor): Color =
            editor.colorsScheme.getAttributes(GherkinHighlighter.PIPE)?.foregroundColor
                ?: editor.colorsScheme.defaultForeground

        private fun Color.lightenTowards(target: Color, factor: Float) = Color(
            (red   + (target.red   - red)   * factor).toInt().coerceIn(0, 255),
            (green + (target.green - green) * factor).toInt().coerceIn(0, 255),
            (blue  + (target.blue  - blue)  * factor).toInt().coerceIn(0, 255),
            alpha
        )
    }
}
