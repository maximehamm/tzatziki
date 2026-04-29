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
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinHighlighter
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl
import java.awt.Color
import java.awt.Graphics

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

        val headerLines = findHeaderLines(editor)

        for (line in 0 until document.lineCount) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd   = document.getLineEndOffset(line)
            val lineText  = text.subSequence(lineStart, lineEnd).toString().trim()

            if (!lineText.startsWith("|")) continue

            if (!prevLineIsTable(document, text, line)) {
                newHighlighters += highlighter(markupModel, lineStart, lineEnd, atTop = true)
            }

            if (line in headerLines) {
                newHighlighters += highlighter(markupModel, lineStart, lineEnd, atTop = false)
            }

            if (!nextLineIsTable(document, text, line)) {
                newHighlighters += highlighter(markupModel, lineStart, lineEnd, atTop = false)
            }
        }

        editorHighlighters[editor] = newHighlighters
    }

    private fun highlighter(
        markupModel: com.intellij.openapi.editor.markup.MarkupModel,
        lineStart: Int, lineEnd: Int, atTop: Boolean
    ): RangeHighlighter {
        val h = markupModel.addRangeHighlighter(
            null, lineStart, lineEnd,
            HighlighterLayer.SYNTAX - 1,
            HighlighterTargetArea.LINES_IN_RANGE
        )
        h.setCustomRenderer(TableBorderRenderer(lineStart, lineEnd, atTop))
        return h
    }

    private fun findHeaderLines(editor: Editor): Set<Int> {
        val project = editor.project ?: return emptySet()
        val result = mutableSetOf<Int>()
        ApplicationManager.getApplication().runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            if (psiFile is GherkinFile) {
                PsiTreeUtil.findChildrenOfType(psiFile, GherkinTableHeaderRowImpl::class.java).forEach { row ->
                    result += editor.document.getLineNumber(row.textRange.startOffset)
                }
            }
        }
        return result
    }

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

    private class TableBorderRenderer(
        private val lineStart: Int,
        private val lineEnd: Int,
        private val atTop: Boolean
    ) : CustomHighlighterRenderer {

        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            val text = editor.document.charsSequence.subSequence(lineStart, lineEnd).toString()
            val firstPipe = text.indexOf('|')
            val lastPipe  = text.lastIndexOf('|')

            val topY = editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineStart)).y
            val y = if (atTop) topY else topY + editor.lineHeight - 1

            g.color = pipeColor(editor)

            if (firstPipe < 0 || firstPipe == lastPipe) {
                g.drawLine(0, y, editor.component.width, y)
            } else {
                val fm = editor.contentComponent.getFontMetrics(
                    editor.colorsScheme.getFont(EditorFontType.PLAIN)
                )
                val half = fm.charWidth('|') / 2
                val startX = editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineStart + firstPipe)).x + half
                val endX   = editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineStart + lastPipe)).x + half
                g.drawLine(startX, y, endX, y)
            }
        }

        private fun pipeColor(editor: Editor): Color =
            editor.colorsScheme.getAttributes(GherkinHighlighter.PIPE)?.foregroundColor
                ?: editor.colorsScheme.defaultForeground
    }
}
