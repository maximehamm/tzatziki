package io.nimbly.tzatziki.view.features.actions

import io.nimbly.tzatziki.view.features.FeaturePanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class LocateAction(val panel: FeaturePanel) : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    init {
        this.templatePresentation.text = "Select opened file"
        this.templatePresentation.icon = AllIcons.General.Locate
    }

    override fun actionPerformed(e: AnActionEvent) {
        panel.selectFromEditor()
    }
}