/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.markdown

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Re-collapses the markdown-syntax fold regions ([TzMarkdownSyntaxFoldingBuilder])
 * as soon as the caret leaves their line.
 *
 * IntelliJ auto-EXPANDS folds when the caret enters them (so the user can edit the
 * raw `**bold**` / `[label](url)` source) but never re-collapses on exit. Without
 * this listener the syntax characters would stay revealed permanently after a
 * single click on the line.
 */
class TzMarkdownSyntaxAutoCollapse : ProjectActivity {

    override suspend fun execute(project: Project) {
        EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(e: CaretEvent) {
                val editor = e.editor
                val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                if (file.extension != "feature") return
                collapseMarkdownSyntaxAwayFromCaret(editor)
            }
        }, project as Disposable)
    }

    private fun collapseMarkdownSyntaxAwayFromCaret(editor: Editor) {
        val caretLine = editor.caretModel.logicalPosition.line
        val doc = editor.document
        val folding = editor.foldingModel
        val toExpand = mutableListOf<com.intellij.openapi.editor.FoldRegion>()
        val toCollapse = mutableListOf<com.intellij.openapi.editor.FoldRegion>()
        for (region in folding.allFoldRegions) {
            if (!region.isValid) continue
            if (!TzMarkdownSyntaxFoldingBuilder.isMarkdownSyntaxPlaceholder(region.placeholderText)) continue
            val onCaretLine = doc.getLineNumber(region.startOffset) == caretLine
            if (onCaretLine && !region.isExpanded) toExpand += region
            else if (!onCaretLine && region.isExpanded) toCollapse += region
        }
        if (toExpand.isEmpty() && toCollapse.isEmpty()) return
        folding.runBatchFoldingOperation {
            toExpand.forEach { it.isExpanded = true }
            toCollapse.forEach { it.isExpanded = false }
        }
    }
}
