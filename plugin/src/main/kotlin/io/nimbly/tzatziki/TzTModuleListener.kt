package io.nimbly.tzatziki

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import io.nimbly.tzatziki.TzTModuleListener.AbstractWriteActionHandler

class TzTModuleListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        if (!handlerInitialized) {
            initTypedHandler()
            handlerInitialized = true
        }
    }

    private fun initTypedHandler() {

        val actionManager = EditorActionManager.getInstance()

        actionManager.replaceHandler(DeleteHandler())
        actionManager.replaceHandler(BackSpaceHandler())
        actionManager.replaceHandler(CutHandler())
        actionManager.replaceHandler(PasteHandler())
    }

    private class DeleteHandler : AbstractWriteActionHandler(IdeActions.ACTION_EDITOR_DELETE) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            doDefault(editor, caret, dataContext)
            editor.findTable(editor.caretModel.offset)?.format()
        }
    }

    private class BackSpaceHandler : AbstractWriteActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            doDefault(editor, caret, dataContext)
            editor.findTable(editor.caretModel.offset)?.format()
        }
    }

    private class CutHandler : AbstractWriteActionHandler(IdeActions.ACTION_EDITOR_CUT) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            doDefault(editor, caret, dataContext)
            editor.findTable(editor.caretModel.offset)?.format()
        }
    }

    private class PasteHandler : AbstractWriteActionHandler(IdeActions.ACTION_EDITOR_PASTE) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            doDefault(editor, caret, dataContext)
            editor.findTable(editor.caretModel.offset)?.format()
        }
    }

    abstract class AbstractWriteActionHandler(val id: String) : EditorWriteActionHandler() {
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
