/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiDocumentManager
import io.nimbly.tzatziki.TzPostStartup.AbstractWriteActionHandler
import io.nimbly.tzatziki.clipboard.smartCopy
import io.nimbly.tzatziki.clipboard.smartCut
import io.nimbly.tzatziki.clipboard.smartPaste
import io.nimbly.tzatziki.mouse.TZMouseAdapter
import io.nimbly.tzatziki.mouse.TzSelectionModeManager.blockSelectionSwitch
import io.nimbly.tzatziki.mouse.TzSelectionModeManager.releaseSelectionSwitch
import io.nimbly.tzatziki.psi.format
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

var TOGGLE_CUCUMBER_PL: Boolean = true

const val EDITOR_UNINDENT_SELECTION = "EditorUnindentSelection"

/**
 * Fires on application frame creation — covers the sandbox/runIde case where the project
 * may already be open before ProjectActivity fires, and guarantees EDT for action handler setup.
 */
class TzAppStartup : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        TzPostStartup.initHandlers()
    }
}

class TzPostStartup : ProjectActivity {

    private val LOG = logger<TzPostStartup>()

    override suspend fun execute(project: Project) {
        LOG.info("C+ TzPostStartup.execute - handlerInitialized=$handlerInitialized")
        // Fallback for tests: AppLifecycleListener doesn't fire in light fixtures.
        // Note: in IntelliJ 2025.3+ ProjectActivity may not run reliably for this plugin —
        // the mouse listener and action handlers are now registered from initHandlers()
        // (called via TzAppStartup.appFrameCreated, which always fires).
        initHandlers()
        askToVote(project)
    }

    private fun initTypedHandler() {
        val actionManager = EditorActionManager.getInstance()
        actionManager.replaceHandler(DeletionHandler(ACTION_EDITOR_DELETE))
        actionManager.replaceHandler(DeletionHandler(ACTION_EDITOR_BACKSPACE))
        actionManager.replaceHandler(TabHandler(ACTION_EDITOR_TAB))
        actionManager.replaceHandler(TabHandler(EDITOR_UNINDENT_SELECTION))
        actionManager.replaceHandler(EnterHandler())
        actionManager.replaceHandler(CopyHandler())
        actionManager.replaceHandler(CutHandler())
        actionManager.replaceHandler(PasteHandler())
    }

    private fun initMouseListenerGlobal() {
        // Application-wide registration: covers all editors in all projects.
        // Disposable scope = application (lives as long as the IDE).
        EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(
            TZMouseAdapter, ApplicationManager.getApplication()
        )
    }

    private class DeletionHandler(actionId: String) : AbstractWriteActionHandler(actionId) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (dataContext.gherkin && editor.stopBeforeDeletion(getActionId()))
                return
            doDefault(editor, caret, dataContext)
            if (dataContext.gherkin)
                editor.findTableAt(editor.caretModel.offset)?.format()
        }
    }

    private class TabHandler(actionId: String) : AbstractWriteActionHandler(actionId) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (!dataContext.gherkin || !editor.navigateInTableWithTab(getActionId() == ACTION_EDITOR_TAB, editor))
                doDefault(editor, caret, dataContext)
        }
    }

    private class EnterHandler : AbstractWriteActionHandler(ACTION_EDITOR_ENTER) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (dataContext.gherkin && editor.navigateInTableWithEnter())
                return
            if (dataContext.gherkin && editor.addTableRow())
                return
            doDefault(editor, caret, dataContext)
        }
    }

    private class CopyHandler : AbstractWriteActionHandler(ACTION_EDITOR_COPY) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (dataContext.gherkin && editor.smartCopy())
                return
            doDefault(editor, caret, dataContext)
        }
    }

    private class CutHandler : AbstractWriteActionHandler(ACTION_EDITOR_CUT) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (dataContext.gherkin && editor.smartCut())
                return
            doDefault(editor, null, dataContext)
            if (dataContext.gherkin) {
                val table = editor.findTableAt(editor.caretModel.offset)
                if (table != null) {
                    table.format()
                    editor.caretModel.removeSecondaryCarets()
                }
            }
        }
    }

    private class PasteHandler : AbstractWriteActionHandler(ACTION_EDITOR_PASTE) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {

            if (CommonDataKeys.EDITOR.getData(dataContext) == null) {
                doDefault(editor, caret, dataContext)
                return
            }

            // Non-Gherkin files: delegate straight to the original handler with the real caret.
            // Passing null would trigger executeForAllCarets, which breaks injected-language
            // fragment range recalculoation (IllegalArgumentException: Invalid range).
            if (!dataContext.gherkin) {
                doDefault(editor, caret, dataContext)
                return
            }

            val offset = editor.caretModel.offset
            if (editor.smartPaste(dataContext))
                return

            blockSelectionSwitch()
            try {
                doDefault(editor, null, dataContext)
            } finally {
                releaseSelectionSwitch()
            }

            if (editor.caretModel.caretCount > 1) {
                PsiDocumentManager.getInstance(editor.project!!).commitDocument(editor.document)
                val table = editor.findTableAt(offset)
                if (table != null) {
                    editor.caretModel.removeSecondaryCarets()
                    table.format()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    abstract class AbstractWriteActionHandler(private val id: String) : EditorWriteActionHandler() {

        private val orginHandler = EditorActionManager.getInstance().getActionHandler(id)

        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext)
                = doDefault(editor, caret, dataContext)

        open fun doDefault(editor: Editor, caret: Caret?, dataContext: DataContext?)
                = orginHandler.execute(editor, caret, dataContext)

        @Deprecated("Deprecated in Java, remove ")
        override fun isEnabled(editor: Editor, dataContext: DataContext): Boolean {
            return orginHandler.isEnabled(editor, dataContext)
        }

        override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
            // Fast path via data context
            if (dataContext?.gherkin == true) return true
            // Fallback: data context may not carry PSI_FILE at evaluation time (e.g. in some
            // IntelliJ 2025.3 action contexts), so also check via the editor's virtual file.
            val vfile = FileDocumentManager.getInstance().getFile(editor.document)
            if (GherkinFileType.INSTANCE == vfile?.fileType) return true
            // Non-Gherkin: respect original handler (issue #90 — Jupyter cells, consoles).
            return runCatching { orginHandler.isEnabled(editor, caret, dataContext) }.getOrDefault(true)
        }

        fun getActionId() = id
    }

    companion object {
        private val LOG = logger<TzPostStartup>()
        private var handlerInitialized = false

        fun initHandlers() {
            if (handlerInitialized) return
            ApplicationManager.getApplication().invokeAndWait {
                if (handlerInitialized) return@invokeAndWait
                LOG.info("C+ TzPostStartup.initHandlers - registering keyboard handlers")
                val instance = TzPostStartup()
                instance.initTypedHandler()
                instance.initMouseListenerGlobal()
                handlerInitialized = true
            }
        }
    }
}

val DataContext.gherkin: Boolean
    get() =
        TOGGLE_CUCUMBER_PL && GherkinFileType.INSTANCE == CommonDataKeys.PSI_FILE.getData(this)?.fileType


private fun EditorActionManager.replaceHandler(handler: AbstractWriteActionHandler) {
    setActionHandler(handler.getActionId(), handler)
}
