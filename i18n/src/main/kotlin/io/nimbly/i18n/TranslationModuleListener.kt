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

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.nimbly.i18n.util.clearInlays

class TranslationModuleListener : StartupActivity {

    override fun runActivity(project: Project) {
        if (!handlerInitialized) {
            initTypedHandler()
            initMouseListener(project)
            handlerInitialized = true
        }
    }

    private fun initTypedHandler() {

        val actionManager = EditorActionManager.getInstance()

        actionManager.replaceHandler(EscapeHandler())
    }

    private fun initMouseListener(project: Project) {
        EditorFactory.getInstance().eventMulticaster.apply {
            addEditorMouseListener(TranslationMouseAdapter, project)
        }
    }

    companion object {
        private var handlerInitialized = false
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
