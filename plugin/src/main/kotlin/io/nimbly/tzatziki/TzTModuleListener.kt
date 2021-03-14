package io.nimbly.tzatziki

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions.*
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.sun.java.accessibility.util.SwingEventMonitor.addCaretListener
import io.nimbly.tzatziki.TzTModuleListener.AbstractWriteActionHandler
import io.nimbly.tzatziki.util.*
import io.nimbly.tzatziki.util.TzSelectionModeManager.blockSelectionSwitch
import io.nimbly.tzatziki.util.TzSelectionModeManager.releaseSelectionSwitch

var TZATZIKI_AUTO_FORMAT : Boolean = true
var TZATZIKI_SMART_COPY : Boolean = true

const val EDITOR_UNINDENT_SELECTION = "EditorUnindentSelection"

class TzTModuleListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        if (!handlerInitialized) {
            initTypedHandler()
            initMouseListener(project)
            handlerInitialized = true
        }
    }

    private fun initTypedHandler() {

        val actionManager = EditorActionManager.getInstance()

        actionManager.replaceHandler(FormatterHandler(ACTION_EDITOR_DELETE))
        actionManager.replaceHandler(FormatterHandler(ACTION_EDITOR_BACKSPACE))

//        actionManager.replaceHandler(FormatterHandler(ACTION_EDITOR_CUT))
//        actionManager.replaceHandler(FormatterHandler(ACTION_ EDITOR_PASTE))

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
            addCaretListener(TZCaretAdapter, project)
        }
    }

    private class FormatterHandler(actionId : String) : AbstractWriteActionHandler(actionId) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            doDefault(editor, caret, dataContext)
            if (TZATZIKI_AUTO_FORMAT)
                editor.findTableAt(editor.caretModel.offset)?.format()
        }
    }

    private class TabHandler(actionId : String) : AbstractWriteActionHandler(actionId) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (!TZATZIKI_AUTO_FORMAT || !editor.navigateInTableWithTab(getActionId() == ACTION_EDITOR_TAB, editor))
                doDefault(editor, caret, dataContext)
        }
    }

    private class EnterHandler : AbstractWriteActionHandler(ACTION_EDITOR_ENTER) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (!TZATZIKI_AUTO_FORMAT || !editor.navigateInTableWithEnter())
                if (!TZATZIKI_AUTO_FORMAT || !editor.addTableRow())
                    doDefault(editor, caret, dataContext)
        }
    }

    private class CopyHandler : AbstractWriteActionHandler(ACTION_EDITOR_COPY) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (!TZATZIKI_SMART_COPY || !editor.smartCopy())
                doDefault(editor, caret, dataContext)
        }
    }

    private class CutHandler : AbstractWriteActionHandler(ACTION_EDITOR_CUT) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            doDefault(editor, caret, dataContext)
            if (TZATZIKI_AUTO_FORMAT) {
                val table = editor.findTableAt(editor.caretModel.offset)
                if (table != null) {
                    table.format()
                    editor.selectionModel.removeSelection()
                }
            }
        }
    }

    private class PasteHandler : AbstractWriteActionHandler(ACTION_EDITOR_PASTE) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (TZATZIKI_SMART_COPY && editor.smartPaste(dataContext))
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

private fun EditorActionManager.replaceHandler(handler: AbstractWriteActionHandler) {
    setActionHandler(handler.getActionId(), handler)

}
