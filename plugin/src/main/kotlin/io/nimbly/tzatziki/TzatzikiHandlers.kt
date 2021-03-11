package io.nimbly.tzatziki

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * CHAR TYPED
 */
class TzTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        editor.findTable(editor.caretModel.offset)?.format()
        return super.charTyped(charTyped, project, editor, file)
    }
}

/**
 * BACKSPACE
 */
class TzBackspaceHandler : BackspaceHandlerDelegate() {
    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        editor.findTable(editor.caretModel.offset)?.format()
        return true
    }
    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor){
    }
}

/**
 * DELETE
 */
class TzEditorDeleteHandler : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        editor.findTable(editor.caretModel.offset)?.format()
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
        return super.isEnabledForCaret(editor, caret, dataContext)
    }
}