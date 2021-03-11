package io.nimbly.tzatziki

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.psi.PsiReference
import java.util.*

class TzTModuleListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        if (!handlerInitialized) {
            initTypedHandler()
            handlerInitialized = true
        }
    }

    private fun initTypedHandler() {

        val actionManager = EditorActionManager.getInstance()

        // DELETE
        val h = DeleteHandler()
        actionManager.setActionHandler(h.getActionId(), h)
    }

    private class DeleteHandler : AbstractWriteActionHandler(IdeActions.ACTION_EDITOR_DELETE) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            doDefault(editor, caret, dataContext)
            editor.findTable(editor.caretModel.offset)?.format()
        }
    }


    private abstract class AbstractWriteActionHandler(val id: String) : EditorWriteActionHandler() {
        val orginHandler: EditorActionHandler

        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            doDefault(editor, caret, dataContext)
        }

        open fun doDefault(editor: Editor, caret: Caret?, dataContext: DataContext?) {
            orginHandler.execute(editor, caret, dataContext)
        }

        fun getActionId(): String {
            return id
        }

        init {
            val actionManager = EditorActionManager.getInstance()
            orginHandler = actionManager.getActionHandler(id)
        }
    }

    companion object {
        private var handlerInitialized = false
    }
}