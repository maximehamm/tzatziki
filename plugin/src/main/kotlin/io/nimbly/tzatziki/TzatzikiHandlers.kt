package io.nimbly.tzatziki

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.nimbly.tzatziki.util.findTable
import io.nimbly.tzatziki.util.format

/**
 * CHAR TYPED
 */
class TzTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        editor.findTable(editor.caretModel.offset)?.format()
        return Result.CONTINUE
    }
}