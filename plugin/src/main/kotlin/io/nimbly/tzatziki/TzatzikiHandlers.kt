package io.nimbly.tzatziki

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.nimbly.tzatziki.util.addNewColum
import io.nimbly.tzatziki.util.findTableAt
import io.nimbly.tzatziki.util.format

class TzTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (SMART_EDIT)
            editor.findTableAt(editor.caretModel.offset)?.format()
        return Result.CONTINUE
    }

    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        return if (SMART_EDIT && addNewColum(c, editor, file, project, fileType))
            Result.STOP
        else
            Result.CONTINUE
    }
}