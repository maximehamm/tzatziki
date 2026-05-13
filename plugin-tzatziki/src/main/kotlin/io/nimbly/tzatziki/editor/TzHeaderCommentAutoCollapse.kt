/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.project.Project

/**
 * Re-collapses the `# @header: row|column` fold regions once the caret leaves their line.
 *
 * IntelliJ's folding model auto-expands a region when the caret enters it but never
 * re-collapses on exit — so without this listener the pretty placeholder would disappear
 * permanently after a single click on the line.
 */
class TzHeaderCommentAutoCollapse : ProjectActivity {

    override suspend fun execute(project: Project) {
        EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(e: CaretEvent) {
                val editor = e.editor
                val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                if (file.extension != "feature") return
                collapseHeaderRegionsAwayFromCaret(editor)
            }
        }, project as Disposable)
    }

    private fun collapseHeaderRegionsAwayFromCaret(editor: Editor) {
        val caretLine = editor.caretModel.logicalPosition.line
        val doc = editor.document
        val folding = editor.foldingModel
        val regions = folding.allFoldRegions
            .filter { isManagedPlaceholder(it.placeholderText) }
            .filter { it.isValid && it.isExpanded }
            .filter { caretLine !in doc.getLineNumber(it.startOffset)..doc.getLineNumber(it.endOffset) }
        if (regions.isEmpty()) return
        folding.runBatchFoldingOperation {
            regions.forEach { it.isExpanded = false }
        }
    }

    // Description folds are V1: manual fold/unfold only — auto-collapse intentionally
    // does NOT include them. Only the @header annotation folds re-collapse on caret exit.
    private fun isManagedPlaceholder(p: String?): Boolean =
        p == "Header row" || p == "Header column"
}
