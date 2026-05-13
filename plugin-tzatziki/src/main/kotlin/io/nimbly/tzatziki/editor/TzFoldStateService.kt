/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Persists the collapsed state of Cucumber+ description folds on a per-file basis.
 *
 * IntelliJ's built-in fold persistence (EditorFoldingInfo / workspace.xml) keys on the AST
 * node signature, which is unstable for fold regions we synthesise from a plain text scan
 * — so user toggles would be lost on file reopen. We side-step that by storing our own
 * "<filePath>:<startOffset>" entries and restoring them after the platform has rebuilt
 * fold regions.
 */
@Service(Service.Level.PROJECT)
@State(name = "TzFoldState", storages = [Storage("tzatziki.xml")])
class TzFoldStateService : PersistentStateComponent<TzFoldStateService.State> {

    class State {
        var entries: MutableSet<String> = mutableSetOf()
    }

    private var state = State()

    override fun getState(): State = state
    override fun loadState(loaded: State) { state = loaded }

    fun isCollapsed(filePath: String, startOffset: Int): Boolean =
        "$filePath:$startOffset" in state.entries

    fun setCollapsed(filePath: String, startOffset: Int, collapsed: Boolean) {
        val key = "$filePath:$startOffset"
        if (collapsed) state.entries.add(key)
        else state.entries.remove(key)
    }
}

/**
 * Wires editors to the [TzFoldStateService]: on open, re-collapses descriptions the user
 * had folded; on every fold-state change, persists the new state.
 */
class TzFoldStatePersistenceActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        com.intellij.openapi.editor.EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project != project) return
                val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                if (file.extension != "feature") return
                val path = file.path
                val svc = project.service<TzFoldStateService>()

                // Restore after the daemon has built the fold regions. ProjectActivity runs early;
                // FoldingBuilder runs slightly later on the EDT. invokeLater queues us after.
                ApplicationManager.getApplication().invokeLater {
                    if (editor.isDisposed) return@invokeLater
                    restoreFoldState(editor, path, svc)
                }

                val fmEx = editor.foldingModel as? com.intellij.openapi.editor.ex.FoldingModelEx
                fmEx?.addListener(object : com.intellij.openapi.editor.ex.FoldingListener {
                    override fun onFoldRegionStateChange(region: FoldRegion) {
                        if (!isDescriptionFold(region)) return
                        svc.setCollapsed(path, region.startOffset, !region.isExpanded)
                    }
                }, project)
            }
        }, project)
    }

    private fun restoreFoldState(editor: Editor, path: String, svc: TzFoldStateService) {
        val folding = editor.foldingModel
        val toCollapse = folding.allFoldRegions
            .filter { it.isValid && it.isExpanded && isDescriptionFold(it) }
            .filter { svc.isCollapsed(path, it.startOffset) }
        if (toCollapse.isEmpty()) return
        folding.runBatchFoldingOperation {
            toCollapse.forEach { it.isExpanded = false }
        }
    }

    private fun isDescriptionFold(region: FoldRegion): Boolean =
        region.placeholderText?.startsWith("📝") == true
}
