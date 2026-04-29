/*
 * TRANSLATION +
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

package io.nimbly.i18n

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import io.nimbly.i18n.util.clearInlays

/**
 * Fires on application frame creation. Registers the editor action handlers (Escape).
 * In IntelliJ 2025.3+ ProjectActivity / StartupActivity may not run reliably for this
 * plugin — AppLifecycleListener always fires, so handler setup is moved here.
 */
class TranslationAppStartup : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        TranslationHandlerSetup.initHandlers()
    }
}

/**
 * Registers the editor mouse listener per project (Disposable = project), so the
 * listener is auto-removed when the project closes — and, importantly, between tests.
 *
 * Lives on com.intellij.openapi.project.ProjectManagerListener (registered via
 * <applicationListeners>). Replaces the former init-from-StartupActivity path that was
 * not reliable in IntelliJ 2025.3+.
 */
class TranslationProjectMouseListenerInstaller : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        EditorFactory.getInstance().eventMulticaster
            .addEditorMouseListener(TranslationMouseAdapter, project)
    }
}

/** One-time setup of the Escape editor action handler. */
object TranslationHandlerSetup {
    private var handlerInitialized = false

    fun initHandlers() {
        if (handlerInitialized) return
        ApplicationManager.getApplication().invokeAndWait {
            if (handlerInitialized) return@invokeAndWait
            EditorActionManager.getInstance().replaceHandler(EscapeHandler())
            handlerInitialized = true
        }
    }
}

private class EscapeHandler : AbstractWriteActionHandler(IdeActions.ACTION_EDITOR_ESCAPE) {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        EditorFactory.getInstance().clearInlays(editor.project)
        doDefault(editor, caret, dataContext)
    }
}

@Suppress("DEPRECATION")
abstract class AbstractWriteActionHandler(private val id: String) : EditorWriteActionHandler() {
    private val orginHandler = EditorActionManager.getInstance().getActionHandler(id)
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext)
            = doDefault(editor, caret, dataContext)
    open fun doDefault(editor: Editor, caret: Caret?, dataContext: DataContext?)
            = orginHandler.execute(editor, caret, dataContext)

    @Deprecated("Deprecated in Java")
    override fun isEnabled(editor: Editor, dataContext: DataContext)
            = orginHandler.isEnabled(editor, dataContext)
    fun getActionId()
            = id
}

private fun EditorActionManager.replaceHandler(handler: AbstractWriteActionHandler) {
    setActionHandler(handler.getActionId(), handler)
}

object TranslationMouseAdapter : EditorMouseListener {
    override fun mouseClicked(event: EditorMouseEvent) {
        EditorFactory.getInstance().clearInlays(event.editor.project, 5)
    }
}
