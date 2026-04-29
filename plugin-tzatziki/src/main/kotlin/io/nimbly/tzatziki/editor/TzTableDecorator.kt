package io.nimbly.tzatziki.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinHighlighter
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D

/**
 * Draws a rounded-corner grid around Gherkin tables.
 *
 * Header detection:
 *   - Examples: blocks  → always a header (PSI)
 *   - DataTables        → add "# @header: row" or "# @header: column" above the table
 */
class TzTableDecorator : EditorFactoryListener {

    private val editorHighlighters = mutableMapOf<Editor, MutableList<RangeHighlighter>>()
    private val editorDisposables  = mutableMapOf<Editor, com.intellij.openapi.Disposable>()

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
        editorDisposables[editor] = disposable
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        editorDisposables.remove(editor)?.let { Disposer.dispose(it) }
        editorHighlighters.remove(editor)
    }

    private fun scheduleRefresh(editor: Editor) {
        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater
            refresh(editor)
        }
    }

    private fun refresh(editor: Editor) {
        val markupModel = editor.markupModel
        editorHighlighters[editor]?.forEach { markupModel.removeHighlighter(it) }
        editorHighlighters.remove(editor)

        val document = editor.document
        val text = document.charsSequence
        val newHighlighters = mutableListOf<RangeHighlighter>()

        val headerRowLines    = findHeaderRowLines(editor)
        val headerColumnLines = findHeaderColumnLines(editor)

        // Group consecutive table lines into tables, then render each as one frame
        var tableFirstLine: Int? = null

        fun processTable(firstLine: Int, lastLine: Int) {
            val firstLineStart = document.getLineStartOffset(firstLine)
            val firstLineEnd   = document.getLineEndOffset(firstLine)
            val lastLineEnd    = document.getLineEndOffset(lastLine)

            // Collect pipe absolute offsets from the first row
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

            // Header row within this table (for the inner separator line)
            val headerLine      = (firstLine..lastLine).firstOrNull { it in headerRowLines }
            val headerLineStart = headerLine?.let { document.getLineStartOffset(it) }

            // One frame renderer for the whole table
            val h = markupModel.addRangeHighlighter(
                null, firstLineStart, lastLineEnd,
                HighlighterLayer.SYNTAX - 1,
                HighlighterTargetArea.LINES_IN_RANGE
            )
            h.setCustomRenderer(TableFrameRenderer(firstLineStart, lastLineEnd, pipeOffsets, headerLineStart))
            newHighlighters += h

            // Per-line header coloring
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

        editorHighlighters[editor] = newHighlighters
    }

    // ---- Header cell coloring ----

    private fun applyHeaderAttrs(
        markupModel: com.intellij.openapi.editor.markup.MarkupModel,
        start: Int, end: Int,
        editor: Editor
    ): RangeHighlighter? {
        val src = editor.colorsScheme.getAttributes(GherkinHighlighter.TABLE_HEADER_CELL) ?: return null
        val attrs = TextAttributes().apply {
            backgroundColor = src.backgroundColor
            foregroundColor = src.foregroundColor
            fontType        = src.fontType
        }
        return markupModel.addRangeHighlighter(
            start, end,
            HighlighterLayer.SYNTAX + 1,
            attrs,
            HighlighterTargetArea.EXACT_RANGE
        )
    }

    // ---- Header line detection ----

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

    // Scans upward skipping blank lines; returns "row", "column", or null.
    private fun tableAnnotation(doc: com.intellij.openapi.editor.Document, text: CharSequence, tableLine: Int): String? {
        var line = tableLine - 1
        while (line >= 0) {
            val s = doc.getLineStartOffset(line)
            val e = doc.getLineEndOffset(line)
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

    // ---- Table frame renderer ----

    private class TableFrameRenderer(
        private val tableStart: Int,
        private val tableEnd: Int,
        private val pipeOffsets: List<Int>,   // absolute offsets of all '|' in the first row
        private val headerLineStart: Int?     // start of header row, for inner separator
    ) : CustomHighlighterRenderer {

        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            if (pipeOffsets.size < 2) return

            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val fm        = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
            val pipeWidth = fm.charWidth('|')
            val half      = pipeWidth / 2
            val base      = pipeColor(editor)
            val thinColor = Color(base.red, base.green, base.blue, (base.alpha * 0.55).toInt().coerceAtLeast(40))
            val bg        = editor.colorsScheme.defaultBackground

            val pipeXs    = pipeOffsets.map { editor.logicalPositionToXY(editor.offsetToLogicalPosition(it)).x + half }
            val firstX    = pipeXs.first()
            val lastX     = pipeXs.last()
            val topY      = editor.logicalPositionToXY(editor.offsetToLogicalPosition(tableStart)).y
            val bottomY   = editor.logicalPositionToXY(editor.offsetToLogicalPosition(tableEnd)).y + editor.lineHeight
            val borderColor = base.lightenTowards(bg, 0.35f)

            val arc = 5f

            // 1. Mask pipe characters with background color
            g2.color = bg
            for (x in pipeXs) g2.fillRect(x - half, topY, pipeWidth, bottomY - topY)

            // 2. Inner vertical separators (thin, semi-transparent)
            g2.stroke = BasicStroke(0.8f)
            g2.color  = thinColor
            for (x in pipeXs.drop(1).dropLast(1)) g2.drawLine(x, topY, x, bottomY)

            // 3. Header separator (thin horizontal line below header row)
            if (headerLineStart != null) {
                val headerY = editor.logicalPositionToXY(
                    editor.offsetToLogicalPosition(headerLineStart)).y + editor.lineHeight - 1
                g2.drawLine(firstX, headerY, lastX, headerY)
            }

            // 4. Outer rounded rectangle
            g2.stroke = BasicStroke(1.0f)
            g2.color  = borderColor
            g2.draw(RoundRectangle2D.Float(
                firstX.toFloat(), topY.toFloat(),
                (lastX - firstX).toFloat(), (bottomY - topY).toFloat(),
                arc, arc
            ))
        }

        private fun pipeColor(editor: Editor): Color =
            editor.colorsScheme.getAttributes(GherkinHighlighter.PIPE)?.foregroundColor
                ?: editor.colorsScheme.defaultForeground

        // Blends this color towards `target` by `factor` (0 = no change, 1 = become target).
        // Using the background as target keeps the result coherent in both light and dark themes.
        private fun Color.lightenTowards(target: Color, factor: Float) = Color(
            (red   + (target.red   - red)   * factor).toInt().coerceIn(0, 255),
            (green + (target.green - green) * factor).toInt().coerceIn(0, 255),
            (blue  + (target.blue  - blue)  * factor).toInt().coerceIn(0, 255),
            alpha
        )
    }
}
