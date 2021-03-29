/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions.*
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import io.nimbly.tzatziki.TzTModuleListener.AbstractWriteActionHandler
import io.nimbly.tzatziki.clipboard.smartCopy
import io.nimbly.tzatziki.clipboard.smartCut
import io.nimbly.tzatziki.clipboard.smartPaste
import io.nimbly.tzatziki.mouse.TZMouseAdapter
import io.nimbly.tzatziki.mouse.TzSelectionModeManager.blockSelectionSwitch
import io.nimbly.tzatziki.mouse.TzSelectionModeManager.releaseSelectionSwitch
import io.nimbly.tzatziki.psi.format
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

var SMART_EDIT : Boolean = true

const val EDITOR_UNINDENT_SELECTION = "EditorUnindentSelection"

class TzTModuleListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        if (!handlerInitialized) {
            initTypedHandler()
            initMouseListener(project)
            handlerInitialized = true

            askToVote(project)
        }
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

    private fun initMouseListener(project: Project) {
        EditorFactory.getInstance().eventMulticaster.apply {
            addEditorMouseListener(TZMouseAdapter, project)
        }
    }

    private class DeletionHandler(actionId : String) : AbstractWriteActionHandler(actionId) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (dataContext.gherkin && editor.stopBeforeDeletion(getActionId()))
                return
            doDefault(editor, caret, dataContext)
            if (dataContext.gherkin)
                editor.findTableAt(editor.caretModel.offset)?.format()
        }
    }

    private class TabHandler(actionId : String) : AbstractWriteActionHandler(actionId) {
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

            if (dataContext.gherkin && editor.smartPaste(dataContext))
                return

            blockSelectionSwitch()
            try {
                super.doExecute(editor, null, dataContext)
            } finally {
                releaseSelectionSwitch()
            }
        }
    }

    abstract class AbstractWriteActionHandler(private val id: String) : EditorWriteActionHandler() {
        private val orginHandler = EditorActionManager.getInstance().getActionHandler(id)
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext)
            = doDefault(editor, caret, dataContext)
        open fun doDefault(editor: Editor, caret: Caret?, dataContext: DataContext?)
            = orginHandler.execute(editor, caret, dataContext)
        fun getActionId()
            = id
    }

    companion object {
        private var handlerInitialized = false
    }

}

private val DataContext.gherkin: Boolean
    get() =
        SMART_EDIT && GherkinFileType.INSTANCE == CommonDataKeys.PSI_FILE.getData(this)?.fileType


private fun EditorActionManager.replaceHandler(handler: AbstractWriteActionHandler) {
    setActionHandler(handler.getActionId(), handler)

}
