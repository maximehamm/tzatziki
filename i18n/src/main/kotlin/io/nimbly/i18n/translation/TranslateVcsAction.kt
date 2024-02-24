package io.nimbly.i18n.translation

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.vcs.VcsDataKeys

class TranslateVcsAction : TranslateAction() {

    override fun update(event: AnActionEvent) {
        super.doUpdate(event, editor(event))
    }

    override fun actionPerformed(event: AnActionEvent) {
        super.doActionPerformed(event, editor(event))
    }

    private fun editor(event: AnActionEvent): Editor? {

        val document = VcsDataKeys.COMMIT_MESSAGE_DOCUMENT.getData(event.dataContext)
            ?: return null

        val project = CommonDataKeys.PROJECT.getData(event.dataContext)
            ?: return null

        return EditorFactory.getInstance().getEditors(document, project).getOrNull(0)
    }
}