package io.nimbly.tzatziki

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result.CONTINUE
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result.STOP
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.nimbly.tzatziki.psi.format
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

class TzTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.gherkin && editor.document.getTextLine(editor.caretModel.offset).contains("|"))
            editor.findTableAt(editor.caretModel.offset)?.format()
        return CONTINUE
    }

    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (!file.gherkin)
            return CONTINUE

        if (editor.addNewColum(c, project, fileType))
            return STOP

        return CONTINUE
    }

    override fun beforeSelectionRemoved(c: Char, project: Project, editor: Editor, file: PsiFile): Result {

        if (file.gherkin && editor.stopBeforeDeletion(false, false))
            return STOP

        return CONTINUE
    }

    private val PsiFile.gherkin: Boolean
        get() = SMART_EDIT && fileType == GherkinFileType.INSTANCE
}