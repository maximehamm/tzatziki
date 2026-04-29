package io.nimbly.tzatziki.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
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

/**
 * Draws horizontal border lines around Gherkin tables and below header rows.
 *
 * Header detection:
 *   - Examples: blocks  → always a header (PSI)
 *   - DataTables        → only if preceded by a comment: # @header: row
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
            override fun documentChanged(event: DocumentEvent) {
                scheduleRefresh(editor)
            }
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

        for (line in 0 until document.lineCount) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd   = document.getLineEndOffset(line)
            val lineText  = text.subSequence(lineStart, lineEnd).toString().trim()

            if (!lineText.startsWith("|")) continue

            newHighlighters += verticalLinesHighlighter(markupModel, lineStart, lineEnd)

            if (!prevLineIsTable(document, text, line)) {
                newHighlighters += highlighter(markupModel, lineStart, lineEnd, atTop = true)
            }

            if (line in headerRowLines) {
                newHighlighters += highlighter(markupModel, lineStart, lineEnd, atTop = false, isHeader = true)
                headerCellHighlight(markupModel, lineStart, lineEnd, editor)?.let { newHighlighters += it }
            }

            if (line in headerColumnLines) {
                headerFirstColumnHighlight(markupModel, lineStart, lineEnd, editor, text)?.let { newHighlighters += it }
            }

            if (!nextLineIsTable(document, text, line)) {
                newHighlighters += highlighter(markupModel, lineStart, lineEnd, atTop = false)
            }
        }

        editorHighlighters[editor] = newHighlighters
    }

    private fun highlighter(
        markupModel: com.intellij.openapi.editor.markup.MarkupModel,
        lineStart: Int, lineEnd: Int, atTop: Boolean, isHeader: Boolean = false
    ): RangeHighlighter {
        val h = markupModel.addRangeHighlighter(
            null, lineStart, lineEnd,
            HighlighterLayer.SYNTAX - 1,
            HighlighterTargetArea.LINES_IN_RANGE
        )
        h.setCustomRenderer(TableBorderRenderer(lineStart, lineEnd, atTop, isHeader))
        return h
    }

    private fun headerCellHighlight(
        markupModel: com.intellij.openapi.editor.markup.MarkupModel,
        lineStart: Int, lineEnd: Int,
        editor: Editor
    ): RangeHighlighter? =
        applyHeaderAttrs(markupModel, lineStart, lineEnd, editor)

    private fun headerFirstColumnHighlight(
        markupModel: com.intellij.openapi.editor.markup.MarkupModel,
        lineStart: Int, lineEnd: Int,
        editor: Editor,
        text: CharSequence
    ): RangeHighlighter? {
        val lineText  = text.subSequence(lineStart, lineEnd).toString()
        val firstPipe = lineText.indexOf('|')
        val secondPipe = lineText.indexOf('|', firstPipe + 1)
        if (firstPipe < 0 || secondPipe < 0) return null
        return applyHeaderAttrs(markupModel, lineStart + firstPipe, lineStart + secondPipe + 1, editor)
    }

    private fun applyHeaderAttrs(
        markupModel: com.intellij.openapi.editor.markup.MarkupModel,
        start: Int, end: Int,
        editor: Editor
    ): RangeHighlighter? {
        val srcAttrs = editor.colorsScheme.getAttributes(GherkinHighlighter.TABLE_HEADER_CELL)
            ?: return null
        val attrs = TextAttributes().apply {
            backgroundColor = srcAttrs.backgroundColor
            foregroundColor = srcAttrs.foregroundColor
            fontType        = srcAttrs.fontType
        }
        return markupModel.addRangeHighlighter(
            start, end,
            HighlighterLayer.SYNTAX + 1,
            attrs,
            HighlighterTargetArea.EXACT_RANGE
        )
    }

    private fun findHeaderRowLines(editor: Editor): Set<Int> {
        val project = editor.project ?: return emptySet()
        val document = editor.document
        val text = document.charsSequence
        val result = mutableSetOf<Int>()

        ApplicationManager.getApplication().runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            if (psiFile !is GherkinFile) return@runReadAction

            // Examples: blocks are always headers
            PsiTreeUtil.findChildrenOfType(psiFile, GherkinExamplesBlock::class.java).forEach { examples ->
                val headerRow = examples.table?.headerRow ?: return@forEach
                result += document.getLineNumber(headerRow.textRange.startOffset)
            }

            // DataTables: only if preceded by "# @header: row"
            PsiTreeUtil.findChildrenOfType(psiFile, GherkinTable::class.java).forEach { table ->
                if (PsiTreeUtil.getParentOfType(table, GherkinExamplesBlock::class.java) != null) return@forEach
                val firstRow = table.headerRow ?: return@forEach
                val firstRowLine = document.getLineNumber(firstRow.textRange.startOffset)
                if (tableAnnotation(document, text, firstRowLine) == "row") {
                    result += firstRowLine
                }
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
                    table.dataRows.forEach { row ->
                        result += document.getLineNumber(row.textRange.startOffset)
                    }
                }
            }
        }
        return result
    }

    // Scans upward from the table's first row, skipping blank lines.
    // Returns "row", "column", or null if no matching annotation is found.
    private fun tableAnnotation(doc: Document, text: CharSequence, tableLine: Int): String? {
        var line = tableLine - 1
        while (line >= 0) {
            val s = doc.getLineStartOffset(line)
            val e = doc.getLineEndOffset(line)
            val lineText = text.subSequence(s, e).toString().trim()
            when {
                lineText.isEmpty() -> line--
                lineText.matches(Regex("#\\s*@header:\\s*row\\s*"))    -> return "row"
                lineText.matches(Regex("#\\s*@header:\\s*column\\s*")) -> return "column"
                else -> return null
            }
        }
        return null
    }

    private fun verticalLinesHighlighter(
        markupModel: com.intellij.openapi.editor.markup.MarkupModel,
        lineStart: Int, lineEnd: Int
    ): RangeHighlighter {
        val h = markupModel.addRangeHighlighter(
            null, lineStart, lineEnd,
            HighlighterLayer.SYNTAX - 1,
            HighlighterTargetArea.LINES_IN_RANGE
        )
        h.setCustomRenderer(TableVerticalLineRenderer(lineStart, lineEnd))
        return h
    }

    // ---- Helpers ----

    private fun prevLineIsTable(doc: Document, text: CharSequence, line: Int): Boolean {
        if (line == 0) return false
        val s = doc.getLineStartOffset(line - 1)
        val e = doc.getLineEndOffset(line - 1)
        return text.subSequence(s, e).toString().trim().startsWith("|")
    }

    private fun nextLineIsTable(doc: Document, text: CharSequence, line: Int): Boolean {
        if (line >= doc.lineCount - 1) return false
        val s = doc.getLineStartOffset(line + 1)
        val e = doc.getLineEndOffset(line + 1)
        return text.subSequence(s, e).toString().trim().startsWith("|")
    }

    private class TableVerticalLineRenderer(
        private val lineStart: Int,
        private val lineEnd: Int
    ) : CustomHighlighterRenderer {

        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            val text    = editor.document.charsSequence.subSequence(lineStart, lineEnd).toString()
            val topY    = editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineStart)).y
            val bottomY = topY + editor.lineHeight

            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val fm        = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
            val pipeWidth = fm.charWidth('|')
            val half      = pipeWidth / 2
            val base      = pipeColor(editor)
            val thinColor = Color(base.red, base.green, base.blue, (base.alpha * 0.55).toInt().coerceAtLeast(40))
            val bg        = editor.colorsScheme.defaultBackground

            // Count total pipes to identify first and last
            val pipePositions = mutableListOf<Int>()
            var idx = 0
            while (idx < text.length) {
                val pipeIdx = text.indexOf('|', idx)
                if (pipeIdx < 0) break
                pipePositions += pipeIdx
                idx = pipeIdx + 1
            }

            pipePositions.forEachIndexed { i, pipeIdx ->
                val x = editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineStart + pipeIdx)).x + half

                // Mask the pipe character with the background
                g2.color = bg
                g2.fillRect(x - half, topY, pipeWidth, bottomY - topY)

                // Outer borders (first and last pipe) → full opacity
                // Inner separators → thin + semi-transparent
                val isOuter = i == 0 || i == pipePositions.lastIndex
                g2.stroke = if (isOuter) BasicStroke(1.0f) else BasicStroke(0.8f)
                g2.color  = if (isOuter) base else thinColor
                g2.drawLine(x, topY, x, bottomY)
            }
        }

        private fun pipeColor(editor: Editor): Color =
            editor.colorsScheme.getAttributes(GherkinHighlighter.PIPE)?.foregroundColor
                ?: editor.colorsScheme.defaultForeground
    }

    private class TableBorderRenderer(
        private val lineStart: Int,
        private val lineEnd: Int,
        private val atTop: Boolean,
        private val isHeader: Boolean = false
    ) : CustomHighlighterRenderer {

        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            val text = editor.document.charsSequence.subSequence(lineStart, lineEnd).toString()
            val firstPipe = text.indexOf('|')
            val lastPipe  = text.lastIndexOf('|')

            val topY = editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineStart)).y
            val y = if (atTop) topY else topY + editor.lineHeight - 1

            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val baseColor = pipeColor(editor)
            g2.color = if (isHeader) Color(baseColor.red, baseColor.green, baseColor.blue, (baseColor.alpha * 0.55).toInt().coerceAtLeast(40)) else baseColor
            g2.stroke = if (isHeader) BasicStroke(0.8f) else BasicStroke(1.0f)

            if (firstPipe < 0 || firstPipe == lastPipe) {
                g2.drawLine(0, y, editor.component.width, y)
            } else {
                val fm = editor.contentComponent.getFontMetrics(
                    editor.colorsScheme.getFont(EditorFontType.PLAIN)
                )
                val half = fm.charWidth('|') / 2
                val startX = editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineStart + firstPipe)).x + half
                val endX   = editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineStart + lastPipe)).x + half
                g2.drawLine(startX, y, endX, y)
            }
        }

        private fun pipeColor(editor: Editor): Color =
            editor.colorsScheme.getAttributes(GherkinHighlighter.PIPE)?.foregroundColor
                ?: editor.colorsScheme.defaultForeground
    }
}
